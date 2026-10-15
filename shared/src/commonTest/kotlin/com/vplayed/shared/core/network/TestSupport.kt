package com.vplayed.shared.core.network

import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.config.ServerEnvironment
import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.platform.DeviceInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun testConfig(
    loggingEnabled: Boolean = false,
    environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
    redactSensitive: Boolean = environment.isProduction,
) = SharedConfig(
    environment = environment,
    serverUrl = "https://api.test.invalid/",
    imageBaseUrl = "https://images.test.invalid/",
    loggingEnabled = loggingEnabled,
    appVersion = "3.0.0",
    platform = ClientPlatform.ANDROID,
    transportKey = "unit-test-passphrase",
    redactSensitive = redactSensitive,
)

internal class FakeDeviceInfo(
    override val deviceId: String? = "device-123",
    override val deviceName: String = "Pixel 9",
    var language: String = "ta",
) : DeviceInfo {
    override fun languageCode(): String = language
}

internal fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** A MockEngine that records every request and answers with [handler]. */
internal class RecordingEngine(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("""{"statusCode":200}""") },
) {
    val requests = mutableListOf<HttpRequestData>()

    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }

    val last: HttpRequestData get() = requests.last()
}
