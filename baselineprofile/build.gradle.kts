// Fix 4: Baseline Profile generator module.
//
// This is a com.android.test module — it runs on a real device (or emulator)
// and records the hot code paths that ART should AOT-compile. The output
// (baseline-prof.txt) is merged into the :app release APK by the
// androidx.baselineprofile plugin.
//
// To generate / regenerate the profile:
//   ./gradlew :app:generateBaselineProfile
//
// This runs the BaselineProfileGenerator test below, collects the HRF rules,
// writes them to app/src/main/baseline-prof.txt, and merges them into the
// next release build automatically.
//
// Run on a physical device or a rooted emulator — Macrobenchmark cannot
// compile profiles on non-rooted stock emulators.

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace    = "com.primaloptima.scribe.baselineprofile"
    compileSdk   = 37

    defaultConfig {
        minSdk = 28   // Macrobenchmark requires minSdk 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Points to the :app module that will be benchmarked
    targetProjectPath = ":app"

    // Enables self-instrumentation: the test apk instruments the app apk
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // The benchmark build type runs against the app's release variant
        // so JIT noise is minimised and R8-optimised paths are profiled.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test:runner:1.6.2")
}

// Tells the plugin where to find the generated baseline-prof.txt once tests run.
androidComponents {
    onVariants(selector().all()) { variant ->
        val artifactType = com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST
    }
}
