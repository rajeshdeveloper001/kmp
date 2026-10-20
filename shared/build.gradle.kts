import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
    // One XCFramework for the VPlayed target: iOS device and the Apple-silicon iOS simulator. There is
    // no iosX64 slice: Compose Multiplatform Resources (the shared string catalog) does not publish
    // one. VPlayed runs on Apple-silicon Macs and Apple Vision Pro as the iPad
    // app ("Designed for iPad"), which uses the iosArm64 slice, so there are no macOS or visionOS targets.
    val xcFramework = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
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

        // The shared string catalog (composeResources) ships in the AAR; the KMP library plugin
        // turns Android resources off unless asked.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            // Shared string catalog (src/commonMain/composeResources). `api` so the apps get the
            // generated `Res` accessors and the lookup functions.
            api(libs.compose.components.resources)
            // Required by the Compose compiler plugin, which the resources plugin needs; no UI is written here.
            implementation(libs.compose.runtime)
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

// The one string catalog both apps read: `Res.string.*` / `Res.plurals.*` in com.vplayed.shared.l10n.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.vplayed.shared.l10n"
    generateResClass = always
}

tasks.register("runApiContractTests") {
    group = "verification"
    description = "Runs comprehensive API contract and wire serialization validation tests."
    dependsOn("testAndroidHostTest")
}

tasks.register("runQualityChecks") {
    group = "verification"
    description = "Executes the unified automated quality framework and diagnostic validation suite."
    dependsOn("testAndroidHostTest")
}

tasks.register("generateQualityReport") {
    group = "verification"
    description = "Generates execution dashboards, sanitized API captures, and test-results artifacts."
    dependsOn("testAndroidHostTest")
}
