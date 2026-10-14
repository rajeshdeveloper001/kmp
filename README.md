# SharedKmp — VPlayed shared business logic

Kotlin Multiplatform library (`:shared`) holding the business logic both VPlayed apps use:
networking, request encryption, models, validation, and later auth, session and Home. **No UI
lives here.** Screens stay native: Jetpack Compose in `AndroidJP`, SwiftUI in `VPlayed`.

The plan and its decisions are in `../KMP_PLANNING/` (architecture, API mapping, storage, migration
plan, backlog). This README covers Phase 2 (foundation).

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
  validation/               EmailValidator
  session/                  SecureStore interface (implemented per platform in Phase 5)
androidMain/                OkHttp engine (+ createOkHttpEngine(OkHttpClient)), javax.crypto, Logcat
appleMain/                  Darwin engine, CommonCrypto, NSLog, FlowWatcher / StateFlowWatcher (Swift bridge)
commonTest/, appleTest/     tests (Ktor MockEngine, kotlinx-coroutines-test)
```

## Rules for shared code

- **No UI, no platform singletons.** No Compose, SwiftUI, UIKit, Hilt or Android `Context` in `commonMain`.
- **Public APIs never throw.** Return `DataResult` or a sealed result type. A Kotlin exception that
  reaches Swift crashes the app. Coroutine cancellation is the only thing rethrown.
- **Status codes carry meaning.** HTTP clients use `expectSuccess = false`, and repositories read both
  `VPlayedResponse.httpStatus` and `effectiveStatusCode`.
- **No secrets or personal data in logs or `toString()`.** Use `RequestOptions.Sensitive` for auth calls.
  `AuthTokens`, `SharedConfig` and `VPlayedResponse` mask their contents.
- **User-facing text stays native.** Shared code returns typed results or message keys. Android maps
  them to `strings.xml`, iOS to `Localizable.xcstrings`.
- **Only synthetic test fixtures.** Real API responses contain personal data.

## Consuming from the apps (Phase 2 remainder, not done yet)

- **AndroidJP:** `includeBuild("../kmp")` in `settings.gradle.kts` with a substitution for
  `com.vplayed:shared`, plus a Hilt `@Provides @Singleton SharedGraph` built from `AppConfig`.
- **VPlayed:** a local Swift package wrapping `Shared.xcframework` as a `binaryTarget`, and
  `@preconcurrency import Shared` in adapter files. The adapters are `@Observable` classes that use
  `StateFlowWatcher.watch(onEach:)` and close the returned `Closeable` in `deinit`.
