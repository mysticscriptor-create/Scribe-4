plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Fix 4 (DISABLED on Termux — baseline profile generation requires a physical device
    // or rooted emulator, neither of which is available here. Re-enable when building
    // on a proper machine with an emulator or connected test device.)
    // id("androidx.baselineprofile")
}

android {
    namespace = "com.primaloptima.scribe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.primaloptima.scribe"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            val localDebugKeystore = file("${rootDir}/debug.keystore")
            if (localDebugKeystore.exists()) {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Phase 0-A: R8 enabled for ~25-30% startup improvement + smaller APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use debug signing for a sideloadable APK — no keystore needed.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Issue #2 fix: stability configuration file.
    // Tells the Compose compiler to treat kotlin.collections.List/Set/Map and all
    // Scribe data-layer classes as stable. This is the modern 2026 approach — no
    // compose-runtime dependency needed in data/model classes, no @Immutable
    // annotations on Room entities, and it covers the List<T> instability that
    // @Immutable on the item class alone cannot fix.
    // See: https://developer.android.com/develop/ui/compose/performance/stability/fix
    composeCompiler {
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("app/stability_config.conf")
        )
        // Emit compiler metrics to build/compose_metrics/ so you can verify
        // classes are marked stable after this change:
        //   ./gradlew assembleDebug
        //   cat app/build/compose_metrics/app_debug-module.json | grep -A2 "unstable"
        // Uncomment the two lines below to enable metrics during auditing:
        // metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
        // reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Fix 4 (DISABLED — see plugin comment above)
// baselineProfile {
//     automaticGenerationDuringBuild = false
// }

dependencies {
    // Jetpack Compose BOM & core dependencies
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Phase 0-C: removed duplicate coil3:coil — coil-compose already includes it
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")

    // Phase 0-D: removed api("lottie:6.7.1") — lottie-compose includes lottie core
    implementation("com.airbnb.android:lottie-compose:6.7.1")

    // Phase 0-E: datastore already present — no change needed
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Core AndroidX
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.13.0")

    // Navigation 3 (replaces navigation-compose)
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")
    implementation("androidx.navigation3:navigation3-ui:1.1.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
    // Phase 3: two-pane adaptive layout (tablet/foldable)
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")

    // Kotlin serialization runtime (required for type-safe nav routes)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Material Design 3
    implementation("com.google.android.material:material:1.14.0")

    // ViewModel + LiveData + coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Room database — Phase 0-B: explicit to prevent silent version drift
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // SAF document file helpers
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Markwon Markdown Engine
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")

    // MPAndroidChart for statistics and word count analytics
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Color picker for theme editor (HSV wheel + sliders)
    implementation("com.github.skydoves:colorpickerview:2.2.4")

    // Palette — extract dominant colors from book cover images
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Modern Android 12+ splash screen
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Haze for frosted glass effects
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")

    // Timber — smart debug logging (zero-cost in release builds)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Fix 4 (DISABLED — profileinstaller and baselineProfile dependency removed
    // because the androidx.baselineprofile plugin is disabled on Termux.
    // Re-enable both lines when the plugin is re-enabled on a proper machine.)
    // implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // "baselineProfile"(project(":baselineprofile"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
