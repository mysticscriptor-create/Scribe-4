package com.primaloptima.scribe.ui.util

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Returns the current keyboard height in pixels, updated every frame
 * during the keyboard open/close animation (Level 3 approach).
 *
 * How it works:
 *   - WindowInsetsAnimationCompat.Callback fires on every animation frame
 *     while the keyboard is sliding in or out, giving us the exact height
 *     at each step. This is what makes the toolbar move smoothly with the
 *     keyboard instead of just snapping in or out.
 *   - ViewCompat.setOnApplyWindowInsetsListener fires when the keyboard
 *     fully opens or fully closes, catching the final state in case the
 *     animation callback misses it.
 *
 * Returns:
 *   0           → keyboard is fully hidden
 *   positive    → keyboard is visible or currently animating (mid-slide)
 *
 * On Android 11+ (API 30+): height updates every frame during animation.
 * On older Android: jumps from 0 to full height immediately — still correct,
 *   just no smooth animation. The toolbar will snap instead of slide, which
 *   is the same behavior apps had before Android 11.
 *
 * Requires: android:windowSoftInputMode="adjustResize" in AndroidManifest.xml
 */
@Composable
fun rememberKeyboardHeightPx(): Int {
    val view = LocalView.current
    var keyboardHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {

        // Fires on every animation frame while keyboard is sliding in/out
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                return insets
            }
        }

        // Fires when keyboard fully opens or fully closes (catches final state)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(view, animationCallback)

        onDispose {
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    return keyboardHeight
}

/**
 * Convenience wrapper — returns true when keyboard is visible or animating in.
 * Drop-in replacement for the old rememberKeyboardVisibility().
 */
@Composable
fun rememberKeyboardVisibility(): Boolean = rememberKeyboardHeightPx() > 0
