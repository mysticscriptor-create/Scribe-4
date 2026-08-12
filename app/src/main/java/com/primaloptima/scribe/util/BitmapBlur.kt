package com.primaloptima.scribe.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.annotation.WorkerThread
import com.primaloptima.scribe.util.GrainTexture

/**
 * Pure-Kotlin bitmap blur — no RenderScript, no deprecated APIs.
 *
 * Two entry points:
 *
 *  • [blurBitmap]       — blur any existing bitmap (used for the static wallpaper).
 *  • [captureAndBlur]   — one-shot: captures the current screen content, crops to
 *                         [cropRect] (pass null for full screen), downscales 40 %,
 *                         blurs, then upscales back. Call from a background thread.
 *
 * Both functions are @WorkerThread — always call from Dispatchers.IO.
 *
 * Frosted-glass finish (chromatic grain) is applied as the FINAL step inside both
 * entry points so drawers, panels, dialogs and wallpapers only ever receive the
 * completed glassy result. Grain is sourced from [GrainTexture] when available,
 * with an inline chromatic LCG fallback for the first frame after cold start.
 */
object BitmapBlur {

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Blurs [src] using a stack-blur approximation of Gaussian.
     * Downscales to 40 % before blurring and upscales back — quality is identical
     * for frosted glass but ~6× faster than blurring full resolution.
     *
     * The frosted-glass look is applied as the last step.
     *
     * @param src     Source bitmap (ARGB_8888). Not recycled by this call.
     * @param radius  Blur radius 1–25 px (clamped).
     * @return        New blurred + frosted bitmap at the same size as [src].
     */
    @WorkerThread
    fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val scale = 0.40f
        val small = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val blurred = stackBlur(small, r)
        small.recycle()
        // Apply frosted-glass finish on the small bitmap — grain is a low-frequency
        // effect, visually identical to full-res but ~6× faster (40% scale = ~6×
        // fewer pixels). The bilinear upscale that follows softens the grain slightly,
        // which enhances the glassy feel.
        val frosted = applyFrostedGlassLook(blurred)
        val upscaled = Bitmap.createScaledBitmap(frosted, src.width, src.height, true)
        frosted.recycle()
        return upscaled
    }

    /**
     * Post-processes a blurred bitmap to look like real frosted glass rather than
     * a plain blur.
     *
     * Applies chromatic noise grain — the fine texture that distinguishes "glass"
     * from "blurry". Brightness lift has been removed: at 40% downscale the blur
     * already retains enough colour energy, and lifting dark wallpapers looks washed
     * out. Dark frosted glass should look dark; that is the correct behaviour.
     *
     * Grain source priority:
     *  1. [GrainTexture.get] — pre-baked bitmap composited via alpha-blend. Zero CPU
     *     per-pixel cost after first [GrainTexture.warmUp].
     *  2. Inline chromatic LCG fallback — used on the first drawer open before the
     *     pre-baked bitmap is ready, or after a screen rotation invalidates the cache.
     *     Seeds from [System.nanoTime] — never a static frozen pattern.
     *
     * Called automatically by [blurBitmap] and [captureAndBlur].
     *
     * @param src        Blurred bitmap (ARGB_8888). Modified in-place.
     * @param noiseAlpha Grain blend opacity (0f = none, 0.05f = subtle, 0.08f = visible).
     */
    @WorkerThread
    fun applyFrostedGlassLook(
        src: Bitmap,
        noiseAlpha: Float = 0.05f
    ): Bitmap {
        if (noiseAlpha <= 0f) return src

        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val noiseStrength = (noiseAlpha * 255).toInt().coerceIn(0, 30)
        val halfNoise = noiseStrength / 2

        val grainBitmap = GrainTexture.get(w, h)

        if (grainBitmap != null) {
            // Fast path: composite pre-baked chromatic grain via alpha blend.
            // Grain pixels are ARGB_8888 with R/G/B centred on 128 (128 = no offset).
            // We re-interpret them as signed offsets scaled by noiseAlpha.
            val grainPixels = IntArray(w * h)
            grainBitmap.getPixels(grainPixels, 0, w, 0, 0, w, h)

            for (i in pixels.indices) {
                val gp = grainPixels[i]
                // Signed offset: grain channel [0,255] → [-halfNoise, +halfNoise]
                val offsetR = (((gp shr 16) and 0xFF) * noiseStrength / 255) - halfNoise
                val offsetG = (((gp shr 8)  and 0xFF) * noiseStrength / 255) - halfNoise
                val offsetB = (( gp         and 0xFF) * noiseStrength / 255) - halfNoise

                val p = pixels[i]
                val a = (p shr 24) and 0xFF
                val r = ((p shr 16 and 0xFF) + offsetR).coerceIn(0, 255)
                val g = ((p shr 8  and 0xFF) + offsetG).coerceIn(0, 255)
                val b = ((p        and 0xFF) + offsetB).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        } else {
            // Fallback: inline chromatic LCG. Runs when GrainTexture is not yet ready
            // (first draw after cold start / rotation). Three independent seeds ensure
            // R, G, B channels are uncorrelated, giving subtle colour-cast grain.
            var seedR = System.nanoTime()
            var seedG = seedR xor 0xDEADBEEFL
            var seedB = seedR xor 0xCAFEBABEL

            for (i in pixels.indices) {
                seedR = (seedR * 1664525L + 1013904223L) and 0xFFFFFFFFL
                seedG = (seedG * 1664525L + 1013904223L) and 0xFFFFFFFFL
                seedB = (seedB * 1664525L + 1013904223L) and 0xFFFFFFFFL

                val offsetR = (((seedR shr 16) and 0xFFL).toInt() * noiseStrength / 255) - halfNoise
                val offsetG = (((seedG shr 16) and 0xFFL).toInt() * noiseStrength / 255) - halfNoise
                val offsetB = (((seedB shr 16) and 0xFFL).toInt() * noiseStrength / 255) - halfNoise

                val p = pixels[i]
                val a = (p shr 24) and 0xFF
                val r = ((p shr 16 and 0xFF) + offsetR).coerceIn(0, 255)
                val g = ((p shr 8  and 0xFF) + offsetG).coerceIn(0, 255)
                val b = ((p        and 0xFF) + offsetB).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        src.setPixels(pixels, 0, w, 0, 0, w, h)
        return src
    }

    /**
     * One-shot screen capture + blur for pre-Android-12 frosted glass on panels/dialogs/drawers.
     *
     * Captures the current window content, optionally crops to [cropRect], downscales
     * to 40 %, blurs with [radius], upscales back, then applies the frosted-glass finish
     * as the final step. Callers only ever receive the completed glassy bitmap.
     *
     * @param view      Any view in the target activity window (used to get the window).
     * @param cropRect  Region to capture in screen pixels, or null for full screen.
     * @param radius    Blur radius 1–25 (clamped). Default 15 matches app default.
     * @return          Blurred + frosted bitmap at [cropRect] size (or full screen), or null on error.
     */
    @WorkerThread
    fun captureAndBlur(
        view: View,
        cropRect: Rect? = null,
        radius: Int = 15
    ): Bitmap? {
        return try {
            val r = radius.coerceIn(1, 25)

            // Capture the whole window into a software bitmap
            val full = captureOnly(view) ?: return null

            // Crop to the desired region
            val cropped = if (cropRect != null) {
                val safeRect = Rect(
                    cropRect.left.coerceAtLeast(0),
                    cropRect.top.coerceAtLeast(0),
                    cropRect.right.coerceAtMost(full.width),
                    cropRect.bottom.coerceAtMost(full.height)
                )
                if (safeRect.isEmpty) {
                    full.recycle()
                    return null
                }
                val c = Bitmap.createBitmap(full, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
                full.recycle()
                c
            } else {
                full
            }

            // Save dimensions before recycling
            val targetW = cropped.width
            val targetH = cropped.height

            // Downscale to 40 % — frosted glass is low-frequency, quality is identical
            // at this scale but ~6× fewer pixels than full resolution.
            val scale = 0.40f
            val small = Bitmap.createScaledBitmap(
                cropped,
                (targetW * scale).toInt().coerceAtLeast(1),
                (targetH * scale).toInt().coerceAtLeast(1),
                true
            )
            cropped.recycle()

            // Blur on the small bitmap — very fast even on old hardware
            val blurred = stackBlur(small, r)
            small.recycle()

            // Apply frosted-glass finish before upscaling.
            // At 40% scale this is ~6× fewer pixel operations than full resolution.
            // The bilinear upscale softens the grain slightly, enhancing the glassy look.
            val frosted = applyFrostedGlassLook(blurred)

            // Upscale back with bilinear filtering
            val upscaled = Bitmap.createScaledBitmap(frosted, targetW, targetH, true)
            frosted.recycle()
            upscaled
        } catch (_: Exception) {
            null
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Captures the given view's root window into a software Bitmap.
     * Must be called on the MAIN thread (View drawing is not thread-safe).
     * After capturing, pass the result to [blurBitmap] on a background thread.
     */
    fun captureOnly(view: View): Bitmap? {
        return try {
            val w = view.rootView.width.coerceAtLeast(1)
            val h = view.rootView.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            view.rootView.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pure Kotlin stack blur (Zhu/Rijnders algorithm).
     * Significantly smoother than box blur at the same radius.
     * Operates on a copy of [src]; does not recycle [src].
     */
    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val div = radius + radius + 1
        val wm = w - 1
        val hm = h - 1
        val divSum = (div + 1) shr 1
        val divSum2 = divSum * divSum

        // Pre-compute reciprocal table to avoid division in hot loop
        val dv = IntArray(256 * divSum2)
        for (i in dv.indices) dv[i] = i / divSum2

        var yw = 0
        var yi = 0

        val stack = IntArray(div)
        var stackPointer: Int
        var stackStart: Int
        var sir: Int
        var rbs: Int
        val r1 = radius + 1

        var rSum: Int; var gSum: Int; var bSum: Int
        var rOutSum: Int; var gOutSum: Int; var bOutSum: Int
        var rInSum: Int; var gInSum: Int; var bInSum: Int

        // Horizontal pass
        for (y in 0 until h) {
            rInSum = 0; gInSum = 0; bInSum = 0
            rOutSum = 0; gOutSum = 0; bOutSum = 0
            rSum = 0; gSum = 0; bSum = 0

            for (i in -radius..radius) {
                sir = pixels[yi + i.coerceIn(0, wm)]
                stack[i + radius] = sir
                rbs = r1 - Math.abs(i)
                rSum += ((sir shr 16) and 0xFF) * rbs
                gSum += ((sir shr 8) and 0xFF) * rbs
                bSum += (sir and 0xFF) * rbs
                if (i > 0) {
                    rInSum += (sir shr 16) and 0xFF
                    gInSum += (sir shr 8) and 0xFF
                    bInSum += sir and 0xFF
                } else {
                    rOutSum += (sir shr 16) and 0xFF
                    gOutSum += (sir shr 8) and 0xFF
                    bOutSum += sir and 0xFF
                }
            }
            stackPointer = radius

            for (x in 0 until w) {
                pixels[yi] = (0xFF shl 24) or
                        (dv[rSum] shl 16) or
                        (dv[gSum] shl 8) or
                        dv[bSum]
                rSum -= rOutSum; gSum -= gOutSum; bSum -= bOutSum
                stackStart = (stackPointer - radius + div) % div
                sir = stack[stackStart]
                rOutSum -= (sir shr 16) and 0xFF
                gOutSum -= (sir shr 8) and 0xFF
                bOutSum -= sir and 0xFF
                val nextX = if (x + radius + 1 <= wm) yi + radius + 1 else yi + (wm - x)
                sir = pixels[nextX]
                stack[stackStart] = sir
                rInSum += (sir shr 16) and 0xFF
                gInSum += (sir shr 8) and 0xFF
                bInSum += sir and 0xFF
                rSum += rInSum; gSum += gInSum; bSum += bInSum
                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer]
                rOutSum += (sir shr 16) and 0xFF
                gOutSum += (sir shr 8) and 0xFF
                bOutSum += sir and 0xFF
                rInSum -= (sir shr 16) and 0xFF
                gInSum -= (sir shr 8) and 0xFF
                bInSum -= sir and 0xFF
                yi++
            }
            yw += w
            yi = yw
        }

        // Vertical pass
        for (x in 0 until w) {
            rInSum = 0; gInSum = 0; bInSum = 0
            rOutSum = 0; gOutSum = 0; bOutSum = 0
            rSum = 0; gSum = 0; bSum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = (yp + x).coerceAtLeast(x)
                sir = pixels[yi]
                stack[i + radius] = sir
                rbs = r1 - Math.abs(i)
                rSum += ((sir shr 16) and 0xFF) * rbs
                gSum += ((sir shr 8) and 0xFF) * rbs
                bSum += (sir and 0xFF) * rbs
                if (i > 0) {
                    rInSum += (sir shr 16) and 0xFF
                    gInSum += (sir shr 8) and 0xFF
                    bInSum += sir and 0xFF
                } else {
                    rOutSum += (sir shr 16) and 0xFF
                    gOutSum += (sir shr 8) and 0xFF
                    bOutSum += sir and 0xFF
                }
                if (i < hm) yp += w
            }
            yi = x
            stackPointer = radius
            for (y in 0 until h) {
                pixels[yi] = (0xFF shl 24) or
                        (dv[rSum] shl 16) or
                        (dv[gSum] shl 8) or
                        dv[bSum]
                rSum -= rOutSum; gSum -= gOutSum; bSum -= bOutSum
                stackStart = (stackPointer - radius + div) % div
                sir = stack[stackStart]
                rOutSum -= (sir shr 16) and 0xFF
                gOutSum -= (sir shr 8) and 0xFF
                bOutSum -= sir and 0xFF
                val nextY = if (y + r1 <= hm) yi + r1 * w else yi + (hm - y) * w
                sir = pixels[nextY]
                stack[stackStart] = sir
                rInSum += (sir shr 16) and 0xFF
                gInSum += (sir shr 8) and 0xFF
                bInSum += sir and 0xFF
                rSum += rInSum; gSum += gInSum; bSum += bInSum
                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer]
                rOutSum += (sir shr 16) and 0xFF
                gOutSum += (sir shr 8) and 0xFF
                bOutSum += sir and 0xFF
                rInSum -= (sir shr 16) and 0xFF
                gInSum -= (sir shr 8) and 0xFF
                bInSum -= sir and 0xFF
                yi += w
            }
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
