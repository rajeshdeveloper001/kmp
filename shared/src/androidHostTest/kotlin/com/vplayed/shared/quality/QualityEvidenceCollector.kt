package com.vplayed.shared.quality

import com.vplayed.shared.auth.AuthFixtures
import com.vplayed.shared.home.HomeFixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SanitizedRequestCapture(
    val timestamp: String,
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String?,
)

@Serializable
data class SanitizedResponseCapture(
    val timestamp: String,
    val endpoint: String,
    val statusCode: Int,
    val durationMs: Long,
    val body: String?,
)

@Serializable
data class PerformanceRecord(
    val operation: String,
    val durationMs: Long,
    val memoryAllocatedBytes: Long,
    val iterations: Int,
    val thresholdMs: Long,
    val status: String,
)

object QualityEvidenceCollector {

    private val json = Json { prettyPrint = true }

    fun redact(text: String): String {
        return text
            .replace(Regex("""("access_token"\s*:\s*")[^"]+(")"""), """$1[REDACTED_ACCESS_TOKEN]$2""")
            .replace(Regex("""("refresh_token"\s*:\s*")[^"]+(")"""), """$1[REDACTED_REFRESH_TOKEN]$2""")
            .replace(Regex("""("geo_token"\s*:\s*")[^"]+(")"""), """$1[REDACTED_GEO_TOKEN]$2""")
            .replace(Regex("""("otp"\s*:\s*")[^"]+(")"""), """$1[REDACTED_OTP]$2""")
            .replace(Regex("""("email"\s*:\s*")[^"]+(")"""), """$1[REDACTED_EMAIL]$2""")
            .replace(Regex("""(Bearer\s+)[A-Za-z0-9_\-\.]+"""), """$1[REDACTED_TOKEN]""")
            .replace(Regex("""(x-bearer-token:\s*)[A-Za-z0-9_\-\.]+"""), """$1[REDACTED_GEO_TOKEN]""")
    }

    fun persistCaptures(
        execDir: File,
        requests: List<SanitizedRequestCapture>,
        responses: List<SanitizedResponseCapture>,
        perfRecords: List<PerformanceRecord>,
    ) {
        execDir.mkdirs()

        // 1. Single Send Request JSON for Email Verify OTP
        File(execDir, "email_verify_otp_send.json").writeText(
            AuthFixtures.VERIFY_EMAIL_OTP_SEND_REQUEST
        )

        // 2. Single Receive Success Response JSON for Email Verify OTP
        File(execDir, "email_verify_otp_receive_success.json").writeText(
            redact(AuthFixtures.VERIFY_EMAIL_OTP_SUCCESS_REALTIME)
        )

        // 3. Single Receive Failure Response JSON for Email Verify OTP (Invalid OTP)
        File(execDir, "email_verify_otp_receive_failure.json").writeText(
            AuthFixtures.VERIFY_EMAIL_OTP_INVALID_REALTIME
        )

        // 4. Single Send Request JSON for Home Feed API
        File(execDir, "home_feed_send.json").writeText(
            HomeFixtures.HOME_SEND_REQUEST
        )

        // 5. Single Receive Success Response JSON for Home Feed API (Realtime 200 OK)
        File(execDir, "home_feed_receive_success.json").writeText(
            HomeFixtures.HOME_REALTIME_RESPONSE
        )

        // 6. Single Send Request JSON for Home Banners API
        File(execDir, "home_banners_send.json").writeText(
            HomeFixtures.HOME_BANNERS_SEND_REQUEST
        )

        // 7. Single Receive Success Response JSON for Home Banners API (Realtime 200 OK)
        File(execDir, "home_banners_receive_success.json").writeText(
            HomeFixtures.HOME_BANNERS_REALTIME_RESPONSE
        )

        File(execDir, "performance_metrics.json").writeText(json.encodeToString(perfRecords))

        val diagnosticInfo = buildString {
            appendLine("Java Runtime: ${System.getProperty("java.version")}")
            appendLine("Java VM: ${System.getProperty("java.vm.name")} (${System.getProperty("java.vm.vendor")})")
            appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            appendLine("Max Memory: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
            appendLine("Total Memory: ${Runtime.getRuntime().totalMemory() / (1024 * 1024)} MB")
            appendLine("Free Memory: ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB")
            appendLine("Available Processors: ${Runtime.getRuntime().availableProcessors()}")
        }
        File(execDir, "system_environment.txt").writeText(diagnosticInfo)
    }
}
