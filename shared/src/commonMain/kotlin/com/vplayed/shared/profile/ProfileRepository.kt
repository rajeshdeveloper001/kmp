package com.vplayed.shared.profile

import com.vplayed.shared.auth.AuthSession
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.RequestOptions
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientInt
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A viewing profile of an account (UIKit `MultiProfileModel`). Its id is sent as `profile_id`. */
data class ViewingProfile(
    /** `_id`. */
    val id: String,
    val name: String?,
    /** `is_pin_lock_enable`: opening it asks for the profile PIN. */
    val isPinLocked: Boolean,
    /** `is_default_profile` = 1. */
    val isDefault: Boolean,
)

sealed interface ProfileListResult {
    /** In the backend's order. */
    data class Loaded(val profiles: List<ViewingProfile>) : ProfileListResult

    data class Failure(val error: AppError) : ProfileListResult
}

/** The account's viewing profiles (UIKit `MultiProfileViewModel`). */
interface ProfileRepository {
    /** `users/api/v1/muti-profile/list` ("muti" is the backend's spelling), asked with [session]'s token. */
    suspend fun listProfiles(session: AuthSession): ProfileListResult
}

class DefaultProfileRepository internal constructor(private val api: ApiCaller) : ProfileRepository {

    override suspend fun listProfiles(session: AuthSession): ProfileListResult {
        // The session may not be stored yet (it is chosen before the app stores it).
        val options = RequestOptions.pendingSession(session.requestTokens).copy(redact = false)
        return when (val result = api.get(PROFILE_LIST, options = options)) {
            is DataResult.Failure -> ProfileListResult.Failure(result.error)
            is DataResult.Success -> {
                val answer = result.data
                val status = answer.outcomeStatus
                if (status !in 200..299 || answer.reportsError) {
                    return ProfileListResult.Failure(AppError.Server(status, answer.message))
                }
                val list = ((answer.envelope?.response as? JsonObject)?.get("data") as? JsonArray)
                    ?: return ProfileListResult.Failure(AppError.Parsing)
                ProfileListResult.Loaded(list.mapNotNull { (it as? JsonObject)?.toProfile() })
            }
        }
    }

    private fun JsonObject.toProfile(): ViewingProfile? {
        val id = this["_id"].lenientString() ?: return null
        return ViewingProfile(
            id = id,
            name = this["first_name"].lenientString(),
            isPinLocked = this["is_pin_lock_enable"].lenientBool() == true,
            isDefault = this["is_default_profile"].lenientInt() == 1,
        )
    }

    internal companion object {
        const val PROFILE_LIST = "users/api/v1/muti-profile/list"
    }
}

/**
 * Picks the profile a new sign-in uses, before the app stores the session. Both apps get it from
 * `SharedGraph.profileChooser`; the sign-in controllers call it.
 */
fun interface SessionProfileChooser {
    /** [session] with the chosen `profileId`; [session] itself when nothing better is known. */
    suspend fun choose(session: AuthSession): AuthSession

    companion object {
        /** Keeps the profile the login answer named (`default_profile`). For tests and previews. */
        val KeepDefault = SessionProfileChooser { it }
    }
}

/**
 * Uses the account's first profile, without the profile picker (not built yet; the UIKit app shows
 * it for several profiles). A PIN-locked profile is skipped — opening one needs its PIN — so the first
 * profile without a lock is used. Guests, an empty list, a failed request or only locked profiles keep
 * the login answer's `default_profile`.
 */
internal class FirstProfileChooser(
    private val repository: ProfileRepository,
    private val logger: SharedLogger,
) : SessionProfileChooser {

    override suspend fun choose(session: AuthSession): AuthSession {
        if (session.isGuest) return session
        return when (val result = repository.listProfiles(session)) {
            is ProfileListResult.Loaded -> {
                val first = result.profiles.firstOrNull { !it.isPinLocked }
                if (first == null) logger.info(TAG, "No unlocked profile; keeping the default profile")
                first?.let { session.copy(profileId = it.id) } ?: session
            }
            is ProfileListResult.Failure -> {
                logger.info(TAG, "Profile list failed: ${result.error::class.simpleName}; keeping the default profile")
                session
            }
        }
    }

    private companion object {
        const val TAG = "ProfileChooser"
    }
}
