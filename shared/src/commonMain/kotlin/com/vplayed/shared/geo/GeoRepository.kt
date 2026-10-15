package com.vplayed.shared.geo

import com.vplayed.shared.core.error.ApiException
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.resultOf
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.RequestOptions
import com.vplayed.shared.core.network.VPlayedResponse
import com.vplayed.shared.core.network.requestOptions
import com.vplayed.shared.core.serialization.lenientString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Result of one geo refresh. The store keeps its previous values for every outcome except [SUCCESS]. */
enum class GeoRefreshOutcome {
    SUCCESS,

    /** Network error, timeout or server error: worth retrying later. */
    TRANSIENT_FAILURE,

    /** Bad IP, missing token or unexpected response: retrying now would not help. */
    INVALID_RESPONSE,
}

/**
 * Refreshes [GeoStore]: public IP → geo access key for that IP → location from the token
 * (decision D18: the repository is shared, each app decides when to call it — AndroidJP on start
 * and on every network change, VPlayed once at launch).
 */
interface GeoRepository {
    val store: GeoStore

    /** Never throws (except cancellation) and never logs the IP or token. Calls are serialized. */
    suspend fun refresh(): GeoRefreshOutcome
}

internal class DefaultGeoRepository(
    private val api: ApiCaller,
    /** Client without VPlayed headers: the public IP service is a third party. */
    private val thirdPartyClient: HttpClient,
    private val json: Json,
    override val store: GeoStore,
) : GeoRepository {
    private val mutex = Mutex()

    override suspend fun refresh(): GeoRefreshOutcome = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked(): GeoRefreshOutcome {
        val ip = when (val result = publicIp()) {
            is DataResult.Failure -> return result.error.toOutcome()
            is DataResult.Success -> result.data?.takeIf(::isValidIp) ?: return GeoRefreshOutcome.INVALID_RESPONSE
        }
        store.updateIp(ip)

        val response = when (val result = api.get(GEO_DATA_PATH + ip, options = GEO_DATA_OPTIONS)) {
            is DataResult.Failure -> return result.error.toOutcome()
            is DataResult.Success -> result.data
        }
        failureOf(response)?.let { return it }
        val token = (response.envelope?.response as? JsonObject)?.get("geo_token").lenientString()
            ?: return GeoRefreshOutcome.INVALID_RESPONSE
        store.updateGeo(token, GeoTokenDecoder.decode(token))
        return GeoRefreshOutcome.SUCCESS
    }

    private suspend fun publicIp(): DataResult<String?> = resultOf {
        val response = thirdPartyClient.get(PUBLIC_IP_URL) {
            requestOptions(RequestOptions(redact = true))
        }
        if (response.status.value !in 200..299) throw ApiException(response.status.value, null)
        (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.get("ip").lenientString()
    }

    /** Decision D1: a non-2xx HTTP status fails; within 2xx a `statusCode` other than 200 fails. */
    private fun failureOf(response: VPlayedResponse): GeoRefreshOutcome? {
        val status = if (response.isHttpSuccess) response.effectiveStatusCode else response.httpStatus
        return when {
            status in 200..299 -> null
            status >= SERVER_ERROR || status in RETRYABLE_CLIENT_CODES -> GeoRefreshOutcome.TRANSIENT_FAILURE
            else -> GeoRefreshOutcome.INVALID_RESPONSE
        }
    }

    private fun AppError.toOutcome(): GeoRefreshOutcome = when (this) {
        AppError.Network, AppError.Timeout -> GeoRefreshOutcome.TRANSIENT_FAILURE
        is AppError.Server -> if (code >= SERVER_ERROR || code in RETRYABLE_CLIENT_CODES) {
            GeoRefreshOutcome.TRANSIENT_FAILURE
        } else {
            GeoRefreshOutcome.INVALID_RESPONSE
        }
        else -> GeoRefreshOutcome.INVALID_RESPONSE
    }

    private companion object {
        /** Both apps look the address up here (AndroidJP `PublicIpApi`, VPlayed `StartupService`). */
        const val PUBLIC_IP_URL = "https://api.ipify.org/?format=json"
        const val GEO_DATA_PATH = "mediaview/api/v1/common/geo-data/"

        /** Must not carry the token it fetches, and its body (the token) is never logged. */
        val GEO_DATA_OPTIONS = RequestOptions(sendGeoToken = false, redact = true)

        const val SERVER_ERROR = 500
        val RETRYABLE_CLIENT_CODES = setOf(408, 429)

        // IPv4 or IPv6 text (the IP is used as a URL path segment, so only these characters pass).
        val IP_PATTERN = Regex("^[0-9A-Fa-f:.]{2,45}$")

        fun isValidIp(ip: String): Boolean = IP_PATTERN.matches(ip) && (ip.contains('.') || ip.contains(':'))
    }
}
