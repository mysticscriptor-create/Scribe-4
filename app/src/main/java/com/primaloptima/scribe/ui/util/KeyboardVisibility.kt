package com.primaloptima.scribe.ui.util

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

/**
 * Returns true when the software keyboard is visible.
 *
 * Uses a two-layer approach for maximum reliability across all Android versions
 * and windowSoftInputMode settings (adjustResize, adjustPan, adjustNothing):
 *
 * Layer 1 — geometry (OnPreDrawListener + getWindowVisibleDisplayFrame):
 *   Works perfectly with adjustResize on all API levels.
 *   Threshold lowered to 15% to catch more edge cases without false positives
 *   from the navigation bar alone (~6-10%).
 *
 * Layer 2 — global focus change (OnGlobalFocusChangeListener):
 *   Catches cases where the geometry method misses the keyboard (e.g. on some
 *   devices still using adjustPan, or on Android 15 edge-to-edge enforcement).
 *   When a view gains focus, the keyboard is very likely coming up.
 *   When no view has focus, the keyboard is very likely going down.
 */
@Composable
fun rememberKeyboardVisibility(): Boolean {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val rect = Rect()

        // Layer 1: geometry — reliable on adjustResize (our main mode)
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keyboardHeight = screenHeight - rect.bottom
            isVisible = keyboardHeight > screenHeight * 0.15f
            true
        }

        // Layer 2: focus — catches adjustPan gaps and Android 15 edge cases
        val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && newFocus.isShown) {
                // A view just gained focus — keyboard is likely opening
                isVisible = true
            } else if (newFocus == null) {
                // Nothing has focus — keyboard is likely closed
                // Let the geometry layer confirm on next pre-draw
                view.getWindowVisibleDisplayFrame(rect)
                val screenHeight = view.rootView.height
                isVisible = (screenHeight - rect.bottom) > screenHeight * 0.15f
            }
        }

        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        view.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            view.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        }
    }
    return isVisible
}
