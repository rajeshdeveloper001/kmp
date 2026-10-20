package com.vplayed.shared.auth

import com.vplayed.shared.phone.PhoneNumber
import com.vplayed.shared.profile.SessionProfileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OtpStep { Code, Devices, Success }

data class OtpState(
    /** Where the code went, as shown: the email, or the phone number (`+91 98765 43210`). */
    val email: String,
    val step: OtpStep = OtpStep.Code,
    /** Digits typed so far (0–[AuthFlowConfig.CODE_LENGTH]). */
    val code: String = "",
    /**
     * Inline error under the code boxes (or the device list): a refused code, a failed resend or
     * sign-out, or no connection. Cleared by the next edit or action.
     */
    val error: AuthMessage? = null,
    /** Bumped for each refused code; the screen shakes once per change (prototype `wpShake`). */
    val shakeCount: Int = 0,
    /** Informational line on the success step; none is set at present. */
    val notice: AuthMessage? = null,
    val isVerifying: Boolean = false,
    /** Seconds until a new code can be requested; 0 = Resend is available. */
    val resendSecondsLeft: Int = 0,
    val isResending: Boolean = false,
    val devices: List<SignedInDevice> = emptyList(),
    val maxDevices: Int? = null,
    /** A sign-out or continue request is in flight on the device step. */
    val isSigningOut: Boolean = false,
    /** At least one device was signed out: the device step offers Continue. */
    val hasSignedOutDevice: Boolean = false,
    /** Email, phone (SMS) or social sign-in (device step only). */
    val channel: OtpChannelKind = OtpChannelKind.EMAIL,
    /** A neutral line under the code boxes (e.g. "code sent"); cleared by the next edit or action. */
    val info: AuthMessage? = null,
) {
    val canVerify: Boolean get() = code.length == AuthFlowConfig.CODE_LENGTH && !isVerifying
    val canResend: Boolean get() = resendSecondsLeft == 0 && !isResending && !isVerifying
}

sealed interface OtpEffect {
    /**
     * The user is signed in: the app stores [session] now (the success step is showing). Comes
     * before [Authenticated].
     */
    data class SignedIn(val session: AuthSession, val isNewUser: Boolean, val hasMultipleProfiles: Boolean) : OtpEffect

    /** The success step has been shown for [AuthFlowConfig.successMillis]: leave sign-in. */
    data object Authenticated : OtpEffect

    /** Leave the code step for the email step (Back, Change). */
    data object NavigateBack : OtpEffect

    /**
     * The account must finish sign-up first: open the sign-up form (`RegistrationController`) with
     * [registration]. Nothing is stored yet.
     */
    data class RegistrationNeeded(val registration: PendingRegistration) : OtpEffect
}

/**
 * The code step of sign-in, the device-limit step and the success step (prototype "VPlayed Sign Up"
 * steps 2–3), shared by AndroidJP's `OtpViewModel` and VPlayed's login adapter, for email, phone and
 * (device step only) Google / Apple sign-in. Every answer is read from typed results; a code is
 * verified only when it has exactly [AuthFlowConfig.CODE_LENGTH] digits and nothing else is in
 * flight. The resend countdown and the success delay run in [scope], so they end with it.
 *
 * Starts on the device step when [initialDeviceLimit] is given (the login request was already
 * "device limit") and the step is enabled; else phone sign-in sends the first SMS, and email sign-in
 * (whose code the email step requested) starts the countdown.
 */
class OtpController internal constructor(
    private val channel: OtpChannel,
    initialDeviceLimit: DeviceLimit?,
    private val scope: CoroutineScope,
    private val config: AuthFlowConfig,
    /** Picks the profile of the session before the app gets it (`SharedGraph.profileChooser`). */
    private val profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
) {
    /** Email sign-in, after the email step's code request. */
    constructor(
        email: String,
        repository: AuthRepository,
        initialDeviceLimit: DeviceLimit?,
        scope: CoroutineScope,
        config: AuthFlowConfig,
        profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
    ) : this(EmailOtpChannel(email, repository), initialDeviceLimit, scope, config, profileChooser)

    /** Where a device-limit answer came from decides what Continue and Back do. */
    private enum class DeviceFlow { AfterLoginRequest, AfterVerify }

    private var deviceFlow = DeviceFlow.AfterVerify
    private var countdownJob: Job? = null

    private val mutableState = MutableStateFlow(OtpState(email = channel.destination, channel = channel.kind))
    val state: StateFlow<OtpState> = mutableState.asStateFlow()

    private val effectChannel = Channel<OtpEffect>(Channel.BUFFERED)
    val effects: Flow<OtpEffect> = effectChannel.receiveAsFlow()

    init {
        when {
            initialDeviceLimit != null && config.deviceSignOutEnabled ->
                // Social sign-in has no code step: its Continue signs in again with the same credential.
                showDevices(initialDeviceLimit, if (channel.hasCodeStep) DeviceFlow.AfterLoginRequest else DeviceFlow.AfterVerify)
            channel.sendsCodeOnStart -> requestCode()
            // The email step just asked the backend to send the code.
            channel.hasCodeStep -> startCountdown()
        }
    }

    fun onCodeChange(value: String) {
        mutableState.update { current ->
            if (current.isVerifying || current.step != OtpStep.Code) {
                current
            } else {
                current.copy(code = value.filter { it in '0'..'9' }.take(AuthFlowConfig.CODE_LENGTH), error = null, info = null)
            }
        }
    }

    fun onVerify() {
        val current = mutableState.value
        if (current.step != OtpStep.Code || !current.canVerify) return
        mutableState.update { it.copy(isVerifying = true, error = null, info = null) }
        scope.launch {
            try {
                handleVerification(channel.verify(current.code), fromCodeStep = true)
            } finally {
                mutableState.update { it.copy(isVerifying = false) }
            }
        }
    }

    fun onResend() {
        if (mutableState.value.step != OtpStep.Code || !mutableState.value.canResend) return
        requestCode()
    }

    /** Resend, and phone sign-in's first SMS. */
    private fun requestCode() {
        mutableState.update { it.copy(isResending = true, error = null, info = null) }
        scope.launch {
            try {
                handleCodeRequest(channel.requestCode())
            } finally {
                mutableState.update { it.copy(isResending = false) }
            }
        }
    }

    /** Back arrow, "Change" and the system Back button. */
    fun onBack() {
        val current = mutableState.value
        when {
            // Busy or celebrating: stay put.
            current.isVerifying || current.isSigningOut || current.step == OtpStep.Success -> Unit
            current.step == OtpStep.Devices && deviceFlow == DeviceFlow.AfterVerify && channel.hasCodeStep ->
                mutableState.update { it.copy(step = OtpStep.Code, code = "", error = null, devices = emptyList()) }
            else -> scope.launch { effectChannel.send(OtpEffect.NavigateBack) }
        }
    }

    // region devices

    fun onSignOutDevice(device: SignedInDevice) {
        if (mutableState.value.isSigningOut || device !in mutableState.value.devices) return
        signOut(device, all = false)
    }

    fun onSignOutAllDevices() {
        val first = mutableState.value.devices.firstOrNull() ?: return
        if (mutableState.value.isSigningOut) return
        signOut(first, all = true)
    }

    fun onContinueAfterSignOut() {
        val current = mutableState.value
        if (current.isSigningOut || current.step != OtpStep.Devices || !current.hasSignedOutDevice) return
        continueAfterSignOut()
    }

    private fun signOut(device: SignedInDevice, all: Boolean) {
        mutableState.update { it.copy(isSigningOut = true, error = null) }
        scope.launch {
            var continueNow = false
            try {
                when (val result = channel.signOutDevice(device.signOutKey, all)) {
                    SignOutDeviceResult.Success -> {
                        val remaining = if (all) emptyList() else mutableState.value.devices - device
                        // The row leaving the list is the feedback. Prototype: with no devices left the flow
                        // continues by itself.
                        mutableState.update { it.copy(devices = remaining, hasSignedOutDevice = true) }
                        continueNow = remaining.isEmpty()
                    }
                    is SignOutDeviceResult.Rejected ->
                        showError(AuthMessage.serverOr(result.message, AuthMessageKey.DEVICE_SIGN_OUT_FAILED))
                    is SignOutDeviceResult.Failure -> showError(AuthMessage.from(result.error))
                }
            } finally {
                mutableState.update { it.copy(isSigningOut = false) }
            }
            if (continueNow) continueAfterSignOut()
        }
    }

    private fun continueAfterSignOut() {
        mutableState.update { it.copy(isSigningOut = true, error = null) }
        scope.launch {
            try {
                when (deviceFlow) {
                    DeviceFlow.AfterVerify -> handleVerification(channel.completeWithoutCode(), fromCodeStep = false)
                    DeviceFlow.AfterLoginRequest -> handleCodeRequest(channel.retryLoginRequest())
                }
            } finally {
                mutableState.update { it.copy(isSigningOut = false) }
            }
        }
    }

    /** A code request's outcome; a device limit here came from the login request itself. */
    private suspend fun handleCodeRequest(outcome: CodeRequestOutcome) {
        when (outcome) {
            is CodeRequestOutcome.Sent -> {
                // A new code replaces the old one, so the boxes start empty again.
                mutableState.update {
                    it.copy(step = OtpStep.Code, code = "", error = null, info = outcome.info, devices = emptyList())
                }
                startCountdown()
            }
            is CodeRequestOutcome.DeviceLimitReached -> handleDeviceLimit(outcome.message, outcome.limit, DeviceFlow.AfterLoginRequest)
            is CodeRequestOutcome.Refused -> showError(outcome.message)
            CodeRequestOutcome.AutoVerified -> {
                mutableState.update { it.copy(isVerifying = true) }
                try {
                    handleVerification(channel.completeWithoutCode(), fromCodeStep = false)
                } finally {
                    mutableState.update { it.copy(isVerifying = false) }
                }
            }
        }
    }

    private fun handleDeviceLimit(message: String?, limit: DeviceLimit?, flow: DeviceFlow) {
        if (limit != null && config.deviceSignOutEnabled) {
            showDevices(limit, flow)
        } else {
            showError(AuthMessage.serverOr(message, AuthMessageKey.DEVICE_LIMIT))
        }
    }

    private fun showDevices(limit: DeviceLimit, flow: DeviceFlow) {
        deviceFlow = flow
        countdownJob?.cancel()
        mutableState.update {
            it.copy(
                step = OtpStep.Devices,
                error = null,
                devices = limit.devices,
                maxDevices = limit.maxDevices,
                hasSignedOutDevice = false,
                resendSecondsLeft = 0,
            )
        }
    }

    // endregion

    private suspend fun handleVerification(result: OtpVerificationResult, fromCodeStep: Boolean) {
        channel.registrationFor(result)?.let { registration ->
            countdownJob?.cancel()
            mutableState.update { it.copy(code = "", error = null, info = null, resendSecondsLeft = 0) }
            effectChannel.send(OtpEffect.RegistrationNeeded(registration))
            return
        }
        when (result) {
            is OtpVerificationResult.Verified -> {
                countdownJob?.cancel()
                // The first profile, without a picker (not built yet).
                val session = profileChooser.choose(result.session)
                effectChannel.send(OtpEffect.SignedIn(session, result.isNewUser, result.hasMultipleProfiles))
                mutableState.update {
                    it.copy(step = OtpStep.Success, code = "", error = null, info = null, notice = null, resendSecondsLeft = 0)
                }
                scope.launch {
                    delay(config.successMillis)
                    effectChannel.send(OtpEffect.Authenticated)
                }
            }
            is OtpVerificationResult.RegistrationRequired ->
                showError(AuthMessage.serverOr(result.message, AuthMessageKey.REGISTRATION_REQUIRED))
            is OtpVerificationResult.DeviceLimitReached -> handleDeviceLimit(result.message, result.limit, DeviceFlow.AfterVerify)
            // Invalid and expired codes are both "refused"; the server's message says which. As the
            // prototype's incorrect-PIN card: inline message, one shake, boxes cleared.
            is OtpVerificationResult.Rejected -> showError(
                AuthMessage.serverOr(result.message, AuthMessageKey.OTP_INVALID),
                shake = fromCodeStep,
                clearCode = fromCodeStep,
            )
            is OtpVerificationResult.Failure -> showError(AuthMessage.from(result.error))
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (left in config.resendSeconds downTo 1) {
                mutableState.update { it.copy(resendSecondsLeft = left) }
                delay(1_000)
            }
            mutableState.update { it.copy(resendSecondsLeft = 0) }
        }
    }

    /** Shows [message] inline. A refused code also shakes the card once and empties the boxes (prototype). */
    private fun showError(message: AuthMessage, shake: Boolean = false, clearCode: Boolean = false) {
        mutableState.update {
            it.copy(
                error = message,
                info = null,
                shakeCount = if (shake) it.shakeCount + 1 else it.shakeCount,
                code = if (clearCode) "" else it.code,
            )
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope (`SharedGraph.otpController`). */
    fun close() = scope.cancel()

    companion object {
        /** Phone sign-in after `phone-login` "0": texts the first code (unless [initialDeviceLimit] opens the device step). */
        fun phone(
            phone: PhoneNumber,
            repository: PhoneAuthRepository,
            accounts: AuthRepository,
            sender: PhoneOtpSender,
            initialDeviceLimit: DeviceLimit?,
            scope: CoroutineScope,
            config: AuthFlowConfig,
            profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
        ): OtpController =
            OtpController(PhoneOtpChannel(phone, repository, sender, accounts), initialDeviceLimit, scope, config, profileChooser)

        /** Google / Apple sign-in at its device limit: the device step, then the same credential again. */
        fun socialDevices(
            credential: SocialCredential,
            repository: SocialAuthRepository,
            accounts: AuthRepository,
            limit: DeviceLimit,
            scope: CoroutineScope,
            config: AuthFlowConfig,
            profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
        ): OtpController = OtpController(SocialOtpChannel(credential, repository, accounts), limit, scope, config, profileChooser)
    }
}
