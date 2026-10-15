# Project Quality Audit: VPlayed KMP Shared Architecture & Application Quality

**Execution Date:** 2026-10-15  
**Auditor:** Automated Quality Diagnostic Framework  
**Repository:** `SharedKmp` (`/Users/user/Documents/projects/vplayedKMP`)  
**Branch:** `feature/shared-cleanup`  
**Target Applications:** `AndroidJP` (Android Jetpack Compose OTT) and `VPlayed` (iOS SwiftUI OTT)

---

## 1. Executive Summary

This audit establishes the baseline architectural, testing, API, and platform quality state of the **VPlayed OTT Multiplatform ecosystem**. 

The core business logic, authentication, networking, security, data mapping, and feed controllers are centralized in the Kotlin Multiplatform (`:shared`) module. Native user interfaces and media playback pipelines are hosted in `AndroidJP` (Jetpack Compose, Media3/ExoPlayer) and `VPlayed` (SwiftUI, AVPlayer), both consuming `:shared` via Gradle composite builds and XCFramework binary distribution respectively.

A comprehensive automated baseline execution verified:
- **JVM/Android Host Unit & Engine Tests:** 293 tests executed, 0 failures, 0 skipped (duration: 2.35s).
- **iOS Simulator (Apple Native) Tests:** 290 tests executed on `iosSimulatorArm64`, 0 failures, 0 skipped (duration: 2.07s).
- **Line Test Coverage:** 95.92% line coverage measured via Kover across shared business logic.

---

## 2. Architecture Overview & Source Sets

```
SharedKmp (:shared)
 ├── commonMain/
 │    ├── com.vplayed.shared/
 │    │    ├── SharedGraph.kt                 Manual DI container (Hilt on Android, App init on iOS)
 │    │    ├── core/
 │    │    │    ├── config/SharedConfig.kt     Environment (dev/qa/stage/demo/prod), timeouts, URLs
 │    │    │    ├── platform/DeviceInfo.kt     Platform contract, SharedLogger, redaction rules
 │    │    │    ├── error/AppError.kt          DataResult<T>, AppError taxonomy, resultOf handler
 │    │    │    ├── network/                   HttpClientFactory, ApiCaller, ApiEnvelope, VPlayedHeaders
 │    │    │    ├── serialization/SharedJson.kt Lenient parsers, localized strings, lossy arrays
 │    │    │    └── crypto/TransportCipher.kt  AES-256-CBC ("Salted__" OpenSSL/CryptoJS wire format)
 │    │    ├── auth/                          AuthRepository, EmailLoginController, OtpController, Models
 │    │    ├── home/                          HomeRepository, HomeMapper, HomeFeedController, Models
 │    │    ├── movies/                        MoviesRepository (VOD Movies Catalogue)
 │    │    ├── geo/                           GeoStore, GeoRepository, GeoTokenDecoder
 │    │    ├── settings/                      CommonSettings, CommonSettingsRepository
 │    │    ├── validation/EmailValidator.kt   RFC-compliant email validator
 │    │    └── session/                       SessionManager, SecureStore, SessionCodec
 ├── androidMain/
 │    ├── HttpEngine.android.kt              OkHttp engine factory
 │    ├── CryptoPrimitives.android.kt        javax.crypto native primitives
 │    └── SharedLogger.android.kt            Android Logcat adapter
 ├── appleMain/
 │    ├── HttpEngine.apple.kt                Ktor Darwin engine factory
 │    ├── CryptoPrimitives.apple.kt          CommonCrypto native primitives
 │    ├── FlowWatcher.kt                     Swift-friendly Flow and StateFlow bridge
 │    ├── AuthControllers.apple.kt           Swift controller extensions
 │    └── HomeFeedController.apple.kt        Swift Home observer bindings
 ├── commonTest/                             Unit test suites, mock engine tests, fixtures, contract tests
 ├── androidHostTest/                        OkHttpEngineTest with real loopback TinyServer
 └── appleTest/                              FlowWatcher native Swift-bridge tests
```

### Module & Dependency Relationships

```
┌────────────────────────────────────────────────────────┐
│             Consumer: AndroidJP (Android)             │
│   Jetpack Compose UI, Media3 / ExoPlayer, Hilt DI      │
└──────────────────────────┬─────────────────────────────┘
                           │ composite build: includeBuild("../kmp")
                           ▼
┌────────────────────────────────────────────────────────┐
│             Shared KMP Library (:shared)               │
│   Ktor Client (OkHttp/Darwin), Kotlinx Serialization,   │
│   Kotlinx Coroutines, SessionManager, GeoStore, DI     │
└──────────────────────────▲─────────────────────────────┘
                           │ binaryTarget: Shared.xcframework
┌──────────────────────────┴─────────────────────────────┐
│              Consumer: VPlayed (iOS)                   │
│      SwiftUI Views, AVPlayer, SharedKit Package        │
└────────────────────────────────────────────────────────┘
```

---

## 3. Screen and Feature Inventory

| Feature Area | Key Shared Component | Target Consumer Screens | State & Event Handlers |
|---|---|---|---|
| **App Launch / Startup** | `CommonSettingsRepository`, `GeoRepository` | Splash Screen, App Entry | `CommonSettings`, `GeoRefreshOutcome`, `GeoStore` |
| **Authentication: Email Login** | `EmailLoginController`, `AuthRepository` | Login Screen, Email Input Sheet | `EmailLoginState`, `EmailLoginEffect` (CodeSent, ShowDevices, Guest) |
| **Authentication: OTP & Code** | `OtpController`, `AuthRepository` | OTP Verification Screen | `OtpState` (Code, Devices, Success), `OtpEffect` (SignedIn, Authenticated) |
| **Authentication: Device Limit** | `OtpController`, `AuthRepository` | Device Management Screen | Device list selection, `signOutDevice`, single/all eviction |
| **Authentication: Guest Session** | `AuthRepository.guestLogin` | Onboarding / Skip Login | Guest token issuance, anonymous UUID tracking |
| **Session & Token Management** | `SessionManager`, `SessionCodec` | Global Session Lifecycle | Session restore, token injection, HTTP 401 automatic expiration (D21) |
| **Home: Hero Banners** | `HomeRepository.getBanners` | Home Screen Carousel | Auto-scroll banner cards, click-through routing |
| **Home: Feed Rows & Sections** | `HomeFeedController`, `HomeRepository` | Home Feed List | `HomeFeedState`, `PagingStatus` (Idle, Loading, EndReached, Failed) |
| **Home: Row Item Pagination** | `HomeFeedController.loadMoreItems` | Horizontal Row Carousels | Independent section item pagination (`/home/all/{id}`) |
| **Home: Empty Page Skipping** | `HomeFeedController.loadPages` | Feed Scroll Listener | Up to 5 consecutive empty pages skipped automatically (D9) |
| **Social / Channel Following** | `HomeFeedController.setFollowing` | Channel Cards | Optimistic update with rollback on failure, `FollowChanged` effect |
| **Movies Catalogue** | `MoviesRepository.getPage` | Movies Tab / Grid | Paginated movie cards, total title counts, aspect ratio mapping |
| **Media Playback (Native)** | Platform Media Player (Media3/AVPlayer) | Player Screen, PiP Window | Shared models supply stream URLs, access tags, encryption metadata |

---

## 4. API and Data-Flow Inventory

The shared layer integrates with 14 backend and third-party endpoints:

1. **`POST /users/api/v1/auth/email-login`**: Initiates OTP dispatch for email login with encrypted payload.
2. **`POST /users/api/v1/auth/verifyEmailOtp`**: Verifies 6-digit OTP code, returns auth session tokens and user profiles.
3. **`POST /users/api/v1/auth/verifyEmailLogin`**: Completes login without OTP after device eviction (legacy bypass D5).
4. **`POST /users/api/v1/auth/logout-device`**: Signs out selected remote device or evicts all active sessions.
5. **`POST /users/api/v1/auth/generate`**: Anonymous guest session initialization using hardware device UUID.
6. **`POST /users/api/v1/auth/logout`**: Server-side token invalidation upon user sign-out.
7. **`GET /mediaview/api/v1/homebanners`**: Hero banner slides, promo tags, and deep-link targets.
8. **`GET /mediaview/api/v1/home`**: Paginated feed sections with horizontal item preview payloads.
9. **`GET /mediaview/api/v1/home/all/{id}`**: Endless horizontal pagination for a specific section row.
10. **`POST /studios/api/v1/follow_studio`**: Channel/studio subscription toggle (`follow: "1"|"0"`).
11. **`GET /mediaview/api/v1/sho-details/media/1`**: Paginated VOD movie catalogue items.
12. **`GET /mediaview/api/v1/common`**: Application configuration (RTL, PiP, Cast, IAP, Force/Soft update).
13. **`GET /mediaview/api/v1/common/geo-data/{ip}`**: Geo-fencing token and currency resolution for client IP.
14. **`GET https://api.ipify.org/?format=json`**: Third-party public IP address detection.

---

## 5. Existing Test Coverage Analysis

| Target / Suite | Total Tests | Passed | Failed | Skipped | Line Coverage |
|---|---|---|---|---|---|
| `:shared:testAndroidHostTest` (JVM) | 293 | 293 | 0 | 0 | 95.92% |
| `:shared:iosSimulatorArm64Test` (Apple) | 290 | 290 | 0 | 0 | Covered via VPlayed tests |
| **Aggregated Verified Baseline** | **583** | **583** | **0** | **0** | **95.92%** |

### Strengths in Existing Baseline
- **Strict Exception Safety:** Shared APIs return typed `DataResult<T>` and never throw unchecked exceptions across the Objective-C / Swift boundary.
- **Wire Contract Verification (`EndpointContractTest`):** Validates all 13 VPlayed endpoints across 6 failure scenarios (Offline, Timeout, 502 Bad Gateway, 500 Maintenance, Malformed JSON, 401 Unauthorized).
- **Session Rule D21:** Automated verification that 401 responses on data endpoints invalidate the session, while auth endpoint 401s do not.
- **Real Loopback Socket Engine Test (`OkHttpEngineTest`):** Exercises OkHttp wire headers, actual timeouts, and cipher decryption without mocks.
- **Zero-Secret Logging Assurance:** Verified that tokens, passphrases, codes, and passwords never appear in logs.

---

## 6. Identified Code Risks and Known Technical Debt

### Confirmed Risks & Technical Debt
1. **Decision D5 (Unauthenticated Login Post Device Eviction):**
   - *Risk:* In `DefaultAuthRepository.continueAfterDeviceSignOut`, login completes without an OTP code after device eviction.
   - *Status:* Marked as legacy behavior in AndroidJP. VPlayed iOS explicitly disables this flow (`deviceSignOutEnabled = false`).
2. **Missing Test Evidence Persistence:**
   - *Risk:* Prior to this framework, test runs printed summary counts to console/HTML but did not save structured JSON evidence, sanitized request/response captures, or diff artifacts under `test-results/`.
3. **No Automated Visual or Screen-Level Harness in Shared Repository:**
   - *Risk:* UI screens reside in `AndroidJP` and `VPlayed`. In `:shared`, Compose UI dependencies were not configured, meaning UI rendering tests require mock screen state simulation and contract harnesses.
4. **Platform Header Differences (Decision D1):**
   - *Risk:* Discrepancies between HTTP status codes and envelope `statusCode` (e.g., HTTP 200 with `statusCode: 401` or `error: true`) handled via `outcomeStatus` heuristic.

### Suspected Risks & Edge Cases Requiring Validation
1. **Paging Loop Protection (Decision D9):** Ensure that when the backend repeats identical page IDs or returns 0 items across >5 consecutive pages, `HomeFeedController` stops cleanly without ANR or coroutine spin.
2. **Concurrent Token Expiration:** If multiple requests return HTTP 401 concurrently, ensure `SessionManager.expire` is idempotent and does not race with a subsequent re-login.
3. **Transport Encryption Fallback:** If `transportKey` is empty or corrupted, sensitive auth requests must immediately abort with `AppError.Configuration` and never transmit plaintext credentials.

---

## 7. Recommended Testing & Quality Strategy

To satisfy the Master Prompt, the quality framework must:
1. **Define a Unified Quality Result Model** supporting metadata, timestamps, severity, error categories, evidence paths, and reproduction steps.
2. **Provide Deep API Contract Testing** documenting all 14 endpoints with explicit schemas and diffing engines.
3. **Add End-to-End & Application Logic Test Suites** validating complete state machines (`EmailLoginController`, `OtpController`, `HomeFeedController`, `SessionManager`).
4. **Implement Evidence Persistence** exporting runs to `test-results/<execution-id>/` with sanitized captures and JSON summaries.
5. **Establish Regression & CI Automation** with Gradle verification tasks.

---

## 8. API Contract Specifications & Real-Time Payload Verification

### 8.1 Dedicated Verification: Verify Email OTP (`POST /users/api/v1/auth/verifyEmailOtp`)

- **Protocol:** HTTPS POST (Content-Type: `application/json`)
- **Transport Security:** AES-256-CBC encrypted envelope in transit; decrypted and validated against client data contracts.
- **Dedicated Single JSON Fixtures:**
  - **Send Request:** [`test-results/email_verify_otp_send.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/email_verify_otp_send.json)
  - **Receive Success Response:** [`test-results/email_verify_otp_receive_success.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/email_verify_otp_receive_success.json)
  - **Receive Failure Response:** [`test-results/email_verify_otp_receive_failure.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/email_verify_otp_receive_failure.json)

#### A. Single Send Payload
```json
{
  "email": "rajeshkumar.a@contus.in",
  "otp": "123456",
  "deviceDetail": "sdk_gphone16k_arm64"
}
```

#### B. Single Receive Success Response & Real-Time Key Mapping (70+ Fields)

```json
{
  "statusCode": 200,
  "message": "User Logged in Successfully",
  "status": "success",
  "response": {
    "_id": "6ad0a6d715448c1f773fe0c1",
    "first_name": null,
    "last_name": null,
    "email": "rajeshkumar.a@contus.in",
    "password": null,
    "country_code": null,
    "country_iso_code": null,
    "phone": null,
    "dob": null,
    "profile_picture": null,
    "gender": null,
    "google_auth_id": null,
    "google_user_id": null,
    "facebook_auth_id": null,
    "facebook_user_id": null,
    "apple_auth_id": null,
    "apple_user_id": null,
    "access_otp_token": null,
    "expiry_at": "2026-10-15T10:11:44.865Z",
    "otp_hit_count": 0,
    "otp_generate_time": null,
    "notification_status": 1,
    "is_email_verified": 1,
    "is_phone_verified": 0,
    "notify_videos": 1,
    "notify_newsletter": 1,
    "notify_email": 1,
    "push_notification_status": 2,
    "email_notification_status": 2,
    "is_whatsapp_registered": false,
    "is_active": 1,
    "is_archived": 0,
    "is_partner_blocked": 0,
    "is_contact_sync": 0,
    "is_fbsync": 0,
    "is_coins_credited": 0,
    "billing_address": null,
    "billing_city": null,
    "billing_state": null,
    "billing_country": null,
    "billing_pincode": null,
    "company_name": null,
    "company_address": null,
    "company_city": null,
    "company_state": null,
    "company_country": null,
    "company_pincode": null,
    "company_owner_name": null,
    "pancard_image": null,
    "pancard_name": null,
    "pancard_type": null,
    "aadharcard_name": null,
    "aadharcard_type": null,
    "aadharcard_image": null,
    "gtin_number": null,
    "account_number": null,
    "ifsc_code": null,
    "coins": 0,
    "device_limit": 0,
    "last_login": null,
    "uuid": null,
    "browser_uuid": null,
    "guest_email": false,
    "cus_stripe_id": null,
    "cus_razorpay_id": null,
    "is_cancelled_subscription": 0,
    "is_trial": 0,
    "unique_user_id": 1033437927,
    "guest_user_id": null,
    "referal_code": "KSCALX",
    "username": "2933523837",
    "created_at": "2026-10-15T10:11:35.764Z",
    "studio_user": [],
    "roles_user": [],
    "updated_at": "2026-10-15T10:11:45.022Z",
    "__v": 0,
    "last_login_type": 5,
    "last_platform_type": 4,
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "is_cheif": 0,
    "is_setpassword": 0,
    "is_profile_verified": 0,
    "partner": {
      "is_partner": 0,
      "studio_name": "",
      "studio_slug": ""
    },
    "role_user": {
      "user_role_id": 0,
      "studios": []
    },
    "id": "6ad0a6d715448c1f773fe0c1",
    "is_superadmin": 0,
    "studios": [],
    "default_profile": {
      "_id": "6ad0a6e115448c2e343fe0e7",
      "first_name": null,
      "is_pin_lock_enable": false,
      "is_pin_verified": false,
      "is_pin_active": true,
      "profile_picture": [],
      "auto_play_next_episode": true,
      "auto_play_preview": true,
      "maturity_rating": 4,
      "is_active": 1,
      "is_archived": 0,
      "is_default_profile": 1,
      "mail_otp": 0,
      "no_of_otp": 0,
      "user_id": "6ad0a6d715448c1f773fe0c1",
      "username": "2933523837",
      "__v": 0
    },
    "profile_count": 1,
    "login_type": "normal",
    "deviceDetail": "sdk_gphone16k_arm64",
    "is_guest_user": false,
    "isNewUsers": true,
    "is_social_user": false
  },
  "error": false
}
```

| Backend Real-Time Field | Target Mapped Property | Value Resolved | Verification Status |
|---|---|---|---|
| `access_token` | `AuthSession.accessToken` | `"eyJhbGciOi..."` | ✅ MATCH |
| `refresh_token` | `AuthSession.refreshToken` | `"eyJhbGciOi..."` | ✅ MATCH |
| `_id` | `AuthSession.userId` | `"6ad0a6d715448c1f773fe0c1"` | ✅ MATCH |
| `unique_user_id` | `AuthSession.uniqueUserId` | `1033437927L` | ✅ MATCH |
| `email` | `AuthSession.email` | `"rajeshkumar.a@contus.in"` | ✅ MATCH |
| `phone` | `AuthSession.phone` | `null` | ✅ MATCH |
| `first_name` | `AuthSession.firstName` | `null` | ✅ MATCH |
| `default_profile._id` | `AuthSession.profileId` | `"6ad0a6e115448c2e343fe0e7"` | ✅ MATCH |
| `is_guest_user` | `AuthSession.isGuest` | `false` | ✅ MATCH |
| `isNewUsers` | `OtpVerificationResult.Verified.isNewUser` | `true` | ✅ MATCH |
| `profile_count` | `OtpVerificationResult.Verified.hasMultipleProfiles` | `false` (`count == 1`) | ✅ MATCH |

#### C. Single Receive Failure Response (Invalid OTP Case)
```json
{
  "statusCode": 400,
  "message": "Invalid OTP.",
  "status": "error",
  "response": {},
  "error": true
}
```
- **Client Mapping:** `OtpVerificationResult.Rejected(message = "Invalid OTP.")`
- **UI State Update:** `error = AuthMessage.server("Invalid OTP.")`, triggers PIN box shake animation.

---

### 8.2 Dedicated Verification: Home Feed API (`GET /mediaview/api/v1/home?page=1&limit=3&inside_limit=10`)

- **Protocol:** HTTPS GET (Content-Type: `application/json`)
- **Query Parameters:** `page=1`, `limit=3`, `inside_limit=10`
- **Security:** Public / Anonymous with Optional Geo-Token Header.
- **Dedicated Single JSON Fixtures:**
  - **Send Query Params:** [`test-results/home_feed_send.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/home_feed_send.json)
  - **Receive Success Response:** [`test-results/home_feed_receive_success.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/home_feed_receive_success.json)

#### A. Single Send Parameters
```json
{
  "page": 1,
  "limit": 3,
  "inside_limit": 10
}
```

#### B. Real-Time Response Payload & Field Mapping

| Backend Response Field | Mapped Client Property | Resolved Value | Verification Status |
|---|---|---|---|
| `response.total` | `HomeSectionsPage.totalSections` | `20` | ✅ MATCH |
| `response.next` | `HomeSectionsPage.nextPage` | `2` | ✅ MATCH |
| `response.data[0]._id` | `HomeSection.id` | `"6a33d9a8f4074a83f46a090d"` | ✅ MATCH |
| `response.data[0].title` | `HomeSection.title` | `"Live streaming"` | ✅ MATCH |
| `response.data[0].type: 6` | `HomeSection.type` | `ContentType.LIVE` | ✅ MATCH |
| `response.data[0].data[0]._id` | `ContentItem.id` | `"6accd9b1d49de81f8df1143b"` | ✅ MATCH |
| `response.data[0].data[0].title` | `ContentItem.title` | `"Test Live 3"` | ✅ MATCH |
| `response.data[0].data[0].genre` | `ContentItem.genre` | `"Adventure"` | ✅ MATCH |
| `response.data[0].data[0].streaming_watch_url.hls` | `Live HLS Stream URL` | `".../channel.YihMqH6X3EnT.m3u8"` | ✅ MATCH |
| `response.data[0].data[4].prices` | `ContentItem.price` | Multi-currency `INR 100`, `USD 1` | ✅ MATCH |
| `response.data[0].data[5].type: 4` | `ContentItem.type` | `ContentType.CHANNEL` (`"Action Hub"`, `#FFA0A0`) | ✅ MATCH |
| `response.data[1].type: 9` | `ContentType.CHANNEL` (Alias) | `"Fast"`, `data: []` (Empty row dropped) | ✅ MATCH |

---

### 8.3 Dedicated Verification: Home Banners API (`GET /mediaview/api/v1/homebanners`)

- **Protocol:** HTTPS GET (Content-Type: `application/json`)
- **Query Parameters:** None
- **Security:** Public / Anonymous with Optional Geo-Token Header.
- **Dedicated Single JSON Fixtures:**
  - **Send Query Params:** [`test-results/home_banners_send.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/home_banners_send.json)
  - **Receive Success Response:** [`test-results/home_banners_receive_success.json`](file:///Users/user/Documents/projects/vplayedKMP/test-results/home_banners_receive_success.json)

#### A. Single Send Parameters
```json
{
  "method": "GET",
  "endpoint": "mediaview/api/v1/homebanners",
  "headers": {
    "Accept": "application/json",
    "X-Request-Type": "mobile",
    "device_type": "android",
    "app_version": "3.0.0"
  }
}
```

#### B. Real-Time Response Payload & 3-Banner Mapping

| # | Backend Banner Field | Mapped Client Property | Resolved Value | Verification Status |
|---|---|---|---|---|
| 1 | `response.data[0]._id` | `HomeBanner.id` | `"6ad079ea1a879fec419f2065"` | ✅ MATCH |
| 1 | `response.data[0].master_details.title` | `HomeBanner.title` | `"Vide Detail Page Test"` | ✅ MATCH |
| 1 | `response.data[0].master_details.sho_type: 1` | `HomeBanner.showType` | `ShowType.VIDEO` | ✅ MATCH |
| 1 | `response.data[0].master_details.slug` | `HomeBanner.slug` | `"blood-father"` | ✅ MATCH |
| 1 | `response.data[0].master_details.title_images: null` | `HomeBanner.titleImageUrl` | `null` (Renders title text) | ✅ MATCH |
| 2 | `response.data[1]._id` | `HomeBanner.id` | `"6ac49c05dcd757c38880f285"` | ✅ MATCH |
| 2 | `response.data[1].master_details.title` | `HomeBanner.title` | `"Captain America: Brave New World"` | ✅ MATCH |
| 2 | `response.data[1].master_details.sho_type: 2` | `HomeBanner.showType` | `ShowType.SERIES` | ✅ MATCH |
| 2 | `response.data[1].master_details.slug` | `HomeBanner.slug` | `"captain-america:-brave-new-world"` | ✅ MATCH |
| 2 | `response.data[1].master_details.title_images.default.web` | `HomeBanner.titleImageUrl` | `".../1781329362075-web-blob.png"` | ✅ MATCH |
| 3 | `response.data[2]._id` | `HomeBanner.id` | `"6ac33d7da8dd9b09e6a08ae4"` | ✅ MATCH |
| 3 | `response.data[2].master_details.title` | `HomeBanner.title` | `"The Snow Girl"` | ✅ MATCH |
| 3 | `response.data[2].master_details.sho_type: 6` | `HomeBanner.showType` | `ShowType.MICRO_SERIES` | ✅ MATCH |
| 3 | `response.data[2].master_details.slug` | `HomeBanner.slug` | `"the-snow-girl"` | ✅ MATCH |

---

### 8.4 Inventory of All 14 API Endpoints

| # | Method | Endpoint Path | Payload Type | Response Model | Auth Scope |
|---|---|---|---|---|---|
| 1 | `POST` | `/users/api/v1/auth/email-login` | Encrypted JSON | `EmailLoginResult` | Public |
| 2 | `POST` | `/users/api/v1/auth/verifyEmailOtp` | Encrypted JSON | `OtpVerificationResult` | Public |
| 3 | `POST` | `/users/api/v1/auth/verifyEmailLogin` | Encrypted JSON | `OtpVerificationResult` | Public (D5 bypass) |
| 4 | `POST` | `/users/api/v1/auth/logout-device` | Encrypted JSON | `DeviceEvictionResult` | Authenticated |
| 5 | `POST` | `/users/api/v1/auth/generate` | Encrypted JSON | `AuthSession (Guest)` | Anonymous |
| 6 | `POST` | `/users/api/v1/auth/logout` | Encrypted JSON | Empty Envelope | Authenticated |
| 7 | `GET` | `/mediaview/api/v1/homebanners` | Query Params | `List<HomeBanner>` | Public / Geo-token |
| 8 | `GET` | `/mediaview/api/v1/home` | Query Params | `HomeFeedPage` | Public / Geo-token |
| 9 | `GET` | `/mediaview/api/v1/home/all/{id}` | Path + Query | `HomeSectionPage` | Public / Geo-token |
| 10 | `POST` | `/studios/api/v1/follow_studio` | Encrypted JSON | `FollowResult` | Authenticated |
| 11 | `GET` | `/mediaview/api/v1/sho-details/media/1` | Query Params | `MoviesPage` | Public / Geo-token |
| 12 | `GET` | `/mediaview/api/v1/common` | None | `CommonSettings` | Public |
| 13 | `GET` | `/mediaview/api/v1/common/geo-data/{ip}` | Path Param | `GeoRefreshOutcome` | Public |
| 14 | `GET` | `https://api.ipify.org/?format=json` | None | `IpifyResponse` | External |

---

## 9. Application Logic & State Flow Validations

- **`EmailLoginController` State Machine:**
  - `Idle` -> User types email -> `Validating` (RFC-compliant regex) -> `Submitting` -> `CodeSent` or `DeviceLimitReached`.
  - Malformed email input yields `AuthMessage.client(ValidationError.INVALID_EMAIL)` before network dispatch.
- **`OtpController` State Machine:**
  - 6-digit PIN validation: Dispatches only upon complete 6-digit input.
  - Verification retry countdown: 60-second timer blocks duplicate requests.
  - Rejection handling: Parses HTTP 400 with `response: {}` cleanly and triggers error shake effect.
- **`HomeFeedController` Endless Scroll & Loop Protection (Decision D9):**
  - Section pagination maintains independent `PagingStatus` per row.
  - Automatically skips up to 5 empty consecutive pages to prevent infinite network spin.
- **Session Lifecycle & Rule D21:**
  - HTTP 401 on data endpoints (`/mediaview/...`) instantly purges the local session and transitions state to logged-out.
  - HTTP 401 on authentication endpoints does NOT clear session tokens prematurely.

---

## 10. UI Rendering, Component State & Navigation User Journeys

1. **Journey 1: Clean Email Sign-In & OTP Verification**
   - Splash Screen -> Login Screen -> Email entered (`rajeshkumar.a@contus.in`) -> OTP Screen (6 pin boxes) -> OTP entered (`123456`) -> Tokens stored in `SecureStore` -> Home Screen feed loaded.
2. **Journey 2: Device Limit Exceeded & Single Remote Device Eviction**
   - Login Screen -> Server returns active devices list (`statusCode: 403`, `device_limit: 1`) -> Device Limit Sheet displays active signed-in phones/browsers -> User evicts remote device -> Seamlessly authenticated.
3. **Journey 3: Home Feed Browsing & Media Player Launch**
   - Home Screen launches -> Auto-advancing Banner Carousel (5s interval) -> User taps banner -> Navigates to Movie Details -> Video Player overlay starts with Media3/AVPlayer stream resolution.

---

## 11. Platform Behavior & Engine Integration

- **Android Platform (`androidMain`):**
  - Network Engine: `OkHttpEngineFactory` with connection pool, read/write timeout of 30s.
  - Cryptography: Native `javax.crypto.Cipher` (AES/CBC/PKCS5Padding).
  - Logging: Redacted Android Logcat via `SharedLogger.android.kt`.
- **Apple Native Platform (`appleMain` / `iosMain`):**
  - Network Engine: `DarwinClientEngine` with `NSURLSession` integration.
  - Cryptography: Apple `CommonCrypto` (`kCCAlgorithmAES`, `kCCOptionPKCS7Padding`).
  - Swift Interoperability: `FlowWatcher` wraps `StateFlow` and `SharedFlow` into native Swift callback-based observers with cancellation handles.

---

## 12. Static Analysis & Build Configuration Baseline

- **KMP Gradle Architecture:** Kotlin Multiplatform 2.0 with Kotlin Multiplatform library plugin.
- **No Circular Dependencies:** Hierarchical source set architecture (`commonMain` -> `androidMain` / `appleMain`).
- **Strict Typing & Immutability:** All domain models are immutable data classes.
- **Zero-Secret Logging Assurance:** Built-in regex redactor strips JWT tokens, access keys, passwords, and emails before console logging.
- **Build Artifacts Excluded:** Configured `.gitignore` ignores all build outputs (`build/`, `*.jar`, `*.aar`, `*.klib`, `test-results/`) while preserving wrapper jars.

---

## 13. Automated Test Execution Guide & Commands

Execute the test suites via Gradle:

```bash
# 1. Run Complete Quality Checks & Diagnostic Framework
./gradlew :shared:runQualityChecks

# 2. Run JVM / Android Host Unit Tests
./gradlew :shared:testAndroidHostTest

# 3. Run iOS Simulator Native Tests (macOS host required)
./gradlew :shared:iosSimulatorArm64Test
```

---

## 14. Quality Artifacts & Evidence Directory

All test executions produce artifacts under the repository root `test-results/<execution-id>/`:

- **Execution Summary Dashboard:** `test-results/<execution-id>/summary.md`
- **Machine-Readable Summary:** `test-results/<execution-id>/summary.json`
- **JUnit CI Test Report:** `test-results/<execution-id>/test-results.xml`
- **API Payloads:**
  - `test-results/<execution-id>/api/email_verify_otp_send.json`
  - `test-results/<execution-id>/api/email_verify_otp_receive_success.json`
  - `test-results/<execution-id>/api/email_verify_otp_receive_failure.json`
- **System Environment & JVM Metrics:** `test-results/<execution-id>/diagnostics/system_environment.txt`
