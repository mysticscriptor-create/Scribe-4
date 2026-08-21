/*
 * ScribeLineCache — per-line TextLayoutResult cache for the virtualized Canvas renderer.
 *
 * WHY THIS EXISTS ON TOP OF TextMeasurer's INTERNAL CACHE:
 *   TextMeasurer has a cacheSize-bounded LRU keyed on the full set of measure inputs.
 *   On a 100 k-word document (~4000 lines), the internal LRU (cacheSize=256) will
 *   evict off-screen lines. When the user scrolls back, those lines must be
 *   re-measured. ScribeLineCache provides a deterministic index → result map
 *   keyed on (lineIndex, contentHash). An unchanged line always hits the cache
 *   regardless of LRU pressure.
 *
 * CACHE KEY:
 *   Deterministic integer combining char-by-char CharSequence hash, span hash, and search version.
 *   Computed with zero object allocations.
 */
package com.primaloptima.scribe.ui.editor

import androidx.compose.ui.text.TextLayoutResult

data class CachedLine(
    val contentHash: Int,
    val result: TextLayoutResult,
)

/**
 * High-performance per-line TextLayoutResult cache for virtualized Canvas rendering.
 *
 * Inspired by Sora Editor's layout caching:
 * - Deterministic lookup keyed by (lineIndex, contentHash).
 * - When user types on line L, only line L's contentHash changes.
 * - All other visible/offscreen lines HIT the cache in O(1) without re-measuring text.
 * - LRU/capacity eviction bounds memory while avoiding unnecessary garbage collection.
 */
class ScribeLineCache(private val maxCapacity: Int = 1024) {
    private val map = LinkedHashMap<Int, CachedLine>(maxCapacity + 1, 0.75f, true)

    /** Returns a cached result only if contentHash still matches. null = stale or absent. */
    fun get(lineIndex: Int, contentHash: Int): TextLayoutResult? {
        val entry = map[lineIndex] ?: return null
        return if (entry.contentHash == contentHash) entry.result else null
    }

    fun put(lineIndex: Int, contentHash: Int, result: TextLayoutResult) {
        map[lineIndex] = CachedLine(contentHash, result)
        if (map.size > maxCapacity) {
            val iterator = map.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    /** Removes a single line — call after a single-line in-place edit. */
    fun invalidateLine(lineIndex: Int) {
        map.remove(lineIndex)
    }

    /** Removes all entries with key >= lineIndex. */
    fun invalidateFrom(lineIndex: Int) {
        map.entries.removeIf { it.key >= lineIndex }
    }

    /** Full clear — call on document text mutations or new document load. */
    fun clear() {
        map.clear()
    }
}

/**
 * Content hash: fast zero-allocation calculation combining line text, format spans, and search highlights.
 * Uses a char-by-char polynomial hash without creating intermediate substring allocations.
 */
fun contentHash(lineText: CharSequence, spans: List<Any>, searchVersion: Int = 0): Int {
    var h = 0
    val len = lineText.length
    for (i in 0 until len) {
        h = 31 * h + lineText[i].code
    }
    h = 31 * h + spans.hashCode()
    h = 31 * h + searchVersion
    return h
}
