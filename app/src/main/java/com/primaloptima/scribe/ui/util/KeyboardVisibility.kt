package com.primaloptima.scribe.ui.util

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Returns true when the software keyboard is visible.
 *
 * Uses ViewTreeObserver.OnPreDrawListener + WindowInsetsCompat.isVisible(ime()).
 * This is the recommended post-Accompanist approach:
 *  - Works on all API levels (no edge-to-edge / setDecorFitsSystemWindows required)
 *  - Compatible with adjustPan and adjustResize window modes
 *  - More reliable than the geometry-based approach (15% screen height threshold
 *    can misfire on foldables, tablets, and split-screen)
 *  - Synchronised to the draw pass via OnPreDrawListener, so it never fires
 *    a stale frame ahead of the actual keyboard animation
 */
@Composable
fun rememberKeyboardVisibility(): Boolean {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            val insets = ViewCompat.getRootWindowInsets(view)
            isVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            true // must return true so the draw proceeds
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
    }
    return isVisible
}
