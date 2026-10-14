import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

// Coordinates AndroidJP resolves (composite build substitution or a published artifact).
group = "com.vplayed"
version = "0.1.0"

kotlin {
    // One XCFramework for the VPlayed target: iOS device, iOS simulator and macOS, with Intel slices
    // because the app ships universal simulator and macOS (Intel Macs run macOS 26) builds.
    // macosX64 is deprecated in Kotlin (removal announced); dropping it means dropping Intel Macs.
    // visionOS has no Kotlin/Native target (KMP_PLANNING/KMP_ARCHITECTURE_ANALYSIS.md §6).
    val xcFramework = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        macosArm64(),
        macosX64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.vplayed.shared")
            xcFramework.add(this)
        }
    }

    android {
        namespace = "com.vplayed.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
