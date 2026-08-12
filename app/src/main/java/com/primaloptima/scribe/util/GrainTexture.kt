package com.primaloptima.scribe.util

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pre-baked chromatic noise grain texture for frosted-glass overlays.
 *
 * Generating grain per-pixel inside [BitmapBlur.applyFrostedGlassLook] every drawer
 * open costs CPU time on old hardware. This singleton bakes the grain once at app
 * start and caches it so every subsequent blur composites a pre-built bitmap instead
 * of running a hot per-pixel loop.
 *
 * Key design decisions
 * ─────────────────────
 *  • **Chromatic noise** — R, G, and B channels each get an independent LCG seed.
 *    Individual pixels shift slightly red, green, or blue rather than pure grey.
 *    This matches real film grain and real frosted glass far better than luminance-
 *    only noise.
 *
 *  • **Session-random seed** — seeded from [System.nanoTime] so the pattern is
 *    different every cold start. The grain never looks like a frozen stamp.
 *
 *  • **Screen-sized bitmap** — sized to the full screen so it can be drawn directly
 *    over any blur target without tiling or transformation.
 *
 *  • **Regenerates on resize** — if the screen size changes (rotation, foldables),
 *    [warmUp] regenerates silently in the background. The old texture is used until
 *    the new one is ready.
 *
 *  • **Null-safe** — [get] returns null if not yet ready. [BitmapBlur] falls back to
 *    an inline chromatic LCG pass so there is never a visual regression.
 *
 * Usage
 * ──────
 * Call [warmUp] once from your first HomeScreen composition (or Application.onCreate).
 * Then pass [get] results to [BitmapBlur.applyFrostedGlassLook].
 */
object GrainTexture {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cached: Bitmap? = null
    @Volatile private var cachedWidth: Int = 0
    @Volatile private var cachedHeight: Int = 0

    /**
     * Returns the cached grain bitmap if it matches [width] × [height], or null if
     * it is not yet ready or the dimensions have changed.
     *
     * The bitmap is ARGB_8888. Each pixel's RGB channels encode a signed noise offset
     * centred on 128 (i.e. 128 = no offset, <128 = darken, >128 = brighten). The
     * alpha channel is 255 (fully opaque); callers control blend opacity via Paint or
     * Compose alpha.
     */
    fun get(width: Int, height: Int): Bitmap? {
        val bmp = cached ?: return null
        return if (bmp.width == width && bmp.height == height) bmp else null
    }

    /**
     * Triggers async generation of a grain bitmap at [width] × [height].
     *
     * Safe to call multiple times — re-generates only if dimensions differ from the
     * current cache. No-ops if dimensions already match.
     *
     * Call from the main thread (e.g. a LaunchedEffect or Application.onCreate).
     * Generation runs on [Dispatchers.IO].
     *
     * @param width   Target bitmap width in pixels (typically screen width).
     * @param height  Target bitmap height in pixels (typically screen height).
     * @param noiseStrength  Peak per-channel noise magnitude 0–30 px. Default 13
     *                       (≈ 5 % of 255) is visibly grainy without being distracting.
     */
    fun warmUp(width: Int, height: Int, noiseStrength: Int = 13) {
        if (width <= 0 || height <= 0) return
        // Already have the right size — nothing to do.
        if (cached != null && cachedWidth == width && cachedHeight == height) return
        scope.launch {
            val bmp = generate(width, height, noiseStrength)
            // Write dimensions before publishing the bitmap. If another thread reads
            // `cached != null` between these two lines it will see width/height = 0
            // only in a tiny window; the worst outcome is one redundant re-generate
            // on the next warmUp call, not a crash or stale bitmap.
            // We write the new bitmap last so `get()` never returns a bitmap whose
            // dimensions don't match the already-updated width/height fields.
            cachedWidth = width
            cachedHeight = height
            cached = bmp
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────────

    /**
     * Generates a chromatic grain bitmap. Runs on a background thread.
     *
     * Each pixel is an ARGB value where:
     *  • A = 255 (always fully opaque; callers blend via alpha)
     *  • R, G, B = 128 + independent noise offset (range 128 ± noiseStrength/2)
     *
     * The three channels use LCG generators with distinct starting seeds derived
     * from [System.nanoTime], ensuring no correlation between colour channels and
     * a different pattern every session.
     */
    @WorkerThread
    private fun generate(width: Int, height: Int, noiseStrength: Int): Bitmap {
        val pixels = IntArray(width * height)
        val half = noiseStrength / 2

        // Three independent LCG seeds — offset by large primes so channels are uncorrelated.
        val base = System.nanoTime()
        var seedR = base xor -7046029254386353131L
        var seedG = base xor -4658895341759072839L
        var seedB = base xor -7723592293110705909L

        for (i in pixels.indices) {
            // LCG step — same multiplier/increment as Numerical Recipes.
            seedR = (seedR * 1664525L + 1013904223L) and 0xFFFFFFFFL
            seedG = (seedG * 1664525L + 1013904223L) and 0xFFFFFFFFL
            seedB = (seedB * 1664525L + 1013904223L) and 0xFFFFFFFFL

            // Map [0, 0xFF] → [128 - half, 128 + half] for each channel independently.
            val r = 128 + (((seedR shr 16) and 0xFFL).toInt() * noiseStrength / 255) - half
            val g = 128 + (((seedG shr 16) and 0xFFL).toInt() * noiseStrength / 255) - half
            val b = 128 + (((seedB shr 16) and 0xFFL).toInt() * noiseStrength / 255) - half

            pixels[i] = (0xFF shl 24) or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
