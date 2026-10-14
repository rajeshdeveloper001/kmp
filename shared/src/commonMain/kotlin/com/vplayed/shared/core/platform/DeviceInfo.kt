package com.vplayed.shared.core.platform

/**
 * Device facts only the platform knows. Implemented by each app (AndroidJP: `DeviceIdProvider`,
 * `DeviceNameProvider`, locale; VPlayed: `DeviceID`, `DeviceModel`, `AppLanguage`).
 */
interface DeviceInfo {
    /** Sent as `device_id`; null when the platform provides none (the header is then omitted). */
    val deviceId: String?

    /** Name shown in a user's device list (`deviceDetail` on login). */
    val deviceName: String

    /**
     * Language for `X-Language-Code`, read on every request so in-app language switches apply
     * immediately. Blank means "en".
     */
    fun languageCode(): String
}
