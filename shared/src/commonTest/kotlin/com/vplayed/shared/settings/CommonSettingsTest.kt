package com.vplayed.shared.settings

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.respondJson
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.vplayed.shared.session.testSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Ported from VPlayed's `AppStartupTests` (settings decoding). */
class CommonSettingsTest {

    private fun settings(json: String, platform: ClientPlatform = ClientPlatform.IOS) =
        CommonSettings.from(Json.parseToJsonElement(json).jsonObject, platform)

    @Test
    fun givenFullResponse_whenReading_thenEveryFlagIsRead() {
        val result = settings(
            """{"rtl":false,"pic_in_pic":true,"casting":"1","screen_rec":0,"is_guest_user":"true","content_library":2,
               "otp_verfied":"phone","is_iap_enabled":1,"is_iosappstore_cta_change":true,
               "force_update_details":{"platform":"IOS","force_update":true,"minimum_version":"3.1.0","force_update_message":"Update",
               "soft_update":"0","latest_version":"3.2.0","soft_update_message":"New"}}""",
        )
        assertEquals(
            CommonSettings(
                isRtlAllowed = false,
                isPipAllowed = true,
                isCastingAllowed = true,
                isScreenRecordingAllowed = false,
                isGuestUser = true,
                contentLibrary = ContentLibrary.DRAMA,
                verificationMethod = VerificationMethod.PHONE,
                isIapEnabled = true,
                isAppStoreCtaChange = true,
                update = UpdateDetails(true, "3.1.0", "Update", false, "3.2.0", "New"),
            ),
            result,
        )
    }

    @Test
    fun givenMissingOrMalformedKeys_whenReading_thenUIKitDefaultsApply() {
        val result = settings("""{"rtl":"maybe","content_library":"9","otp_verfied":42,"force_update_details":"none"}""")
        assertEquals(CommonSettings(), result)
        assertNull(result.isRtlAllowed, "Only an explicit false turns RTL off")
    }

    @Test
    fun givenUpdateDetailsForAnotherPlatform_whenReading_thenTheyAreIgnored() {
        val json = """{"force_update_details":{"platform":"android","force_update":true}}"""
        assertNull(settings(json, ClientPlatform.IOS).update)
        assertEquals(true, settings(json, ClientPlatform.ANDROID).update?.isForceUpdateEnabled)
    }

    @Test
    fun givenRepository_whenLoading_thenRequestsCommonAndAppliesTheReadRule() = runTest {
        val answers = ArrayDeque(
            listOf(
                """{"statusCode":200,"response":{"content_library":1}}""" to HttpStatusCode.OK,
                """{"statusCode":500,"message":"Down"}""" to HttpStatusCode.OK,
            ),
        )
        val recorder = RecordingEngine { answers.removeFirst().let { (body, status) -> respondJson(body, status) } }
        val repository = SharedGraph(
            testConfig(),
            PlatformDependencies(FakeDeviceInfo(), NoOpLogger, { recorder.engine }, platformCryptoPrimitives()),
            testSessions(),
        ).commonSettingsRepository

        assertEquals(ContentLibrary.VOD, assertIs<DataResult.Success<CommonSettings>>(repository.load()).data.contentLibrary)
        assertEquals("/mediaview/api/v1/common", recorder.last.url.encodedPath)
        assertEquals(DataResult.Failure(AppError.Server(500, "Down")), repository.load())
    }
}
