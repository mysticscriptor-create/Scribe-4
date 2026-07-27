package com.primaloptima.scribe.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.WorkerThread

/**
 * Software bitmap blur for Android < 12 (API < 31).
 *
 * Strategy:
 *  - API 17–30: Use RenderScript (deprecated but available). Fast GPU-assisted blur.
 *  - Fallback:  Pure-Java box blur. Slower but works everywhere.
 *
 * Call [blurBitmap] on a background thread (it's annotated @WorkerThread).
 * The input bitmap is NOT recycled; the caller owns both bitmaps.
 *
 * Typical usage in Compose:
 *   val blurred = remember(uri, radius) {
 *       // run in LaunchedEffect / produceState
 *       BitmapBlur.blurBitmap(context, sourceBitmap, radius)
 *   }
 */
object BitmapBlur {

    /**
     * @param context  Any context — used only to create a RenderScript instance.
     * @param src      Source bitmap. Must be ARGB_8888. Not recycled by this call.
     * @param radius   Blur radius in pixels, 1–25. Values outside this range are clamped.
     * @return         A new blurred bitmap.
     */
    @WorkerThread
    fun blurBitmap(context: Context, src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        // Downscale for performance — blur on a smaller bitmap then upscale
        val scale = 0.4f
        val small = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            renderScriptBlur(context, small, r) ?: javaBoxBlur(small, r)
        } else {
            javaBoxBlur(small, r)
        }
        // Upscale back to original size
        return Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
            .also { if (blurred !== small) blurred.recycle(); small.recycle() }
    }

    @Suppress("DEPRECATION")
    private fun renderScriptBlur(context: Context, src: Bitmap, radius: Int): Bitmap? {
        return try {
            val rs = android.renderscript.RenderScript.create(context)
            val input = android.renderscript.Allocation.createFromBitmap(
                rs, src,
                android.renderscript.Allocation.MipmapControl.MIPMAP_NONE,
                android.renderscript.Allocation.USAGE_SCRIPT
            )
            val output = android.renderscript.Allocation.createTyped(rs, input.type)
            val blur = android.renderscript.ScriptIntrinsicBlur.create(
                rs, android.renderscript.Element.U8_4(rs)
            )
            blur.setRadius(radius.toFloat())
            blur.setInput(input)
            blur.forEach(output)
            val result = src.copy(Bitmap.Config.ARGB_8888, true)
            output.copyTo(result)
            rs.destroy()
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Simple 3-pass box blur. Not as smooth as Gaussian but very fast for
     * pre-blurred static backgrounds.
     */
    private fun javaBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) { boxBlurPass(pixels, w, h, radius) }
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = pixels.copyOf()
        val div = 2 * radius + 1

        // Horizontal pass
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (k in -radius..radius) {
                val px = tmp[y * w + k.coerceIn(0, w - 1)]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (x in 0 until w) {
                pixels[y * w + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                val addIdx = (x + radius + 1).coerceAtMost(w - 1)
                val removeIdx = (x - radius).coerceAtLeast(0)
                val addPx = tmp[y * w + addIdx]
                val removePx = tmp[y * w + removeIdx]
                rSum += ((addPx shr 16) and 0xFF) - ((removePx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((removePx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (removePx and 0xFF)
            }
        }

        val tmp2 = pixels.copyOf()
        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (k in -radius..radius) {
                val px = tmp2[k.coerceIn(0, h - 1) * w + x]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                val addIdx = (y + radius + 1).coerceAtMost(h - 1)
                val removeIdx = (y - radius).coerceAtLeast(0)
                val addPx = tmp2[addIdx * w + x]
                val removePx = tmp2[removeIdx * w + x]
                rSum += ((addPx shr 16) and 0xFF) - ((removePx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((removePx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (removePx and 0xFF)
            }
        }
    }
}
