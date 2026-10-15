package com.vplayed.shared.quality

import kotlinx.serialization.Serializable

/**
 * Unified Quality Framework test result model adhering to Codex master specification.
 */
@Serializable
enum class TestCategory {
    API_CONTRACT,
    APP_LOGIC,
    UI_RENDERING,
    NAVIGATION_E2E,
    PLATFORM_COMPATIBILITY,
    PERFORMANCE_STABILITY,
    SECURITY_PRIVACY,
    STATIC_ANALYSIS,
}

@Serializable
enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
}

@Serializable
enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFORMATIONAL,
}

@Serializable
enum class ErrorCategory {
    API_CONTRACT_FAILURE,
    UI_STATE_FAILURE,
    NAVIGATION_REGRESSION,
    PARSING_FAILURE,
    TIMEOUT_ERROR,
    NETWORK_FAILURE,
    CONCURRENCY_RACE,
    STORAGE_CORRUPTION,
    SECURITY_SECRET_LEAK,
    NONE,
}

@Serializable
enum class ConfidenceLevel {
    CONFIRMED,
    HIGH,
    MEDIUM,
    LOW,
}

@Serializable
data class QualityTestResult(
    val testId: String,
    val feature: String,
    val module: String,
    val category: TestCategory,
    val scenario: String,
    val expectedBehavior: String,
    val actualBehavior: String,
    val status: TestStatus,
    val severity: Severity,
    val environment: String = "DEVELOPMENT",
    val platform: String = "KMP / Android Host (JVM 11)",
    val buildVariant: String = "debug",
    val appVersion: String = "3.0.0",
    val timestamp: String,
    val durationMs: Long,
    val errorCategory: ErrorCategory = ErrorCategory.NONE,
    val exceptionDetails: String? = null,
    val sourceFile: String? = null,
    val lineNumber: Int? = null,
    val evidencePaths: List<String> = emptyList(),
    val associatedApiCalls: List<String> = emptyList(),
    val reproductionSteps: List<String> = emptyList(),
    val suspectedRootCause: String? = null,
    val confidence: ConfidenceLevel? = null,
    val recommendedFix: String? = null,
)

@Serializable
data class FeatureQualitySummary(
    val featureName: String,
    val apiStatus: String,
    val logicStatus: String,
    val uiStatus: String,
    val e2eStatus: String,
    val overallStatus: String,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
)

@Serializable
data class QualityExecutionReport(
    val executionId: String,
    val timestamp: String,
    val buildVariant: String = "debug",
    val appVersion: String,
    val platform: String,
    val totalTests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val passPercentage: Double,
    val coveragePercentage: Double,
    val durationMs: Long,
    val results: List<QualityTestResult>,
    val featureSummaries: List<FeatureQualitySummary>,
    val severityDistribution: Map<String, Int>,
    val categoryDistribution: Map<String, Int>,
)
