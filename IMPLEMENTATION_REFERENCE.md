I have an app at
https://github.com/xyrvik-maker/Scribe-Android-Native.git
I've already added the GitHub access token in secrets as GITHUB_TOKEN
Clone the repository and follow the instructions below
Below is a precise list of issues to fix and updates to make. Apply all of them according to the workflow at the bottom:

# Section 1:

**Update all dependencies in this Android project to their latest stable versions. Make all the changes listed below carefully and completely.**

---

**PART 1 — BUILD TOOLS (root `build.gradle.kts` and `gradle/libs.versions.toml`)**

Update the root `build.gradle.kts` plugin declarations:
```
AGP: 8.7.0 → 9.1.1
Kotlin: 2.0.21 → 2.4.10
KSP: 2.0.21-1.0.28 → 2.3.9
```

Also remove the `id("org.jetbrains.kotlin.android")` plugin declaration from root `build.gradle.kts` and from `app/build.gradle.kts` — AGP 9.x has built-in Kotlin support and no longer needs it declared separately.

Also remove the `resolutionStrategy.eachDependency` block in `app/build.gradle.kts` that forces `kotlin-stdlib` to `2.0.21` — it's no longer needed and will conflict.

Update `gradle/libs.versions.toml` to match:
```
agp = "9.1.1"
kotlin = "2.4.10"
googleDevtoolsKsp = "2.3.9"
```

Delete the entire `[plugins]` section from `libs.versions.toml` — the root `build.gradle.kts` is the single source of truth for plugin versions.

Update `gradle/wrapper/gradle-wrapper.properties` Gradle version to `9.5.1`.

**Java & Kotlin JVM compatibility — REQUIRED for AGP 9:**

AGP 9.x requires JDK 21 and will fail to build with JDK 17. Update `app/build.gradle.kts`:
```
// CHANGE:
sourceCompatibility = JavaVersion.VERSION_17  →  JavaVersion.VERSION_21
targetCompatibility = JavaVersion.VERSION_17  →  JavaVersion.VERSION_21
jvmTarget = "17"                              →  "21"
```

---

**PART 2 — COMPOSE & UI (`app/build.gradle.kts`)**

```
androidx.compose:compose-bom: 2024.10.01 → 2026.06.00
androidx.activity:activity-compose: 1.9.3 → 1.13.0
androidx.activity:activity-ktx: 1.9.3 → 1.13.0
dev.chrisbanes.haze:haze: 1.3.1 → 1.7.2
dev.chrisbanes.haze:haze-materials: 1.3.1 → 1.7.2
```

Do NOT upgrade haze beyond 1.7.2 — version 2.0 is a breaking API change that requires significant code rewrites.

---

**PART 3 — ANDROIDX LIBRARIES (`app/build.gradle.kts`)**

```
androidx.core:core-ktx: 1.15.0 → 1.16.0
androidx.lifecycle:lifecycle-viewmodel-ktx: 2.8.7 → 2.10.0
androidx.lifecycle:lifecycle-livedata-ktx: 2.8.7 → 2.10.0
androidx.lifecycle:lifecycle-runtime-ktx: 2.8.7 → 2.10.0
androidx.lifecycle:lifecycle-viewmodel-compose: 2.8.7 → 2.10.0
androidx.lifecycle:lifecycle-runtime-compose: 2.8.7 → 2.10.0
androidx.datastore:datastore-preferences: 1.1.1 → 1.2.1
androidx.documentfile:documentfile: 1.0.1 → 1.1.0
androidx.core:core-splashscreen: 1.0.1 → 1.2.0
```

**Coroutines:**
```
org.jetbrains.kotlinx:kotlinx-coroutines-android: 1.8.1 → 1.10.2
```

**Room — special instructions:**
- The current `app/build.gradle.kts` has Room at `2.6.1`. Update all three Room entries to `2.8.4`.
- **Remove** `androidx.room:room-ktx` entirely — in Room 2.8.x it has been merged into `room-runtime` and the artifact is now blank.
- Also clean up `gradle/libs.versions.toml`: remove the `roomKtx` version entry and the `androidx-room-ktx` library entry, and update `roomRuntime` and `roomCompiler` from `2.7.0` to `2.8.4`.
- The final Room dependencies in `app/build.gradle.kts` should be only:
```kotlin
implementation("androidx.room:room-runtime:2.8.4")
ksp("androidx.room:room-compiler:2.8.4")
```

---

**PART 4 — THIRD-PARTY LIBRARIES (`app/build.gradle.kts`)**

**Coil — this is a major migration, follow carefully:**

Coil 3 uses a completely new Maven group ID. Remove ALL existing Coil 2 dependencies:
```
// REMOVE these:
implementation("io.coil-kt:coil-compose:2.7.0")
implementation("io.coil-kt:coil:2.7.0")
```

Add the Coil 3 replacements:
```kotlin
implementation("io.coil-kt.coil3:coil-compose:3.5.0")
implementation("io.coil-kt.coil3:coil:3.5.0")
```

Then update all import statements across the codebase:
- Replace `import coil.compose.AsyncImage` → `import coil3.compose.AsyncImage`
- Replace `import coil.compose.rememberAsyncImagePainter` → `import coil3.compose.rememberAsyncImagePainter`
- Replace `import coil.request.ImageRequest` → `import coil3.request.ImageRequest`
- Replace `import coil.ImageLoader` → `import coil3.ImageLoader`
- Search the entire project for any other `import coil.` and replace with `import coil3.`

**Lottie — note: it is declared TWICE in `app/build.gradle.kts`:**
Both lines must be updated:
```
implementation("com.airbnb.android:lottie-compose:6.4.1") → 6.7.1
api("com.airbnb.android:lottie:6.4.1")                   → 6.7.1
```

**Other third-party updates:**
```
com.google.code.gson:gson: 2.11.0 → 2.13.2
```

---

**PART 5 — MINIMUM SDK UPDATE**

Update `minSdk` in `app/build.gradle.kts` from `21` to `23`.

Reason: Starting June 2025, many new AndroidX library releases require `minSdk 23`. Room 2.8.x, Lifecycle 2.10.x, and other updated libraries you're now using require it. The vast majority of real devices (99%+) run Android 6.0+ so this has no meaningful user impact.

---

**PART 6 — COMPILE AND TARGET SDK**

Update in `app/build.gradle.kts`:
```
compileSdk = 35 → 36
targetSdk = 35 → 36
```

---

**PART 7 — GITHUB ACTIONS CI WORKFLOW (`.github/workflows/build.yml`)**

The CI workflow must be updated to match the new build requirements — AGP 9.1.1 will fail on JDK 17 and Gradle 8.x:

```
Set up JDK: java-version: '17'  →  '21'
Setup Gradle: gradle-version: '8.10.2'  →  '9.5.1'
```

Also update the step name from `"Setup Gradle 8.10.2"` to `"Setup Gradle 9.5.1"` and from `"Set up JDK 17"` to `"Set up JDK 21"` for clarity.

---

**After making these changes, verify:**
1. `libs.versions.toml` and `build.gradle.kts` use consistent versions with no conflicts
2. No `room-ktx` remains anywhere (neither in `build.gradle.kts` nor in `libs.versions.toml`)
3. No `io.coil-kt:coil` (v2) imports remain anywhere — only `io.coil-kt.coil3`
4. No `org.jetbrains.kotlin.android` plugin is declared anywhere
5. No `resolutionStrategy` block forcing an old kotlin-stdlib version
6. The `[plugins]` section is fully removed from `libs.versions.toml`
7. `sourceCompatibility`, `targetCompatibility`, and `jvmTarget` are all set to Java 21
8. `.github/workflows/build.yml` uses JDK 21 and Gradle 9.5.1
9. Run `./gradlew assembleDebug` and confirm it builds cleanly
10. Then move to Section 2

---


# Section 2: UI Fix & Improvement

---

## 1. Format/Shortcut Bar
**File:** `ui/screens/MainEditorScreen.kt`

Make sure the bottom format bar (B, I, H1, H2, Quote, List, Code… shortcut buttons) should only show when the keyboard appears.

**Fix:** Wrap the entire `bottomBar` content with a keyboard visibility check using `WindowInsets.ime`:

---

## 2. Format Bar Visual Style — Fix Background, Buttons, and + Button

**File:** `ui/screens/MainEditorScreen.kt` — the `bottomBar` `Surface` + `Row` block; also `ShortcutBarView.kt`

**Problems:**
- The `FormatButton` composable uses `OutlinedButton` which has a stroke border that looks cluttered; the light purple tint from the default `MaterialTheme.colorScheme.primary` bleeds through in all themes
- The `+` add-shortcut button has a visible box/card background behind it that doesn't belong
- The bar background is "messy" — it needs a clean frosted glass look that completely blurs the content behind it, with enough opacity to keep all text readable

## 3. Background Image resize when keyboard appears in Editor — Fix IME Resize

**Problem:** When the keyboard opens, the background image shifts/scrolls upward because `windowSoftInputMode` is set to `adjustResize`.

## 4. Dropdown Menus and Drawer Panels — Frosted Glass, Not Transparent

**Files:** `MainEditorScreen.kt`, `HomeScreen.kt`, `ScribeTheme.kt`

**Problem:** The three-dot `DropdownMenu` backgrounds and the left/right drawer panel backgrounds are too transparent — text is hard to read over background images.


## 5. Auto Text & Accent Color When User Hasn't Set Them

**File:** `ui/theme/ScribeTheme.kt`, `util/ThemeManager.kt`

**Problem:** If the user sets a background image but doesn't explicitly configure text/accent colors, the default colors may be unreadable (e.g., dark text on a dark image, or white text on a light image).

**Fix:** In `ScribeComposeTheme`, after resolving `resolvedTheme`, check if the background image is active and compute a luminance-aware fallback for text and accent

## 6. Light Purple Persisting Across Themes — Remove Hardcoded Colors

**Files:** `MainEditorScreen.kt`, `HomeScreen.kt`, all Compose screens

**Problem:** The default Material3 `primary` color (a muted purple from the baseline scheme) leaks through in buttons, segmented controls, selected tab indicators, and `NavigationBarItem` selected states when the active theme's accent doesn't override them properly.

**Root cause:** `ScribeComposeTheme` correctly maps `primary = accentIcons` from the theme, but some widgets (like `SegmentedButton` in Statistics, `NavigationBarItem` selected highlight, `OutlinedButton` border) still pull from the base `colorScheme.primary` before animation completes, or from places where `MaterialTheme` is used before `ScribeComposeTheme` wraps them.

**Fix:**
- In `ScribeComposeTheme`, also explicitly set `tertiary = accentIcons` and `tertiaryContainer = surface` so no M3 slot falls back to purple.
- In `MainStatisticsTabContent.kt`, the `SingleChoiceSegmentedButtonRow` / `SegmentedButton` picks up `primary`. Override explicitly:
```kotlin
SegmentedButton(
    colors = SegmentedButtonDefaults.colors(
        activeContainerColor = parseComposeColor(theme.colors.accent),
        activeContentColor = onAccentColor,
        inactiveContainerColor = parseComposeColor(theme.colors.surface),
        inactiveContentColor = parseComposeColor(theme.colors.text)
    ),
    ...
)
```
Pass `activeTheme` into `MainStatisticsTabContent` and `DetailedStatisticsTab` for this.
- For `NavigationBar` selected state, explicitly set `NavigationBarItemDefaults.colors(indicatorColor = parseComposeColor(theme.colors.accent))` on all `NavigationBarItem` calls.

---

## 7. Remove Dark Gradient Overlays on Book/Notes Lists

**File:** `HomeScreen.kt` — `BooksTabContent` and `NotesTabContent`

**Problem:** Both tabs apply a `verticalGradientOverlay` `Box` that darkens the top and bottom edges of the list. This looks bad with background images and adds unnecessary visual noise.

**Fix:** Delete the `verticalGradientOverlay` `Brush` and the `Box` that applies it in both `BooksTabContent` and `NotesTabContent`:

```kotlin
// DELETE these entirely from both composables:
val verticalGradientOverlay = remember(themeBg) {
    Brush.verticalGradient(...)
}
// ...
Box(modifier = Modifier.fillMaxSize().background(verticalGradientOverlay))
```

---

## 8. Bottom Navigation Bar — Slimmer, More Refined

**File:** `HomeScreen.kt` — the `NavigationBar` in `Scaffold.bottomBar`

**Problem:** The bottom nav bar is taller than needed and the selected item highlight is the default purple pill.

**Fix:**
- Set `NavigationBar(tonalElevation = 0.dp)` and remove default height; use `windowInsetsPadding` manually to keep it tight.
- Add `modifier = Modifier.height(60.dp)` on the `NavigationBar` (default is 80dp — this saves 20dp of space).
- Override `NavigationBarItemDefaults.colors(indicatorColor = parseComposeColor(theme.colors.accent))` on every `NavigationBarItem`.
- Keep labels but reduce label font size to `10.sp` to compensate for the smaller bar height.

---

## 9. Statistics Screen — Visual Overhaul

**File:** `ui/screens/MainStatisticsTabContent.kt`

**Problem:** The Statistics tab looks cluttered and plain — the chart card, stat cards, and daily goal section all look like plain elevated cards floating over the background image with poor contrast.

**Fixes:**

**a) Tab row** — `SecondaryTabRow` uses `primary` color for the indicator. Override with theme accent. Also wrap the `SecondaryTabRow` in a `Surface` with the theme's `toolbar` color at 90% opacity so it reads clearly over any background.

**b) Stat cards (today / books / streak)** — Give each card a subtle accent-tinted background instead of plain `surfaceContainer`:
```kotlin
ElevatedCard(
    colors = CardDefaults.elevatedCardColors(
        containerColor = parseComposeColor(theme.colors.surface).copy(alpha = 0.92f)
    )
)
```
Add a colored top border accent strip (2dp height, theme accent color) at the top of each stat card using `Box` + `Divider` or `Canvas` to give them identity.

**c) Chart card** — The bar chart currently renders on a plain card over the background image, which can be invisible. Ensure the chart card has `containerColor = parseComposeColor(theme.colors.surface).copy(alpha = 0.95f)` — fully opaque enough to read the chart lines and bars.

**d) Daily Goal bar** — The `LinearProgressIndicator` is too thin and plain. Replace with a custom `Canvas`-drawn rounded progress bar, 12dp height, using the theme accent as fill color and `theme.colors.border` as track color. Add an animated fill using `animateFloatAsState`.

**e) Section spacing** — Increase `verticalArrangement = Arrangement.spacedBy(20.dp)` (currently 16dp) and add `horizontalPadding = 20.dp` for more breathing room.

**f) Wordmap tab** — Ensure the word map heatmap cells also use theme accent for the filled/active cells rather than a hardcoded color.

---

# Section 3: Themes Don't Apply Live (Require Back + Re-enter)

## Root Cause (Diagnosed)

There are **two separate bugs** working together to cause this:

---

### Bug 1 — `ScribeComposeTheme` reads the theme once and never re-reads it

**File:** `ui/theme/ScribeTheme.kt`

```kotlin
// CURRENT (broken):
@Composable
fun ScribeComposeTheme(appTheme: AppTheme? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resolvedTheme = appTheme ?: try {
        ThemeManager(context).activeTheme()  // ← called once at initial composition, never again
    } catch (_: Exception) {
        DefaultThemes.all.first()
    }
    // ...
}
```

`ThemeManager(context).activeTheme()` reads `activeThemeId` from `SharedPreferences` (via `PrefsManager`). This is a plain synchronous read with no reactive wrapper — Compose has no way to know when it changes. When `ThemeViewModel.setActive()` writes a new theme ID to both `SharedPreferences` and `DataStore`, `ScribeComposeTheme` is never told to recompose.

---

### Bug 2 — `HomeActivity`, `MainActivity`, and `BookActivity` call `ScribeComposeTheme` with no `appTheme` param and have no `onResume` reload

**Files:** `HomeActivity.kt`, `MainActivity.kt`, `BookActivity.kt`

```kotlin
// CURRENT (broken) — all three look like this:
setContent {
    ScribeComposeTheme {   // ← no appTheme arg, no reactive source
        HomeScreen(...)
    }
}
// No onResume, no reload
```

`ThemeListActivity` has `onResume { vm.reload() }`, but that only refreshes the ThemeListScreen itself — not HomeActivity or MainActivity which sit underneath it in the back stack.

---

## The Fix

### Step 1 — Make `ScribeComposeTheme` reactive to `DataStore`

Replace the one-time `ThemeManager(context).activeTheme()` call with a `collectAsState` on `ThemeDataStoreRepo.activeThemeIdFlow` and `customThemesJsonFlow`. Both flows emit whenever `DataStore` is written to — which `ThemeViewModel.setActive()` and `ThemeViewModel.save()` already do correctly.

**File:** `ui/theme/ScribeTheme.kt`

```kotlin
@Composable
fun ScribeComposeTheme(
    appTheme: AppTheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // NEW: Reactive theme resolution via DataStore flows
    val resolvedTheme: AppTheme = if (appTheme != null) {
        appTheme
    } else {
        val repo = remember { ThemeDataStoreRepo(context) }
        val themeManager = remember { ThemeManager(context) }

        val activeThemeId by repo.activeThemeIdFlow
            .collectAsState(initial = themeManager.activeTheme().id)
        val customThemesJson by repo.customThemesJsonFlow
            .collectAsState(initial = "[]")

        // Re-derive the full AppTheme whenever the id or custom themes json changes
        remember(activeThemeId, customThemesJson) {
            themeManager.allThemes().firstOrNull { it.id == activeThemeId }
                ?: DefaultThemes.all.first()
        }
    }

    // rest of ScribeComposeTheme is unchanged below this point...
}
```

Required imports to add:
```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.primaloptima.scribe.util.ThemeDataStoreRepo
```

**Why this works:** `collectAsState` turns the `DataStore` `Flow` into Compose `State`. Whenever `ThemeViewModel.setActive(id)` calls `dataStoreRepo.setActiveThemeId(id)`, the flow emits, `activeThemeId` state changes, `remember(activeThemeId, customThemesJson)` invalidates, `resolvedTheme` is a new object, and the entire `ScribeComposeTheme` tree recomposes with the new colors — instantly, with no navigation required.

---

### Step 2 — Fix `EditorViewModel` theme observation

**File:** `viewmodel/EditorViewModel.kt`

Currently `loadTheme()` is called once in `init {}` and only again if `reloadTheme()` is called manually (which nothing calls from the outside). Replace with a `DataStore` flow collector:

```kotlin
// In EditorViewModel init block, REPLACE:
// loadTheme()

// WITH:
private val dataStoreRepo = ThemeDataStoreRepo(getApplication())

init {
    // Observe active theme reactively — updates whenever user changes theme
    viewModelScope.launch {
        dataStoreRepo.activeThemeIdFlow.collectLatest { themeId ->
            _theme.value = themeManager.allThemes()
                .firstOrNull { it.id == themeId }
                ?: DefaultThemes.all.first()
        }
    }
    writingStats.reconcileStreak()
    loadExternalRoot()
}
```

Also remove the now-unused `loadTheme()` and `reloadTheme()` private functions from `EditorViewModel`.

---

### Step 3 — Remove the stale `SharedPreferences` path for theme storage (optional but clean)

`ThemeViewModel.setActive()` currently writes to **both** `SharedPreferences` (via `themeManager.setActiveTheme(id)` → `prefs.activeThemeId = id`) and `DataStore` (via `dataStoreRepo.setActiveThemeId(id)`). After Step 1, Compose only reads from `DataStore`. The `SharedPreferences` write can stay for backward compatibility, but `ScribeComposeTheme` and `EditorViewModel` must no longer read from `SharedPreferences` for theme resolution — only from `DataStore` flows.

---

### Step 4 — Remove `onResume { vm.reload() }` from `ThemeListActivity` (now redundant)

**File:** `ThemeListActivity.kt`

```kotlin
// DELETE this — no longer needed once DataStore is the single reactive source:
override fun onResume() {
    super.onResume()
    vm.reload()
}
```

`ThemeViewModel.activeTheme` is already a `LiveData` driven by `reload()`, which is called after every `setActive()` and `save()`. Once `ScribeComposeTheme` is reactive to DataStore, the theme preview in `ThemeListScreen` also updates correctly via the `activeTheme` LiveData.

---

# Section 4: Feature: Auto-Contrast Text Color Based on Background

## What This Does

When a background image is active, text anywhere in the app automatically picks white or dark color depending on whether the pixel region *behind* it is light or dark. A dark image region → white text. A bright image region → dark text. This happens automatically for every Text composable that opts in, with no frame drops.

---

## Architecture Overview

The system has 4 parts:

1. **Analysis bitmap** — A 32×32 downscaled copy of the background image, decoded once on a background thread when the URI changes, stored in a `CompositionLocal`.
2. **`LocalBgAnalysisBitmap`** — A `CompositionLocal<Bitmap?>` available anywhere in the tree.
3. **`rememberAdaptiveTextColor()`** — A composable function any `Text` can call to get the right color.
4. **`Modifier.adaptiveTextColor()`** — Convenience modifier that wires position tracking via `onLayoutRectChanged`.

---

## Step 1 — Add the Analysis Bitmap CompositionLocal

**File:** `ui/theme/ScribeTheme.kt`

Add alongside the existing `LocalHazeState`:

```kotlin
import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf

val LocalBgAnalysisBitmap = compositionLocalOf<Bitmap?> { null }
val LocalScreenSize = compositionLocalOf { Pair(1080f, 1920f) } // width, height in px
```

---

## Step 2 — Decode the 32×32 Analysis Bitmap When Background URI Changes

**File:** `ui/theme/ScribeTheme.kt` — inside `ScribeComposeTheme`, after `resolvedTheme` is determined.

```kotlin
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Inside ScribeComposeTheme, after resolvedTheme is resolved:

val bgUri = resolvedTheme.backgroundImageUri
val hasBgImage = !bgUri.isNullOrEmpty() && resolvedTheme.bgMode != "color"

// Decode tiny analysis bitmap once per URI change, on IO thread
var analysisBitmap by remember { mutableStateOf<Bitmap?>(null) }
LaunchedEffect(bgUri) {
    if (bgUri.isNullOrEmpty() || !hasBgImage) {
        analysisBitmap = null
        return@LaunchedEffect
    }
    analysisBitmap = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(bgUri)
                .size(32, 32)           // tiny — Coil downsamples efficiently
                .allowHardware(false)   // required for pixel access
                .build()
            (loader.execute(req).drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) { null }
    }
}

// Capture screen size in pixels for coordinate mapping
val view = LocalView.current
val screenWidthPx = remember { view.resources.displayMetrics.widthPixels.toFloat() }
val screenHeightPx = remember { view.resources.displayMetrics.heightPixels.toFloat() }

// Provide both via CompositionLocal, wrapping the existing content
MaterialTheme(colorScheme = animatedColorScheme) {
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBgAnalysisBitmap provides analysisBitmap,         // NEW
        LocalScreenSize provides Pair(screenWidthPx, screenHeightPx)  // NEW
    ) {
        // ... existing Box with background image and content() call, unchanged
    }
}
```

**Why 32×32:** Coil skips pixels during decode at this size rather than reading and discarding them — it's ~2ms on the IO thread. At 32×32, the worst-case luminance loop over a region is 1,024 pixel reads — under 0.1ms on the main thread, no background thread needed.

---

## Step 3 — The Luminance Helper (Pure Kotlin, No Libraries)

**File:** `ui/theme/AdaptiveColor.kt` (new file)

```kotlin
package com.primaloptima.scribe.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * BT.709 perceived luminance: weighted sum matching human eye sensitivity.
 * Returns 0.0 (black) to 1.0 (white).
 */
fun regionLuminance(
    bitmap: Bitmap,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float
): Double {
    val bw = bitmap.width.toFloat()   // 32
    val bh = bitmap.height.toFloat()  // 32

    // Map screen coordinates → bitmap coordinates
    val x0 = ((screenRect.left / screenWidthPx) * bw).toInt().coerceIn(0, bitmap.width - 1)
    val y0 = ((screenRect.top / screenHeightPx) * bh).toInt().coerceIn(0, bitmap.height - 1)
    val x1 = ((screenRect.right / screenWidthPx) * bw).toInt().coerceIn(x0, bitmap.width - 1)
    val y1 = ((screenRect.bottom / screenHeightPx) * bh).toInt().coerceIn(y0, bitmap.height - 1)

    var total = 0.0
    var count = 0
    for (x in x0..x1) {
        for (y in y0..y1) {
            val pixel = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(pixel) / 255.0
            val g = android.graphics.Color.green(pixel) / 255.0
            val b = android.graphics.Color.blue(pixel) / 255.0
            // BT.709 coefficients
            total += 0.2126 * r + 0.7152 * g + 0.0722 * b
            count++
        }
    }
    return if (count > 0) total / count else 0.5
}

/**
 * Given a screen region and the analysis bitmap, return either the light
 * or dark color depending on which contrasts better with the background.
 * Threshold 0.45 (slightly below 0.5) favors white text for ambiguous mid-tones,
 * since background images tend to have texture that makes dark text harder to read.
 */
fun contrastingTextColor(
    bitmap: Bitmap?,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFF1A1A1A)
): Color {
    if (bitmap == null || screenRect.isEmpty) return lightColor
    val lum = regionLuminance(bitmap, screenRect, screenWidthPx, screenHeightPx)
    return if (lum < 0.45) lightColor else darkColor
}
```

---

## Step 4 — The `rememberAdaptiveTextColor` Composable

**File:** `ui/theme/AdaptiveColor.kt` (append to same file)

```kotlin
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.graphics.Color

/**
 * Returns a Color (white or dark) that contrasts with the background image
 * at this composable's screen position. Falls back to [fallback] when no
 * background image is active.
 *
 * Usage:
 *   var textColorModifier = Modifier.Companion
 *   val textColor = rememberAdaptiveTextColor { textColorModifier = it }
 *   Text("Hello", color = textColor, modifier = textColorModifier)
 */
@Composable
fun rememberAdaptiveTextColor(
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFF1A1A1A),
    fallback: Color = Color.Unspecified,  // Unspecified = inherits from MaterialTheme
): Pair<Color, Modifier> {
    val bitmap = LocalBgAnalysisBitmap.current
    val (screenW, screenH) = LocalScreenSize.current

    // No background image active — return theme default
    if (bitmap == null) return Pair(fallback, Modifier)

    var bounds by remember { mutableStateOf(Rect.Zero) }

    // Cache result — only recomputes when bounds change (after layout)
    val color by remember(bounds, bitmap) {
        derivedStateOf {
            contrastingTextColor(bitmap, bounds, screenW, screenH, lightColor, darkColor)
        }
    }

    // onLayoutRectChanged: Compose 1.8+, much lower overhead than onGloballyPositioned.
    // debounceMillis=150 means it won't fire more than once per 150ms during scrolling —
    // prevents excessive recomputation in LazyColumn while still reacting to layout changes.
    val trackingModifier = Modifier.onLayoutRectChanged(
        debounceMillis = 150,
        throttleMillis = 0
    ) { layoutBounds ->
        val newBounds = layoutBounds.boundsInRoot
        // Only update if bounds actually moved meaningfully (>2px) to avoid
        // spurious recompositions from sub-pixel layout fluctuations
        if ((newBounds.left - bounds.left).let { it > 2f || it < -2f } ||
            (newBounds.top - bounds.top).let { it > 2f || it < -2f }) {
            bounds = newBounds
        }
    }

    return Pair(color, trackingModifier)
}
```

---

## Step 5 — Apply Across the App

### Where to use it

Only apply `rememberAdaptiveTextColor` on Text elements that sit **directly over the background image** with no opaque Surface behind them. Text inside cards, drawers, and dialogs already has an opaque background — skip those.

**Target locations:**
- `HomeScreen.kt` — top bar title "Scribe", bottom nav labels
- `MainEditorScreen.kt` — top bar title (note name), word count pill
- `MainStatisticsTabContent.kt` — the tab row labels "Statistics" / "Wordmap"
- `BookScreen.kt` — any heading rendered directly over the book background

### Usage pattern

```kotlin
// BEFORE:
Text(
    text = "Scribe",
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onBackground
)

// AFTER:
val (adaptiveColor, adaptiveModifier) = rememberAdaptiveTextColor()
Text(
    text = "Scribe",
    fontWeight = FontWeight.Bold,
    color = adaptiveColor,
    modifier = adaptiveModifier
)
```

### For Icons (TopAppBar icons, nav icons)

Icons use `tint` not `color`, but the same composable works:

```kotlin
val (iconColor, iconMod) = rememberAdaptiveTextColor()
Icon(
    imageVector = Icons.Default.Menu,
    contentDescription = null,
    tint = iconColor,
    modifier = iconMod
)
```

---

## Step 6 — Theme-Level Fallback (No Background Image)

When no background image is active (`analysisBitmap == null`), `rememberAdaptiveTextColor` returns `Color.Unspecified`, which means the `Text` inherits its color from `LocalContentColor` — which is already set correctly to contrast with the solid background color by `ScribeComposeTheme`. So solid-color themes need zero changes and continue working exactly as before.

---

## Performance Budget

| Operation | Thread | Frequency | Cost |
|---|---|---|---|
| 32×32 bitmap decode | IO | Once per URI change | ~2ms |
| Store in CompositionLocal | Main | Once per URI change | Zero |
| `onLayoutRectChanged` callback | Main (post-layout) | Debounced 150ms | Near-zero |
| `regionLuminance` on 32×32 region | Main | Once per layout change per element | <0.1ms |
| `derivedStateOf` cache check | Main | Every recomposition | Negligible |

Total steady-state overhead per frame: **zero** — all computation happens post-layout, not during draw, and results are cached until position changes.

---

## What NOT to Do

- **Do not** call `rememberAdaptiveTextColor` inside `Text` composables that are inside `Card`, `ModalDrawerSheet`, `DropdownMenu`, `AlertDialog`, or any other composable that already has an opaque `Surface` background. Those already have correct contrast from `MaterialTheme.colorScheme`.
- **Do not** use `onGloballyPositioned` — it traverses the full UI tree on every call and cannot be debounced. `onLayoutRectChanged` is the correct modern API.
- **Do not** decode the full-resolution image for luminance analysis — only the 32×32 thumbnail. Never call `allowHardware(false)` on the display-resolution `AsyncImage`, only on the analysis request.



# Section 5:

> Conduct a complete code audit, fix any bugs, and modernize the UI/UX
> **Strict Rules & Constraints:**
>  1. **Zero Regression:** Do NOT remove, break, or disable any existing features, screens, or underlying functional logic. Every feature that works now must work after your changes.
>  2. **Build Compatibility:** Do NOT break or delete existing build configurations, manifest files, or GitHub Actions workflow scripts, update if needed
> **What You Need To Do:**
>  1. **Code Audit & Bug Fixes:**
>    * Analyze the entire repository for syntax errors, broken imports, missing dependencies, or unhandled edge cases.
>    * Fix any underlying bugs, memory leaks, or performance bottlenecks you discover.
>  2. **UI & Theme Overhaul:**
>    * Modernize the app's visual layout, typography, and spacing to give it a clean, premium, and polished feel.
>    * Improve dark/light theme consistency, contrast, and overall color palette.
>    * Ensure responsive alignment across various mobile screen sizes.
>  3. **Icons & Visual Elements:**
>    * Upgrade old or missing icons and emoji with clean, modern vector/SVG icons.
>    * Ensure all icon routes, asset folders, and UI render calls are properly mapped without broken image links.
>  4. **Output Requirements:**
>    * Provide a summary list of the exact changes and bug fixes you made.
>    * Commit everything into the built-in git


# Workflow:

1: Save the full prompt with sections and parts in a reference file for your reference, create a plan file then commit, tick whenever you completes a points in section and commits the changes so you know your progress
2: start working one section at a time and one point at a time accurately, make sure to understand the code and research best ways to implement the vision instead of just following blindly
3: Make sure to commit everything after completing every section and every point in each section so nothing geyser lost, this is very important, commit at each points in each section
4: After all Sections and points are done, do a validation check, after confirming there's no error, commit and push it to GitHub
