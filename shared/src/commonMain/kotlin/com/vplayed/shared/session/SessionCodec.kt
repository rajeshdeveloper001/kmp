package com.vplayed.shared.session

import com.vplayed.shared.auth.AuthSession
import com.vplayed.shared.core.serialization.SharedJson
import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientLong
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The stored form of an [AuthSession]: one JSON object, kept only inside a [SecureStore].
 *
 * [decode] also reads what the apps stored before the shared session manager, so an upgrade keeps
 * the user signed in:
 * - AndroidJP `UserSession`: `userId`, `email`, `displayName`, `accessToken`, `refreshToken`, `profileId`;
 * - VPlayed `AuthCredentials`: `accessToken`, `refreshToken`, `userID`, `uniqueUserID`, `email`,
 *   `phone`, `firstName`, `isGuest`, `profileID`.
 *
 * [encode] writes `format` 1 with the keys below. An older build reading it keeps the user signed
 * in where it can (VPlayed reads it; AndroidJP needs `userId` and `email`, else it signs out).
 */
internal object SessionCodec {
    private const val FORMAT = "format"
    private const val CURRENT_FORMAT = 1

    fun encode(session: AuthSession): String = buildJsonObject {
        put(FORMAT, CURRENT_FORMAT)
        put("accessToken", session.accessToken)
        session.refreshToken?.let { put("refreshToken", it) }
        session.userId?.let { put("userId", it) }
        session.uniqueUserId?.let { put("uniqueUserId", it) }
        session.email?.let { put("email", it) }
        session.phone?.let { put("phone", it) }
        session.firstName?.let { put("firstName", it) }
        session.profileId?.let { put("profileId", it) }
        put("isGuest", session.isGuest)
    }.toString()

    /** Null when [text] is not a session (malformed JSON, no access token): the caller signs out. */
    fun decode(text: String): AuthSession? {
        val stored = try {
            SharedJson.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        fun text(vararg keys: String) = keys.firstNotNullOfOrNull { stored[it].lenientString() }
        return AuthSession(
            accessToken = text("accessToken") ?: return null,
            refreshToken = text("refreshToken"),
            userId = text("userId", "userID"),
            uniqueUserId = stored["uniqueUserId"].lenientLong() ?: stored["uniqueUserID"].lenientLong(),
            email = text("email"),
            phone = text("phone"),
            firstName = text("firstName", "displayName"),
            profileId = text("profileId", "profileID"),
            isGuest = stored["isGuest"].lenientBool() ?: false,
        )
    }
}
