package com.primaloptima.scribe.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onLayoutRectChanged

/**
 * Calculates perceived luminance for the portion of the analysis bitmap behind
 * a composable. The bitmap is intentionally tiny, so this stays inexpensive
 * even when several labels are tracked in a scrolling screen.
 */
fun regionLuminance(
    bitmap: Bitmap,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float
): Double {
    // Hardware bitmaps live on the GPU and don't support getPixel() — bail safely.
    if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) return 0.5
    if (bitmap.width == 0 || bitmap.height == 0 || screenWidthPx <= 0f || screenHeightPx <= 0f) {
        return 0.5
    }

    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val x0 = ((screenRect.left / screenWidthPx) * bw).toInt().coerceIn(0, bitmap.width - 1)
    val y0 = ((screenRect.top / screenHeightPx) * bh).toInt().coerceIn(0, bitmap.height - 1)
    val x1 = ((screenRect.right / screenWidthPx) * bw).toInt().coerceIn(x0, bitmap.width - 1)
    val y1 = ((screenRect.bottom / screenHeightPx) * bh).toInt().coerceIn(y0, bitmap.height - 1)

    var total = 0.0
    var count = 0
    for (x in x0..x1) {
        for (y in y0..y1) {
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel) / 255.0
            val green = android.graphics.Color.green(pixel) / 255.0
            val blue = android.graphics.Color.blue(pixel) / 255.0
            total += 0.2126 * red + 0.7152 * green + 0.0722 * blue
            count++
        }
    }
    return if (count == 0) 0.5 else total / count
}

fun contrastingTextColor(
    bitmap: Bitmap?,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFF1A1A1A)
): Color {
    if (bitmap == null || screenRect.isEmpty) return lightColor
    return if (regionLuminance(bitmap, screenRect, screenWidthPx, screenHeightPx) < 0.45) {
        lightColor
    } else {
        darkColor
    }
}

@Composable
fun rememberAdaptiveTextColor(
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFF1A1A1A),
    fallback: Color = Color.Unspecified
): Pair<Color, Modifier> {
    val bitmap = LocalBgAnalysisBitmap.current
    val (screenW, screenH) = LocalScreenSize.current
    if (bitmap == null) return Pair(fallback, Modifier)

    var bounds by remember { mutableStateOf(Rect.Zero) }
    val color by remember(bounds, bitmap, screenW, screenH, lightColor, darkColor) {
        derivedStateOf {
            contrastingTextColor(bitmap, bounds, screenW, screenH, lightColor, darkColor)
        }
    }
    val trackingModifier = Modifier.onLayoutRectChanged(
        debounceMillis = 150,
        throttleMillis = 0
    ) { layoutBounds ->
        val intRect = layoutBounds.boundsInRoot
        val newBounds = Rect(intRect.left.toFloat(), intRect.top.toFloat(), intRect.right.toFloat(), intRect.bottom.toFloat())
        if (
            kotlin.math.abs(newBounds.left - bounds.left) > 2f ||
            kotlin.math.abs(newBounds.top - bounds.top) > 2f
        ) {
            bounds = newBounds
        }
    }
    return Pair(color, trackingModifier)
}
