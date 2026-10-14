package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.validation.EmailValidationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmailLoginControllerTest {

    private val repository = FakeAuthRepository()
    private val device = SignedInDevice("Pixel", DeviceKind.PHONE, null, false, DeviceSignOutKey("u", "t"))

    private fun TestScope.controller(deviceSignOutEnabled: Boolean = true) =
        EmailLoginController(repository, backgroundScope, AuthFlowConfig(deviceSignOutEnabled))

    private suspend fun EmailLoginController.nextMessage(): AuthMessage =
        (effects.first() as EmailLoginEffect.ShowMessage).message

    // region validation

    @Test
    fun givenEmptyEmail_whenSubmitting_thenEmptyErrorAndNoRequest() = runTest {
        val controller = controller()
        controller.onEmailChange("   ")
        controller.onSubmit()

        assertEquals(EmailValidationResult.EMPTY, controller.state.value.emailError)
        assertTrue(repository.requestedEmails.isEmpty())
    }

    @Test
    fun givenMalformedOrTooLongEmail_whenSubmitting_thenErrorAndNoRequest() = runTest {
        val controller = controller()
        controller.onEmailChange("user@example")
        controller.onSubmit()
        assertEquals(EmailValidationResult.INVALID_FORMAT, controller.state.value.emailError)

        // Decision D16: at most 50 characters, as in the UIKit app.
        controller.onEmailChange("a".repeat(40) + "@example.com")
        controller.onSubmit()
        assertEquals(EmailValidationResult.TOO_LONG, controller.state.value.emailError)
        assertTrue(repository.requestedEmails.isEmpty())
    }

    @Test
    fun givenError_whenEditingEmail_thenErrorCleared() = runTest {
        val controller = controller()
        controller.onSubmit()
        controller.onEmailChange("u")
        assertNull(controller.state.value.emailError)
    }

    @Test
    fun givenMalformedEmail_whenFieldLosesFocus_thenInvalidErrorShown() = runTest {
        val controller = controller()
        controller.onEmailChange("user@")
        controller.onEmailFocusLost()
        assertEquals(EmailValidationResult.INVALID_FORMAT, controller.state.value.emailError)
    }

    @Test
    fun givenEmptyEmail_whenFieldLosesFocus_thenNoError() = runTest {
        val controller = controller()
        controller.onEmailFocusLost()
        assertNull(controller.state.value.emailError)
    }

    // endregion

    // region outcomes

    @Test
    fun givenCodeSent_whenSubmitting_thenCodeStepForTrimmedEmailAndLoadingEnds() = runTest {
        repository.result = EmailLoginResult.CodeSent("OTP sent to your email", isNewUser = false)
        val controller = controller()
        controller.onEmailChange("  user@example.com ")

        controller.onSubmit()
        assertTrue(controller.state.value.isSubmitting)

        assertEquals(EmailLoginEffect.CodeSent(EMAIL, isNewUser = false), controller.effects.first())
        assertEquals(listOf("  user@example.com "), repository.requestedEmails)
        runCurrent()
        assertFalse(controller.state.value.isSubmitting)
    }

    @Test
    fun givenCodeSentToNewUser_whenSubmitting_thenCodeStepToo() = runTest {
        // Decision D2.
        repository.result = EmailLoginResult.CodeSent(null, isNewUser = true)
        val controller = controller()
        controller.onEmailChange(EMAIL)
        controller.onSubmit()
        assertEquals(EmailLoginEffect.CodeSent(EMAIL, isNewUser = true), controller.effects.first())
    }

    @Test
    fun givenDeviceLimitWithDevices_whenSubmitting_thenDeviceStep() = runTest {
        val limit = DeviceLimit(listOf(device), maxDevices = 1)
        repository.result = EmailLoginResult.DeviceLimitReached("Limit", limit)
        val controller = controller()
        controller.onEmailChange(EMAIL)

        controller.onSubmit()

        assertEquals(EmailLoginEffect.ShowDevices(EMAIL, limit), controller.effects.first())
    }

    @Test
    fun givenDeviceStepDisabled_whenDeviceLimit_thenServerMessageInstead() = runTest {
        // Decision D5: VPlayed keeps the device step off.
        repository.result = EmailLoginResult.DeviceLimitReached("Signed in on 2 devices", DeviceLimit(listOf(device), maxDevices = 2))
        val controller = controller(deviceSignOutEnabled = false)
        controller.onEmailChange(EMAIL)
        controller.onSubmit()
        assertEquals(AuthMessage.Server("Signed in on 2 devices"), controller.nextMessage())
    }

    @Test
    fun givenDeviceLimitWithoutDevices_whenSubmitting_thenMessage() = runTest {
        repository.result = EmailLoginResult.DeviceLimitReached(null, null)
        val controller = controller()
        controller.onEmailChange(EMAIL)
        controller.onSubmit()
        assertEquals(AuthMessage.Localized(AuthMessageKey.DEVICE_LIMIT), controller.nextMessage())
    }

    @Test
    fun givenRejected_whenSubmitting_thenServerMessageOrFallback() = runTest {
        val controller = controller()
        controller.onEmailChange(EMAIL)

        repository.result = EmailLoginResult.Rejected("Account blocked")
        controller.onSubmit()
        assertEquals(AuthMessage.Server("Account blocked"), controller.nextMessage())
        runCurrent()

        repository.result = EmailLoginResult.Rejected("<html><body>Fatal error at line 3</body></html>")
        controller.onSubmit()
        assertEquals(AuthMessage.Localized(AuthMessageKey.LOGIN_FAILED), controller.nextMessage())
    }

    @Test
    fun givenNetworkFailure_whenSubmitting_thenOfflineMessage() = runTest {
        repository.result = EmailLoginResult.Failure(AppError.Network)
        val controller = controller()
        controller.onEmailChange(EMAIL)
        controller.onSubmit()
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_NETWORK), controller.nextMessage())
        runCurrent()
        assertFalse(controller.state.value.isSubmitting)
    }

    @Test
    fun givenRequestInFlight_whenSubmittingAgainOrEditing_thenIgnored() = runTest {
        val hold = CompletableDeferred<Unit>()
        repository.hold = hold
        val controller = controller()
        controller.onEmailChange(EMAIL)

        controller.onSubmit()
        runCurrent()
        controller.onSubmit()
        controller.joinAsGuest()
        controller.onEmailChange("other@example.com")
        runCurrent()

        assertEquals(1, repository.requestedEmails.size)
        assertEquals(0, repository.guestLogins)
        assertEquals(EMAIL, controller.state.value.email)
        hold.complete(Unit)
        controller.effects.first()
        runCurrent()
        assertFalse(controller.state.value.isSubmitting)
    }

    // endregion

    // region reset and guest

    @Test
    fun givenTypedEmail_whenClosedOrCleared_thenForgotten() = runTest {
        val controller = controller()
        controller.onEmailChange(EMAIL)
        controller.onClosed()
        assertEquals("", controller.state.value.email)

        controller.onEmailChange(EMAIL)
        controller.clear()
        assertEquals("", controller.state.value.email)
    }

    @Test
    fun givenGuestAllowed_whenJoining_thenGuestSessionEffect() = runTest {
        val controller = controller()

        controller.joinAsGuest()
        assertTrue(controller.state.value.isJoiningAsGuest)

        val effect = controller.effects.first() as EmailLoginEffect.GuestSignedIn
        assertTrue(effect.session.isGuest)
        runCurrent()
        assertFalse(controller.state.value.isJoiningAsGuest)
    }

    @Test
    fun givenGuestRefusedOrOffline_whenJoining_thenMessage() = runTest {
        val controller = controller()

        repository.guestResult = GuestLoginResult.Rejected(null)
        controller.joinAsGuest()
        assertEquals(AuthMessage.Localized(AuthMessageKey.GUEST_LOGIN_FAILED), controller.nextMessage())

        runCurrent()
        repository.guestResult = GuestLoginResult.Failure(AppError.Timeout)
        controller.joinAsGuest()
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_TIMEOUT), controller.nextMessage())
    }

    // endregion

    private companion object {
        const val EMAIL = "user@example.com"
    }
}
