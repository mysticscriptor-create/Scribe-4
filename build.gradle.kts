// Top-level build file. Add configuration options common to all sub-projects/modules here.
plugins {
    id("com.android.application") version "9.3.0" apply false
    id("com.android.test")        version "9.3.0" apply false   // needed by :baselineprofile module
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false  // needed by :baselineprofile module
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    // Fix 4: Baseline Profile Gradle plugin — generates baseline-prof.txt from
    // the :baselineprofile Macrobenchmark module. Requires AGP ≥ 8.1; we are on 9.2.0.
    id("androidx.baselineprofile") version "1.4.1" apply false
}
