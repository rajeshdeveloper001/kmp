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

publishing {
    repositories {
        maven {
            name = "LocalRepo"
            url = uri(rootProject.layout.projectDirectory.dir("repo/localMaven"))
        }
    }
}

kotlin {
    // One XCFramework for the VPlayed target: iOS device and iOS simulator (with the Intel slice for
    // universal simulator builds). VPlayed runs on Apple-silicon Macs and Apple Vision Pro as the iPad
    // app ("Designed for iPad"), which uses the iosArm64 slice, so there are no macOS or visionOS targets.
    val xcFramework = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
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
