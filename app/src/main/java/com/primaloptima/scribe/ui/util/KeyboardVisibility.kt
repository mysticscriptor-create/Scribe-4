package com.primaloptima.scribe.ui.util

import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

/**
 * Returns true when the software keyboard is visible.
 *
 * Uses ViewTreeObserver.OnPreDrawListener + getWindowVisibleDisplayFrame().
 * This geometry approach works on all API levels with ANY windowSoftInputMode
 * (adjustPan, adjustResize, or adjustNothing) and requires no edge-to-edge /
 * setDecorFitsSystemWindows setup.
 *
 * The threshold (25% of screen height) safely distinguishes:
 *   - navigation bar alone (~6-10%)  → isVisible = false
 *   - keyboard + optional nav bar (~35-55%) → isVisible = true
 */
@Composable
fun rememberKeyboardVisibility(): Boolean {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val rect = Rect()
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            isVisible = (screenHeight - rect.bottom) > screenHeight * 0.25f
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
    }
    return isVisible
}
