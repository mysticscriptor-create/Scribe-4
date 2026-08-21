package com.primaloptima.scribe.ui.editor

import androidx.compose.ui.text.TextLayoutResult

data class CachedLine(
    val revision: Int,
    val searchVersion: Int,
    val lineStart: Int,
    val lineLength: Int,
    val width: Int,
    val result: TextLayoutResult,
)

/**
 * Ultra-high-performance zero-allocation per-line TextLayoutResult cache.
 * Keyed on lineIndex + document revision + search version + line dimensions.
 * During 120fps scrolling, cache hits are verified by checking primitive ints with zero GC allocations.
 */
class ScribeLineCache(private val maxCapacity: Int = 1024) {
    private val map = LinkedHashMap<Int, CachedLine>(maxCapacity + 1, 0.75f, true)

    fun get(
        lineIndex: Int,
        revision: Int,
        searchVersion: Int,
        lineStart: Int,
        lineLength: Int,
        width: Int
    ): TextLayoutResult? {
        val entry = map[lineIndex] ?: return null
        if (entry.revision == revision &&
            entry.searchVersion == searchVersion &&
            entry.lineStart == lineStart &&
            entry.lineLength == lineLength &&
            entry.width == width
        ) {
            return entry.result
        }
        return null
    }

    fun put(
        lineIndex: Int,
        revision: Int,
        searchVersion: Int,
        lineStart: Int,
        lineLength: Int,
        width: Int,
        result: TextLayoutResult
    ) {
        map[lineIndex] = CachedLine(revision, searchVersion, lineStart, lineLength, width, result)
        if (map.size > maxCapacity) {
            val iterator = map.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun clear() {
        map.clear()
    }
}
