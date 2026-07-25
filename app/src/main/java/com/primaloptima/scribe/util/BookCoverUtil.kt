package com.primaloptima.scribe.util

import android.graphics.drawable.GradientDrawable
import kotlin.math.abs

/**
 * Generates warm, literary-themed gradient covers for books that don't have a user-set photo.
 */
object BookCoverUtil {

    // Pairs of (top-colour, bottom-colour) — all desaturated and literary-feeling
    private val PALETTE = arrayOf(
        intArrayOf(0xFF8B4513.toInt(), 0xFFA8651E.toInt()), // Burnt sienna → amber (brand)
        intArrayOf(0xFF2C4A6E.toInt(), 0xFF3A6B9E.toInt()), // Ink navy
        intArrayOf(0xFF4A2040.toInt(), 0xFF7A3D6B.toInt()), // Plum
        intArrayOf(0xFF1A4A2E.toInt(), 0xFF2E7A4C.toInt()), // Forest
        intArrayOf(0xFF4A1818.toInt(), 0xFF7A2E2E.toInt()), // Crimson
        intArrayOf(0xFF3A3A1A.toInt(), 0xFF626235.toInt()), // Olive
        intArrayOf(0xFF1E3A4A.toInt(), 0xFF2E6080.toInt()), // Deep teal
        intArrayOf(0xFF4A3A1A.toInt(), 0xFF7A5E28.toInt()), // Bronze
        intArrayOf(0xFF2A2A4A.toInt(), 0xFF404070.toInt()), // Midnight indigo
        intArrayOf(0xFF3A1A1A.toInt(), 0xFF6B2E1A.toInt()), // Espresso
    )

    /** Create a gradient drawable whose colours are deterministically derived from [title]. */
    fun makeGradient(title: String): GradientDrawable {
        val idx = abs(title.hashCode()) % PALETTE.size
        val (top, bot) = PALETTE[idx]
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot))
    }

    /** Returns the first alphanumeric character of [title] in upper-case, or "?" if empty. */
    fun initial(title: String): String =
        title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}
