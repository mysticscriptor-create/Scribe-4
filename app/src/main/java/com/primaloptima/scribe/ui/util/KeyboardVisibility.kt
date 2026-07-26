package com.primaloptima.scribe.ui.util

import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Returns true when the software keyboard is visible.
 * Works on all API levels without requiring edge-to-edge / setDecorFitsSystemWindows.
 * Uses ViewTreeObserver so it is compatible with adjustPan window mode.
 */
@Composable
fun rememberKeyboardVisibility(): Boolean {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val rect = Rect()
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            // Keyboard is considered visible if it occupies >15% of the screen height.
            isVisible = (screenHeight - rect.bottom) > screenHeight * 0.15f
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return isVisible
}
