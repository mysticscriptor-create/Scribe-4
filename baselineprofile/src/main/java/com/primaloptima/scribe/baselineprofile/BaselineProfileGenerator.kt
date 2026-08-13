package com.primaloptima.scribe.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fix 4: Baseline Profile generator for Scribe.
 *
 * Records the hot code paths ART should AOT-compile so the first run of the
 * app is as fast as a warmed-up run. Covers the critical user journeys that
 * matter most for perceived performance:
 *
 *   1. Cold start → HomeScreen (tab-swipe lag fix benefits most from this)
 *   2. Swipe across all three tabs (Dashboard / Books / Stats)
 *   3. Open a book → BookScreen
 *   4. Open a note → MainEditorScreen
 *   5. Return home
 *
 * Run with:
 *   ./gradlew :app:generateBaselineProfile
 *
 * The plugin writes the result to:
 *   app/src/main/baseline-prof.txt
 * and merges it into every release APK automatically.
 *
 * NOTES:
 * - Requires a physical device or rooted emulator (API 28+).
 * - UiAutomator selectors below use content descriptions set in Scribe's
 *   composables. If you rename a CD, update the selector here too.
 * - The generator runs several warm-up iterations before recording — normal.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName  = "com.primaloptima.scribe",
        startupMode  = StartupMode.COLD,
        profileBlock = { scribeCriticalJourneys() }
    )
}

// ── Critical user journeys ────────────────────────────────────────────────────

private fun MacrobenchmarkScope.scribeCriticalJourneys() {
    // 1. Launch the app and wait for the home screen to settle.
    //    The HorizontalPager + LazyColumn renders on first frame — this is the
    //    highest-value path to profile (cold-start jank on first scroll/swipe).
    startActivityAndWait()

    // Give the pager and lazy lists time to complete their first layout pass.
    device.waitForIdle(2_000)

    // 2. Swipe across all three tabs to profile the HorizontalPager animation
    //    paths and each tab's initial composition.
    //    Tab order: Dashboard (0) → Books (1) → Stats (2)
    repeat(2) {
        swipeTabRight()
        device.waitForIdle(800)
    }

    // 3. Swipe back to Books tab (the most common landing tab)
    swipeTabLeft()
    device.waitForIdle(800)

    // 4. Tap the first book card (if one exists) to profile BookScreen's
    //    composition and its LazyColumn of notes.
    val firstBook = device.findObject(By.res("com.primaloptima.scribe:id/book_card"))
        ?: device.findObject(By.desc("book_card"))
    if (firstBook != null) {
        firstBook.click()
        device.wait(Until.gone(By.pkg("com.primaloptima.scribe").depth(0)), 2_000)
        device.waitForIdle(1_500)

        // 5. Tap the first note (if one exists) to profile MainEditorScreen
        //    and its rich-text rendering.
        val firstNote = device.findObject(By.res("com.primaloptima.scribe:id/note_card"))
            ?: device.findObject(By.desc("note_card"))
        if (firstNote != null) {
            firstNote.click()
            device.waitForIdle(2_000)

            // 6. Press back to return to BookScreen, then back to HomeScreen.
            device.pressBack()
            device.waitForIdle(1_000)
        }

        device.pressBack()
        device.waitForIdle(1_000)
    }

    // 7. Scroll the Books list to profile lazy-list scroll paths.
    val booksList = device.findObject(By.scrollable(true))
    booksList?.fling(Direction.DOWN)
    device.waitForIdle(500)
    booksList?.fling(Direction.UP)
    device.waitForIdle(500)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Swipes left-to-right on the screen centre to advance to the next tab.
 * The HorizontalPager in HomeScreen responds to horizontal flings.
 */
private fun MacrobenchmarkScope.swipeTabRight() {
    val screenWidth  = device.displayWidth
    val screenHeight = device.displayHeight
    device.swipe(
        /* startX = */ screenWidth  / 4,
        /* startY = */ screenHeight / 2,
        /* endX   = */ screenWidth  * 3 / 4,
        /* endY   = */ screenHeight / 2,
        /* steps  = */ 20
    )
}

private fun MacrobenchmarkScope.swipeTabLeft() {
    val screenWidth  = device.displayWidth
    val screenHeight = device.displayHeight
    device.swipe(
        /* startX = */ screenWidth  * 3 / 4,
        /* startY = */ screenHeight / 2,
        /* endX   = */ screenWidth  / 4,
        /* endY   = */ screenHeight / 2,
        /* steps  = */ 20
    )
}
