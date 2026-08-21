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
 * DOCUMENTBUFFER API CONTRACT (Researched August 2026):
 *   - DocumentBuffer.lineCount(): Int returns total line count based on lineStartsCache.size.
 *   - DocumentBuffer.lineStart(index: Int): Int returns the starting document character offset.
 *   - DocumentBuffer.lineLength(index: Int): Int returns character length excluding trailing newline.
 *   - DocumentBuffer.lineContent(index: Int): String returns line substring.
 *   - DocumentBuffer has no granular dirtyLines() / changedSince() tracking, so lineCache.clear()
 *     is used on snapshot commits, which is O(visible_lines) on redraw.
 *
 * CACHE KEY:
 *   Int derived as: lineText.hashCode() * 31 xor spans.hashCode()
 *   Pure Kotlin, no Android deps, testable on JVM.
 *
 * EVICTION:
 *   Max 512 entries. When exceeded, the entry with the lowest line index is evicted
 *   (it has scrolled furthest off-screen and is least likely to be needed soon).
 */
package com.primaloptima.scribe.ui.editor

import androidx.compose.ui.text.TextLayoutResult

data class CachedLine(
    val contentHash: Int,
    val result: TextLayoutResult,
)

class ScribeLineCache {

    private val maxCapacity = 512
    // LinkedHashMap with accessOrder=false (insertion order) is fine here because we
    // evict by key (lineIndex), not by access recency. We want to evict the smallest
    // lineIndex (furthest above viewport) when over capacity.
    private val map = LinkedHashMap<Int, CachedLine>(maxCapacity + 1)

    /** Returns a cached result only if contentHash still matches. null = stale or absent. */
    fun get(lineIndex: Int, contentHash: Int): TextLayoutResult? {
        val entry = map[lineIndex] ?: return null
        return if (entry.contentHash == contentHash) entry.result else null
    }

    fun put(lineIndex: Int, contentHash: Int, result: TextLayoutResult) {
        map[lineIndex] = CachedLine(contentHash, result)
        if (map.size > maxCapacity) {
            // Evict the entry with the smallest (oldest, furthest above viewport) key.
            val minKey = map.keys.minOrNull()
            if (minKey != null) {
                map.remove(minKey)
            }
        }
    }

    /** Removes a single line — call after a single-line in-place edit. */
    fun invalidateLine(lineIndex: Int) {
        map.remove(lineIndex)
    }

    /**
     * Removes all entries with key >= lineIndex.
     * Call when an insert or delete shifts line indices downward,
     * because every line after the change point has a different lineIndex mapping.
     */
    fun invalidateFrom(lineIndex: Int) {
        val keysToRemove = map.keys.filter { it >= lineIndex }
        for (key in keysToRemove) {
            map.remove(key)
        }
    }

    /** Full clear — call on document text mutations or new document load. */
    fun clear() {
        map.clear()
    }
}

/** Content hash: pure Kotlin, no Android deps. */
fun contentHash(lineText: String, spans: List<Any>): Int =
    lineText.hashCode() * 31 xor spans.hashCode()
