// Line coverage of :shared (KMP-605), measured on its JVM tests (androidHostTest) with Kover.
// Opt-in, so normal builds of kmp, AndroidJP (composite build) and VPlayed never load the plugin:
//   ./gradlew -I scripts/coverage.init.gradle.kts :shared:koverHtmlReport :shared:koverLog
initscript {
    repositories { gradlePluginPortal() }
    dependencies { classpath("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.11") }
}

allprojects {
    if (path == ":shared") apply<kotlinx.kover.gradle.plugin.KoverGradlePlugin>()
}

// Proposed gate (KMP-605; to be agreed): fail `koverVerify` below 90 % line coverage of :shared.
// The JVM run cannot reach the Apple-only code (watchers, Darwin engine) or the main-thread
// controller factories; VPlayed's tests cover those.
allprojects {
    if (path == ":shared") {
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            reports.verify.rule { minBound(90) }
        }
    }
}
