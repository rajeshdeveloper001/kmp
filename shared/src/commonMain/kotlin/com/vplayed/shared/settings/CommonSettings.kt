package com.vplayed.shared.settings

import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.flatMap
import com.vplayed.shared.core.error.map
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.requireResponse
import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientInt
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.json.JsonObject

/** Which catalogue the backend serves (`AppContentType` in the UIKit app). */
enum class ContentLibrary(val code: Int) {
    VOD_DRAMA(0),
    VOD(1),
    DRAMA(2),
    ;

    companion object {
        fun fromCode(code: Int?): ContentLibrary = entries.firstOrNull { it.code == code } ?: VOD_DRAMA
    }
}

/** How sign-up codes are verified (`LoginType` in the UIKit app). */
enum class VerificationMethod(val value: String) {
    EMAIL("email"),
    PHONE("phone"),
    MOBILE("mobile"),
    ;

    companion object {
        fun fromValue(value: String?): VerificationMethod = entries.firstOrNull { it.value == value } ?: EMAIL
    }
}

/** Force / soft update settings from `force_update_details`, for this app's platform only. Stored; nothing prompts yet. */
data class UpdateDetails(
    val isForceUpdateEnabled: Boolean,
    val minimumVersion: String?,
    val forceUpdateMessage: String?,
    val isSoftUpdateEnabled: Boolean,
    val latestVersion: String?,
    val softUpdateMessage: String?,
)

/**
 * App-wide settings from `GET mediaview/api/v1/common` (VPlayed `CommonSettings`, ported from the
 * UIKit app's `CommonKeysStruct`). Missing or malformed keys fall back to the UIKit defaults
 * instead of failing the response.
 */
data class CommonSettings(
    /** Null means the key was absent; the UIKit app only acts on an explicit `false`. */
    val isRtlAllowed: Boolean? = null,
    val isPipAllowed: Boolean? = null,
    val isCastingAllowed: Boolean? = null,
    val isScreenRecordingAllowed: Boolean? = null,
    val isGuestUser: Boolean? = null,
    val contentLibrary: ContentLibrary = ContentLibrary.VOD_DRAMA,
    val verificationMethod: VerificationMethod = VerificationMethod.EMAIL,
    val isIapEnabled: Boolean = false,
    val isAppStoreCtaChange: Boolean = false,
    /** Only set when the block is for this app's platform. */
    val update: UpdateDetails? = null,
) {
    companion object {
        /** Reads the `response` object; [platform] selects the `force_update_details` block meant for this app. */
        fun from(response: JsonObject, platform: ClientPlatform): CommonSettings {
            val update = response["force_update_details"] as? JsonObject
            val isForThisPlatform = update?.get("platform").lenientString()?.lowercase() == platform.headerValue
            return CommonSettings(
                isRtlAllowed = response["rtl"].lenientBool(),
                isPipAllowed = response["pic_in_pic"].lenientBool(),
                isCastingAllowed = response["casting"].lenientBool(),
                isScreenRecordingAllowed = response["screen_rec"].lenientBool(),
                isGuestUser = response["is_guest_user"].lenientBool(),
                contentLibrary = ContentLibrary.fromCode(response["content_library"].lenientInt()),
                // The backend's spelling.
                verificationMethod = VerificationMethod.fromValue(response["otp_verfied"].lenientString()),
                isIapEnabled = response["is_iap_enabled"].lenientBool() ?: false,
                isAppStoreCtaChange = response["is_iosappstore_cta_change"].lenientBool() ?: false,
                update = update?.takeIf { isForThisPlatform }?.let { block ->
                    UpdateDetails(
                        isForceUpdateEnabled = block["force_update"].lenientBool() ?: false,
                        minimumVersion = block["minimum_version"].lenientString(),
                        forceUpdateMessage = block["force_update_message"].lenientString(),
                        isSoftUpdateEnabled = block["soft_update"].lenientBool() ?: false,
                        latestVersion = block["latest_version"].lenientString(),
                        softUpdateMessage = block["soft_update_message"].lenientString(),
                    )
                },
            )
        }
    }
}

/** The launch-time settings call. */
interface CommonSettingsRepository {
    suspend fun load(): DataResult<CommonSettings>
}

internal class DefaultCommonSettingsRepository(
    private val api: ApiCaller,
    private val platform: ClientPlatform,
) : CommonSettingsRepository {

    override suspend fun load(): DataResult<CommonSettings> =
        api.get(PATH).flatMap { it.requireResponse() }.map { CommonSettings.from(it, platform) }

    private companion object {
        const val PATH = "mediaview/api/v1/common"
    }
}
