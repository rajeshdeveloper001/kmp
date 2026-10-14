# SharedKmp — VPlayed shared business logic

Kotlin Multiplatform library (`:shared`) holding the business logic both VPlayed apps use:
networking, request encryption, models, validation, and later auth, session and Home. **No UI
lives here.** Screens stay native: Jetpack Compose in `AndroidJP`, SwiftUI in `VPlayed`.

The plan and its decisions are in `../KMP_PLANNING/` (architecture, API mapping, storage, migration
plan, backlog). Done so far: Phase 2 (foundation) and Phase 3 (sign-in, `auth/`), used by both apps.

## Targets

| Target | Consumer | Artifact |
|---|---|---|
| Android (`com.android.kotlin.multiplatform.library`, compile 37 / min 24, JVM 11) | AndroidJP | `com.vplayed:shared:<version>` |
| `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64` | VPlayed (iOS + macOS destinations) | `Shared.xcframework` (static, module `Shared`) |

The Intel slices exist because VPlayed builds universal simulator and macOS binaries (macOS 26 still
runs on Intel Macs). Kotlin has deprecated `macosX64`, so Intel Mac support will need a decision
before Kotlin removes it. Their tests can't run on Apple-silicon hosts. visionOS has no Kotlin/Native
target, so the VPlayed visionOS destination doesn't link `Shared` (`KMP_PLANNING/KMP_ARCHITECTURE_ANALYSIS.md` §6).

## Toolchain

Aligned with AndroidJP: Kotlin 2.4.21, AGP 9.4.1, Gradle 9.6.0. The daemon JVM is JDK 25
(`gradle/gradle-daemon-jvm.properties`). On the command line, use Android Studio's JBR:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Common tasks

```bash
./gradlew :shared:testAndroidHostTest          # common + Android tests on the JVM
./gradlew :shared:iosSimulatorArm64Test        # common + Apple tests on the iOS simulator
./gradlew :shared:macosArm64Test               # common + Apple tests natively on macOS
./gradlew :shared:assembleSharedXCFramework    # build/XCFrameworks/{debug,release}/Shared.xcframework
./gradlew :shared:publishToMavenLocal          # com.vplayed:shared:<version> in ~/.m2
```

## Package layout (`shared/src`)

```
commonMain/kotlin/com/vplayed/shared/
  SharedGraph.kt            manual DI: one per process (Hilt @Provides on Android, MyApp.init on iOS)
  core/config/              SharedConfig, ServerEnvironment, ClientPlatform
  core/platform/            DeviceInfo (implemented by each app), SharedLogger
  core/error/               AppError, DataResult, resultOf, ApiException
  core/network/             HttpClientFactory, ApiCaller, VPlayedResponse/ApiEnvelope,
                            VPlayedHeaders plugin, RequestLogging plugin, RequestOptions, RequestCredentials
  core/serialization/       SharedJson, lenient readers (lenientInt/Bool/String…, localizedText), LossyListSerializer
  core/crypto/              TransportCipher (CryptoJS/OpenSSL "Salted__" AES-256-CBC), CryptoPrimitives
  auth/                     AuthRepository + DefaultAuthRepository (email-login, verifyEmailOtp, logout-device,
                            verifyEmailLogin, generate, logout; status rules D1–D5 in its KDoc), result types,
                            EmailLoginController / OtpController (the sign-in flow: state, intents, effects),
                            AuthFlowConfig, AuthMessage / AuthMessageKey (text stays in the apps)
  validation/               EmailValidator
  session/                  SecureStore interface (implemented per platform in Phase 5)
androidMain/                OkHttp engine (+ createOkHttpEngine(OkHttpClient)), javax.crypto, Logcat
appleMain/                  Darwin engine, CommonCrypto, NSLog, FlowWatcher / StateFlowWatcher (Swift bridge),
                            controller watchState / watchEffects / currentState for Swift
commonTest/, appleTest/     tests (Ktor MockEngine, kotlinx-coroutines-test)
```

## Rules for shared code

- **No UI, no platform singletons.** No Compose, SwiftUI, UIKit, Hilt or Android `Context` in `commonMain`.
- **Public APIs never throw.** Return `DataResult` or a sealed result type. A Kotlin exception that
  reaches Swift crashes the app. Coroutine cancellation is the only thing rethrown.
- **Status codes carry meaning.** HTTP clients use `expectSuccess = false`, and repositories read both
  `VPlayedResponse.httpStatus` and `effectiveStatusCode`. Decision D1: a non-2xx HTTP status always
  fails; within 2xx the envelope's `statusCode` decides when present (`authStatus`).
- **The apps own the session until Phase 5.** Repositories return sessions; they don't store them.
  Request headers read credentials through the `RequestCredentials` the app supplies in
  `PlatformDependencies` (AndroidJP reads its stores live; VPlayed mirrors its `SessionStore`).
- **No secrets or personal data in logs or `toString()`.** Use `RequestOptions.Sensitive` for auth calls.
  `AuthTokens`, `SharedConfig` and `VPlayedResponse` mask their contents.
- **User-facing text stays native.** Shared code returns typed results or message keys. Android maps
  them to `strings.xml`, iOS to `Localizable.xcstrings`.
- **Only synthetic test fixtures.** Real API responses contain personal data.

## Consumers

| App | How it links | What uses the shared layer |
|---|---|---|
| AndroidJP | `includeBuild("../kmp")` with a substitution for `com.vplayed:shared`; Hilt `@Provides @Singleton SharedGraph` (`core/shared/SharedModule`) | Login: `LoginViewModel` / `OtpViewModel` delegate to the controllers on `viewModelScope`; Hilt provides `SharedGraph.authRepository` |
| VPlayed | `scripts/build-shared.sh` builds `Shared.xcframework` into the local `SharedKit` package (`binaryTarget`); `@preconcurrency import Shared` in adapter files | Login: `SharedLoginViewModel` on `SharedGraph.emailLoginController` / `otpController` (own main-thread scopes, closed with the screen); logout: `SharedAuthService`. visionOS has no Kotlin target and keeps Swift versions |

Swift adapters map sealed results with `as?` casts (`EmailLoginResultCodeSent`, …), read a
controller's state with `currentState` (a `StateFlow` value is untyped in Swift), observe it with
`watchState` / `watchEffects` and close the returned `Closeable`s and the controller together.
Avoid Kotlin names Objective-C reserves (an enum entry `NEW_…` becomes `theNew…`) and nested
types whose flattened Swift name collides with another type (`AuthMessage.Key` vs `AuthMessageKey`).
