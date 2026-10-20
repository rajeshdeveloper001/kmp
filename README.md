# SharedKmp — VPlayed shared business logic

Kotlin Multiplatform library (`:shared`) holding the business logic both VPlayed apps use:
networking, request encryption, models, validation, sign-in, Home, geo restriction, launch
settings and the session. **No UI
lives here.** Screens stay native: Jetpack Compose in `AndroidJP`, SwiftUI in `VPlayed`.

The plan and its decisions are in `../KMP_PLANNING/` (architecture, API mapping, storage, migration
plan, backlog). Done so far: Phase 2 (foundation), Phase 3 (sign-in, `auth/`), Phase 4 (Home,
geo restriction, launch settings: `home/`, `geo/`, `settings/`) and Phase 5 (session: `session/`),
used by both apps.

## Targets

| Target | Consumer | Artifact |
|---|---|---|
| Android (`com.android.kotlin.multiplatform.library`, compile 37 / min 24, JVM 11) | AndroidJP | `com.vplayed:shared:<version>` |
| `iosArm64`, `iosSimulatorArm64`, `iosX64` | VPlayed (iPhone, iPad) | `Shared.xcframework` (static, module `Shared`) |

VPlayed is an iOS-only target. It runs on Apple-silicon Macs and Apple Vision Pro as the iPad app
("Designed for iPad"), which uses the `iosArm64` slice, so there are no macOS or visionOS targets.
`iosX64` is for universal simulator builds; its tests can't run on Apple-silicon hosts.

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
./gradlew :shared:allTests                     # both
./gradlew -I scripts/coverage.init.gradle.kts :shared:koverHtmlReport :shared:koverVerify   # line coverage (opt-in Kover)
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
                            phone-login, auth/login (Google / Apple), sign-up of new accounts
                            (RegistrationRepository: auth/register, auth/update-profile; RegistrationController),
                            AuthFlowConfig, AuthMessage / AuthMessageKey (text stays in the apps)
  profile/                  ProfileRepository (`users/api/v1/muti-profile/list`), SessionProfileChooser:
                            each sign-in uses the account's first profile without a PIN lock (no picker yet)
  home/                     HomeRepository + DefaultHomeRepository (homebanners, home, home/all/{id},
                            follow_studio; read rule D6), HomeMapper (lenient JSON → models; per-app image
                            table HomeImagePolicy D12, price D13, AccessTag D14, live keys D10, bundle colour
                            D11), HomeModels, HomeFeedController (first load, paging, empty-page skip D9,
                            row paging D8, refresh, follow)
  geo/                      GeoStore (IP, geo token, currency, location), GeoRepository (ipify → geo-data,
                            serialized, outcomes for retry decisions; D18: the apps decide when), GeoTokenDecoder
  settings/                 CommonSettings + CommonSettingsRepository (`mediaview/api/v1/common`, update
                            details of this app's platform only)
  validation/               EmailValidator, PhoneValidator, NameValidator
  session/                  SessionManager (restore, sign-in/out, 401 → expired), SessionCodec (reads both apps'
                            old formats), SecureStore / BlockingSecureStore / DispatchingSecureStore / InMemorySecureStore
androidMain/                OkHttp engine (+ createOkHttpEngine(OkHttpClient)), javax.crypto, Logcat
appleMain/                  Darwin engine, CommonCrypto, NSLog, FlowWatcher / StateFlowWatcher (Swift bridge),
                            controller watchState / watchEffects / currentState for Swift (auth, Home)
commonTest/, appleTest/     tests (Ktor MockEngine, kotlinx-coroutines-test); EndpointContractTest runs every
                            endpoint through the same failure scenarios, the D21 rule and a no-secrets log check
androidHostTest/            OkHttpEngineTest: the OkHttp engine against a real local HTTP server
```

## Rules for shared code

- **No UI, no platform singletons.** No Compose, SwiftUI, UIKit, Hilt or Android `Context` in `commonMain`.
- **Public APIs never throw.** Return `DataResult` or a sealed result type. A Kotlin exception that
  reaches Swift crashes the app. Coroutine cancellation is the only thing rethrown.
- **Status codes carry meaning.** HTTP clients use `expectSuccess = false`, and repositories read both
  `VPlayedResponse.httpStatus` and `effectiveStatusCode`. Decision D1: a non-2xx HTTP status always
  fails; within 2xx the envelope's `statusCode` decides when present (`authStatus`).
- **`SessionManager` owns the session.** Each app creates one over its secure storage (AndroidJP
  `KeystoreSecureStore`; VPlayed `KeychainSecureStore` through `DispatchingSecureStore`) and passes
  it to `SharedGraph`, like the `GeoStore`. Repositories return sessions; the app calls
  `SessionManager.signIn` when its flow is done. Every backend request waits for the stored session
  to be read, then sends its tokens and the `GeoStore` geo token. A 401 (HTTP or envelope) on a
  request that carried the token first tries one token refresh (`auth/refresh-token`, one at a
  time) and resends the request once; a refused refresh or a second 401 ends the session
  (decision D21, revised), except on auth endpoints (`RequestOptions.endsSessionOnUnauthorized`).
  `GuestSessionKeeper` keeps a guest session whenever nobody is signed in and the common settings
  allow guests. A new account's session (`PendingRegistration`) is stored only after sign-up
  succeeds; until then its calls send that session's token through `RequestOptions.pendingSession`. `SessionManager.logout(authRepository)` (or
  `SharedGraph.logout()`) ends it after the server agrees; both apps' sign-out entries use it.
- **No secrets or personal data in logs or `toString()`.** Use `RequestOptions.Sensitive` for auth calls.
  `AuthTokens`, `SharedConfig` and `VPlayedResponse` mask their contents.
- **User-facing text stays native.** Shared code returns typed results or message keys. Android maps
  them to `strings.xml`, iOS to `Localizable.xcstrings`.
- **Only synthetic test fixtures.** Real API responses contain personal data.
- **Coverage.** Kover is applied only through `scripts/coverage.init.gradle.kts`, so normal builds
  (and AndroidJP's composite build) never load it. Proposed gate: 90 % lines on the JVM run (96.6 %
  in Phase 6); Apple-only code is covered by VPlayed's tests.

## Module dependencies

```
AndroidJP :app ──(composite build: includeBuild("../kmp"))──► kmp :shared ◄──(Shared.xcframework in SharedKit, built by
   Hilt modules provide SharedGraph,                          scripts/build-shared.sh)── VPlayed (iOS; on Mac and
   SessionManager, GeoStore                                                                         Vision Pro as the iPad app)
```

`:shared` depends only on kotlinx-coroutines, kotlinx-serialization and Ktor (OkHttp engine on
Android, Darwin on Apple). It never depends on an app. Each app supplies its platform pieces (device
facts, secure storage, the HTTP engine factory) and owns when things run (geo refresh triggers,
storing a session after its flow, sign-out). Changes here reach AndroidJP on its next build and
VPlayed after `scripts/build-shared.sh`; keep both apps' tests green before merging.

## Consumers

| App | How it links | What uses the shared layer |
|---|---|---|
| AndroidJP | `includeBuild("../kmp")` with a substitution for `com.vplayed:shared`; Hilt `@Provides @Singleton SharedGraph` (`core/shared/SharedModule`) on the app's `OkHttpClient`, with the app's `SessionManager` (over `KeystoreSecureStore`) and `GeoStore` | Login: `LoginViewModel` / `OtpViewModel` delegate to the controllers on `viewModelScope`. Home: `HomeViewModel` runs `HomeFeedController`, the UI uses the shared models. Geo: `GeoRefreshWorker` calls `GeoRepository`. Session: splash, app shell (side-menu Sign out through `SessionManager.logout`), Login and OTP use `SessionManager`. No other network stack is left |
| VPlayed | `scripts/build-shared.sh` builds `Shared.xcframework` into the local `SharedKit` package (`binaryTarget`); `@preconcurrency import Shared` in adapter files; `SharedLayer.sessions` over `KeychainSecureStore` | Login: `SharedLoginViewModel` on `SharedGraph.emailLoginController` / `otpController` (own main-thread scopes, closed with the screen); logout: `SharedAuthService` (`SharedGraph.logout`); session: `AccountStore` over `SessionManager`. Home: `SharedHomeViewModel` on `homeFeedController()`, mapped to the Swift view structs by `SharedHomeMapping`. Movies: `SharedMoviesService` on `moviesRepository`. Launch: `SharedStartupService` (settings, geo). No other network stack is left |

Swift adapters map sealed results with `as?` casts (`EmailLoginResultCodeSent`, …), read a
controller's state with `currentState` (a `StateFlow` value is untyped in Swift), observe it with
`watchState` / `watchEffects` and close the returned `Closeable`s and the controller together.
Avoid Kotlin names Objective-C reserves (an enum entry `NEW_…` becomes `theNew…`; a property
`description` becomes `description_`) and nested types whose flattened Swift name collides with
another type (`AuthMessage.Key` vs `AuthMessageKey`). Name enum entries in UPPER_SNAKE_CASE: Swift
gets `transientFailure` from `TRANSIENT_FAILURE`, but `transientfailure` from `TransientFailure`.
Kotlin types that share a name with a VPlayed type (`HomeSection`, `ContentItem`, `CommonSettings`,
`GeoInfo`, `Price`, `AccessTag`) are written `Shared.X` in Swift; app code sees its own type unqualified.
