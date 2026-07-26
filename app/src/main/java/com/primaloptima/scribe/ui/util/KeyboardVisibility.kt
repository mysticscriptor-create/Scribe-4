package com.primaloptima.scribe.ui.util

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

/**
 * Returns true when the software keyboard is visible.
 *
 * Uses a two-layer approach for maximum reliability across all Android versions:
 *
 * Layer 1 — geometry (OnPreDrawListener + getWindowVisibleDisplayFrame):
 *   Works reliably with adjustResize on all API levels.
 *   Threshold is 15% of screen height, which safely separates:
 *     - navigation bar alone (~6-10%)  → false
 *     - keyboard present (~35-55%)     → true
 *
 * Layer 2 — global focus change (OnGlobalFocusChangeListener):
 *   Backup for edge cases where the geometry misses the keyboard.
 *   When a view gains focus the keyboard is very likely coming up.
 *   When nothing has focus it double-checks with geometry.
 *
 * Requires: android:windowSoftInputMode="adjustResize" in AndroidManifest.xml
 */
@Composable
fun rememberKeyboardVisibility(): Boolean {
    val view = LocalView.current
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val rect = Rect()

        // Layer 1: geometry — reliable on adjustResize
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keyboardHeight = screenHeight - rect.bottom
            isVisible = keyboardHeight > screenHeight * 0.15f
            true
        }

        // Layer 2: focus — catches edge cases on some devices
        val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && newFocus.isShown) {
                isVisible = true
            } else if (newFocus == null) {
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
