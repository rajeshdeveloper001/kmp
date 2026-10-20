package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from AndroidJP's `OtpViewModelTest`. Virtual time drives the resend countdown and the success delay. */
@OptIn(ExperimentalCoroutinesApi::class)
class OtpControllerTest {

    private val repository = FakeAuthRepository()

    private val laptop = SignedInDevice("Chrome on Linux", DeviceKind.DESKTOP, "Chrome", false, DeviceSignOutKey("u1", "t1"))
    private val phone = SignedInDevice("iPhone 15", DeviceKind.PHONE, null, false, DeviceSignOutKey("u2", "t2"))

    /** A controller on the test scheduler, after its start-up work ran. */
    private fun TestScope.started(initialDeviceLimit: DeviceLimit? = null, deviceSignOutEnabled: Boolean = true): OtpController =
        OtpController(EMAIL, repository, initialDeviceLimit, backgroundScope, AuthFlowConfig(deviceSignOutEnabled))
            .also { runCurrent() }

    /** The next navigation effect ([OtpEffect.SignedIn] is checked separately). */
    private suspend fun OtpController.nextEffect(): OtpEffect = effects.first { it !is OtpEffect.SignedIn }

    private fun OtpController.typeFullCode(code: String = "123456") = onCodeChange(code)

    // region code input

    @Test
    fun givenTyping_whenEnteringCharacters_thenOnlySixDigitsAreKept() = runTest {
        val controller = started()

        controller.onCodeChange("12a-34 56789")

        assertEquals("123456", controller.state.value.code)
    }

    @Test
    fun givenPastedCodeWithSpaces_whenChanged_thenDigitsKept() = runTest {
        val controller = started()
        controller.onCodeChange("123 456")
        assertEquals("123456", controller.state.value.code)
        assertTrue(controller.state.value.canVerify)
    }

    @Test
    fun givenFewerThanSixDigits_whenVerifying_thenNothingIsSent() = runTest {
        val controller = started()
        controller.onCodeChange("12345")

        assertFalse(controller.state.value.canVerify)
        controller.onVerify()
        runCurrent()

        assertTrue(repository.verifications.isEmpty())
    }

    // endregion

    // region verify

    @Test
    fun givenCorrectCode_whenVerifying_thenSuccessStepThenAuthenticatedAfterThreeSeconds() = runTest {
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        assertEquals(listOf(EMAIL to "123456"), repository.verifications)
        assertEquals(OtpStep.Success, controller.state.value.step)
        assertFalse(controller.state.value.isVerifying)
        advanceTimeBy(AuthFlowConfig.SUCCESS_MILLIS - 1)
        runCurrent()
        assertEquals(OtpStep.Success, controller.state.value.step) // still celebrating, nothing navigated
        advanceTimeBy(2)
        runCurrent()
        assertEquals(OtpEffect.Authenticated, controller.nextEffect())
    }

    @Test
    fun givenIncorrectCode_whenVerifying_thenInlineErrorShakeAndEmptyBoxesLikeThePrototype() = runTest {
        repository.verifyResult = OtpVerificationResult.Rejected("OTP is invalid")
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        val state = controller.state.value
        assertEquals(OtpStep.Code, state.step)
        assertEquals(AuthMessage.Server("OTP is invalid"), state.error)
        assertEquals("", state.code)
        assertEquals(1, state.shakeCount)
        assertFalse(state.isVerifying)
        controller.onCodeChange("1")
        assertNull(controller.state.value.error, "Editing clears the error")
        assertEquals(1, controller.state.value.shakeCount, "The shake counter only ever grows")
    }

    @Test
    fun givenTwoRefusedCodes_whenVerifying_thenShakesEachTime() = runTest {
        repository.verifyResult = OtpVerificationResult.Rejected(null)
        val controller = started()
        repeat(2) {
            controller.typeFullCode()
            controller.onVerify()
            runCurrent()
        }
        assertEquals(2, controller.state.value.shakeCount)
    }

    @Test
    fun givenExpiredCodeWithoutMessage_whenVerifying_thenFallbackMessage() = runTest {
        repository.verifyResult = OtpVerificationResult.Rejected(null)
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        assertEquals(AuthMessage.Localized(AuthMessageKey.OTP_INVALID), controller.state.value.error)
    }

    @Test
    fun givenNetworkFailure_whenVerifying_thenInlineOfflineErrorWithCodeKeptAndNoShake() = runTest {
        repository.verifyResult = OtpVerificationResult.Failure(AppError.Network)
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_NETWORK), controller.state.value.error)
        assertEquals("123456", controller.state.value.code)
        assertEquals(0, controller.state.value.shakeCount)
        assertFalse(controller.state.value.isVerifying)
        assertEquals(OtpStep.Code, controller.state.value.step)
    }

    @Test
    fun givenInlineError_whenVerifyingAgain_thenErrorIsClearedWhileRetrying() = runTest {
        repository.verifyResult = OtpVerificationResult.Failure(AppError.Network)
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_NETWORK), controller.state.value.error)

        repository.verifyHold = CompletableDeferred()
        controller.onVerify()
        runCurrent()

        assertEquals(null, controller.state.value.error)
    }

    @Test
    fun givenVerificationInFlight_whenVerifyingAgainOrEditing_thenIgnored() = runTest {
        val hold = CompletableDeferred<Unit>()
        repository.verifyHold = hold
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()
        assertTrue(controller.state.value.isVerifying)
        controller.onVerify()
        controller.onCodeChange("654321")
        runCurrent()

        assertEquals(1, repository.verifications.size)
        assertEquals("123456", controller.state.value.code)
        hold.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.isVerifying)
    }

    @Test
    fun givenNewUser_whenVerified_thenSuccessStepHasNoNoticeAndStillContinues() = runTest {
        repository.verifyResult = verified(isNewUser = true, hasMultipleProfiles = true)
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        // Sign-up and the first-profile choice exist now: no "coming soon" notice.
        assertNull(controller.state.value.notice)
        advanceTimeBy(AuthFlowConfig.SUCCESS_MILLIS + 1)
        runCurrent()
        assertEquals(OtpEffect.Authenticated, controller.nextEffect())
    }

    @Test
    fun givenSeveralProfiles_whenVerified_thenSuccessStepHasNoNotice() = runTest {
        repository.verifyResult = verified(hasMultipleProfiles = true)
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        // The first profile is used without a picker, so nothing is announced.
        assertNull(controller.state.value.notice)
    }

    @Test
    fun givenPlainLogin_whenVerified_thenNoNotice() = runTest {
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        assertEquals(null, controller.state.value.notice)
    }

    @Test
    fun givenRegistrationRequired_whenVerifying_thenInlineMessageAndNoSuccess() = runTest {
        repository.verifyResult = OtpVerificationResult.RegistrationRequired(null)
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        assertEquals(AuthMessage.Localized(AuthMessageKey.REGISTRATION_REQUIRED), controller.state.value.error)
        assertEquals(OtpStep.Code, controller.state.value.step)
    }

    // endregion

    // region resend + countdown

    @Test
    fun givenScreenOpened_whenTimePasses_thenResendCountsDownFromSixtyThenEnables() = runTest {
        val controller = started()
        assertEquals(60, controller.state.value.resendSecondsLeft)
        assertFalse(controller.state.value.canResend)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(30, controller.state.value.resendSecondsLeft)

        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(0, controller.state.value.resendSecondsLeft)
        assertTrue(controller.state.value.canResend)
    }

    @Test
    fun givenCountdownRunning_whenResending_thenNothingIsRequested() = runTest {
        val controller = started()
        controller.onResend()
        runCurrent()
        assertTrue(repository.requestedEmails.isEmpty())
    }

    @Test
    fun givenCountdownFinished_whenResending_thenNewCodeRequestedBoxesClearedAndCountdownRestartsWithoutAnyMessage() = runTest {
        repository.result = EmailLoginResult.CodeSent("A new code is on its way", isNewUser = false)
        val controller = started()
        controller.onCodeChange("123")
        advanceTimeBy(61_000)
        runCurrent()

        controller.onResend()
        runCurrent()

        assertEquals(listOf(EMAIL), repository.requestedEmails)
        assertEquals("", controller.state.value.code)
        assertEquals(60, controller.state.value.resendSecondsLeft)
        assertFalse(controller.state.value.isResending)
        assertEquals(null, controller.state.value.error)
    }

    @Test
    fun givenResendFails_whenResending_thenInlineErrorInputKeptCountdownNotRestartedAndRetryPossible() = runTest {
        repository.result = EmailLoginResult.Rejected("Too many requests")
        val controller = started()
        controller.onCodeChange("123")
        advanceTimeBy(61_000)
        runCurrent()

        controller.onResend()
        runCurrent()

        assertEquals("123", controller.state.value.code)
        assertEquals(0, controller.state.value.resendSecondsLeft)
        assertTrue(controller.state.value.canResend)
        assertEquals(AuthMessage.Server("Too many requests"), controller.state.value.error)
        assertEquals(0, controller.state.value.shakeCount)
    }

    @Test
    fun givenResendInFlight_whenResendingAgain_thenOnlyOneRequest() = runTest {
        val hold = CompletableDeferred<Unit>()
        repository.hold = hold
        val controller = started()
        advanceTimeBy(61_000)
        runCurrent()

        controller.onResend()
        runCurrent()
        assertTrue(controller.state.value.isResending)
        controller.onResend()
        runCurrent()

        assertEquals(1, repository.requestedEmails.size)
        hold.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.isResending)
    }

    @Test
    fun givenVerifying_whenCountdownFinishes_thenResendStaysDisabledUntilDone() = runTest {
        val hold = CompletableDeferred<Unit>()
        repository.verifyHold = hold
        val controller = started()
        controller.typeFullCode()
        advanceTimeBy(61_000)
        runCurrent()

        controller.onVerify()
        runCurrent()

        assertFalse(controller.state.value.canResend)
        hold.complete(Unit)
    }

    @Test
    fun givenSuccess_whenTimePasses_thenCountdownNoLongerRuns() = runTest {
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        // Verified: the countdown stops, there is nothing left to resend.
        assertEquals(0, controller.state.value.resendSecondsLeft)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, controller.state.value.resendSecondsLeft)
    }

    // endregion

    // region back

    @Test
    fun givenCodeStep_whenGoingBackOrChangingEmail_thenNavigatesBack() = runTest {
        val controller = started()
        controller.onBack()
        runCurrent()
        assertEquals(OtpEffect.NavigateBack, controller.nextEffect())
    }

    @Test
    fun givenVerifying_whenGoingBack_thenIgnored() = runTest {
        repository.verifyHold = CompletableDeferred()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onBack()
        runCurrent()

        assertEquals(OtpStep.Code, controller.state.value.step)
        assertTrue(controller.state.value.isVerifying)
    }

    // endregion

    // region devices

    private fun deviceLimitFromVerify() {
        repository.verifyResult = OtpVerificationResult.DeviceLimitReached("Limit", DeviceLimit(listOf(laptop, phone), maxDevices = 2))
    }

    @Test
    fun givenDeviceLimit_whenVerifying_thenDeviceStepListsDevicesAndStopsCountdown() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        assertEquals(OtpStep.Devices, controller.state.value.step)
        assertEquals(listOf(laptop, phone), controller.state.value.devices)
        assertEquals(2, controller.state.value.maxDevices)
        assertFalse(controller.state.value.hasSignedOutDevice)
    }

    @Test
    fun givenDeviceStepAfterVerify_whenGoingBack_thenCodeStepWithEmptyBoxes() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onBack()
        runCurrent()

        assertEquals(OtpStep.Code, controller.state.value.step)
        assertEquals("", controller.state.value.code)
    }

    @Test
    fun givenDeviceStepAfterVerify_whenSigningOutOneDevice_thenItLeavesTheListAndContinueAppears() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onSignOutDevice(laptop)
        runCurrent()

        assertEquals(listOf(laptop.signOutKey to false), repository.signOuts)
        assertEquals(listOf(phone), controller.state.value.devices)
        assertTrue(controller.state.value.hasSignedOutDevice)
        assertEquals(OtpStep.Devices, controller.state.value.step)
    }

    @Test
    fun givenSignedOutADevice_whenContinuing_thenVerifyEmailLoginIsUsedAndSuccessShown() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        controller.onSignOutDevice(laptop)
        runCurrent()

        controller.onContinueAfterSignOut()
        runCurrent()

        assertEquals(listOf(EMAIL), repository.continuedEmails)
        assertEquals(OtpStep.Success, controller.state.value.step)
    }

    @Test
    fun givenNoDeviceSignedOutYet_whenContinuing_thenIgnored() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onContinueAfterSignOut()
        runCurrent()

        assertTrue(repository.continuedEmails.isEmpty())
    }

    @Test
    fun givenSignOutAll_whenSucceeds_thenFlowContinuesAutomatically() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onSignOutAllDevices()
        runCurrent()

        assertEquals(listOf(laptop.signOutKey to true), repository.signOuts)
        assertEquals(listOf(EMAIL), repository.continuedEmails)
        assertEquals(OtpStep.Success, controller.state.value.step)
    }

    @Test
    fun givenLastDeviceSignedOut_whenSucceeds_thenFlowContinuesAutomatically() = runTest {
        repository.verifyResult = OtpVerificationResult.DeviceLimitReached(null, DeviceLimit(listOf(laptop), maxDevices = 1))
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()

        controller.onSignOutDevice(laptop)
        runCurrent()

        assertEquals(OtpStep.Success, controller.state.value.step)
    }

    @Test
    fun givenSignOutFails_whenSigningOut_thenDeviceStaysAndInlineErrorShown() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        repository.signOutResult = SignOutDeviceResult.Rejected("Could not sign out")

        controller.onSignOutDevice(laptop)
        runCurrent()

        assertEquals(listOf(laptop, phone), controller.state.value.devices)
        assertFalse(controller.state.value.isSigningOut)
        assertEquals(AuthMessage.Server("Could not sign out"), controller.state.value.error)
    }

    @Test
    fun givenSignOutInFlight_whenSigningOutAnother_thenIgnored() = runTest {
        deviceLimitFromVerify()
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        val hold = CompletableDeferred<Unit>()
        repository.signOutHold = hold

        controller.onSignOutDevice(laptop)
        runCurrent()
        controller.onSignOutDevice(phone)
        controller.onSignOutAllDevices()
        runCurrent()

        assertEquals(1, repository.signOuts.size)
        hold.complete(Unit)
    }

    @Test
    fun givenDeviceLimitFromEmailLogin_whenOpened_thenDeviceStepFromHandoffWithNoCountdown() = runTest {
        val controller = started(initialDeviceLimit = DeviceLimit(listOf(laptop, phone), maxDevices = 2))

        assertEquals(OtpStep.Devices, controller.state.value.step)
        assertEquals(listOf(laptop, phone), controller.state.value.devices)
        assertEquals(0, controller.state.value.resendSecondsLeft)
    }

    @Test
    fun givenDeviceStepFromEmailLogin_whenGoingBack_thenLeavesToEmailStep() = runTest {
        val controller = started(initialDeviceLimit = DeviceLimit(listOf(laptop), maxDevices = 1))

        controller.onBack()
        runCurrent()

        assertEquals(OtpEffect.NavigateBack, controller.nextEffect())
    }

    @Test
    fun givenDeviceStepFromEmailLogin_whenContinuingAfterSignOut_thenNewCodeRequestedAndCodeStepShown() = runTest {
        val controller = started(initialDeviceLimit = DeviceLimit(listOf(laptop, phone), maxDevices = 2))
        controller.onSignOutDevice(laptop)
        runCurrent()

        controller.onContinueAfterSignOut()
        runCurrent()

        assertEquals(listOf(EMAIL), repository.requestedEmails)
        assertTrue(repository.continuedEmails.isEmpty())
        assertEquals(OtpStep.Code, controller.state.value.step)
        assertEquals(60, controller.state.value.resendSecondsLeft)
    }

    @Test
    fun givenDeviceStepDisabled_whenStartedWithDeviceLimit_thenCodeStep() = runTest {
        val controller = started(initialDeviceLimit = DeviceLimit(listOf(laptop), maxDevices = 1), deviceSignOutEnabled = false)
        assertEquals(OtpStep.Code, controller.state.value.step)
        assertEquals(60, controller.state.value.resendSecondsLeft)
    }

    @Test
    fun givenDeviceStepDisabled_whenVerifyAnswersDeviceLimit_thenServerMessageOnTheCodeStep() = runTest {
        // Decision D5: VPlayed shows the message instead of the device list.
        repository.verifyResult = OtpVerificationResult.DeviceLimitReached("Signed in on 2 devices", DeviceLimit(listOf(laptop, phone), maxDevices = 2))
        val controller = started(deviceSignOutEnabled = false)
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        assertEquals(OtpStep.Code, controller.state.value.step)
        assertEquals(AuthMessage.Server("Signed in on 2 devices"), controller.state.value.error)
        assertTrue(controller.state.value.devices.isEmpty())
    }

    // endregion

    // region session

    @Test
    fun givenCorrectCode_whenVerified_thenSessionIsHandedOverBeforeAuthenticated() = runTest {
        repository.verifyResult = verified(isNewUser = true)
        val controller = started()
        controller.typeFullCode()

        controller.onVerify()
        runCurrent()

        assertEquals(OtpEffect.SignedIn(testSession, isNewUser = true, hasMultipleProfiles = false), controller.effects.first())
        advanceTimeBy(AuthFlowConfig.SUCCESS_MILLIS + 1)
        runCurrent()
        assertEquals(OtpEffect.Authenticated, controller.effects.first())
    }

    @Test
    fun givenRefusedCode_whenVerifying_thenNoSession() = runTest {
        repository.verifyResult = OtpVerificationResult.Rejected(null)
        val controller = started()
        controller.typeFullCode()
        controller.onVerify()
        runCurrent()
        controller.onBack()

        // No SignedIn came before it.
        assertEquals(OtpEffect.NavigateBack, controller.effects.first())
    }

    @Test
    fun givenOwnedScope_whenClosed_thenCountdownStops() = runTest {
        // A scope of its own, as SharedGraph.otpController creates.
        val owned = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val controller = OtpController(EMAIL, repository, null, owned, AuthFlowConfig(deviceSignOutEnabled = false))
        advanceTimeBy(5_000)
        runCurrent()
        val left = controller.state.value.resendSecondsLeft

        controller.close()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(left, controller.state.value.resendSecondsLeft)
    }

    // endregion

    private companion object {
        const val EMAIL = "user@example.com"
    }
}
