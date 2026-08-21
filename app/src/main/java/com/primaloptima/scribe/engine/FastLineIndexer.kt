package com.primaloptima.scribe.engine

/**
 * High-performance, zero-allocation line indexer inspired by Sora Editor's Piece/Line Index.
 * Scans and mutates CharSequence indices directly without creating intermediate string allocations or boxed objects.
 *
 * Supports O(1) queries, O(lines) intra-line shifts, and single-line split/merge operations.
 */
class FastLineIndexer {
    private var lineStarts = IntArray(512) { 0 }
    var lineCount: Int = 1
        private set
    private var docLength: Int = 0

    /**
     * Fast full scan over [text].
     * Used for initial document load, large multi-line pastes, and undo history restorations.
     */
    fun index(text: CharSequence) {
        docLength = text.length
        var count = 1
        if (lineStarts.isEmpty()) lineStarts = IntArray(512)
        lineStarts[0] = 0
        val len = text.length
        var i = 0
        while (i < len) {
            if (text[i] == '\n') {
                if (count >= lineStarts.size) {
                    lineStarts = lineStarts.copyOf(lineStarts.size * 2)
                }
                lineStarts[count++] = i + 1
            }
            i++
        }
        this.lineCount = count
    }

    /**
     * Incremental in-place shift for intra-line edits (standard typing / deletion within a single line).
     * Avoids full re-scanning by shifting offsets of subsequent lines.
     */
    fun updateIntraLine(lineIndex: Int, deltaLength: Int) {
        if (deltaLength == 0 || lineIndex < 0 || lineIndex >= lineCount) return
        for (i in (lineIndex + 1) until lineCount) {
            lineStarts[i] += deltaLength
        }
        docLength = (docLength + deltaLength).coerceAtLeast(0)
    }

    /**
     * Incremental line split when a newline is inserted at [splitOffset] on line [lineIndex].
     */
    fun splitLine(lineIndex: Int, splitOffset: Int) {
        if (lineIndex < 0 || lineIndex >= lineCount) return
        ensureCapacity(lineCount + 1)
        val insertIdx = lineIndex + 1
        if (insertIdx < lineCount) {
            System.arraycopy(lineStarts, insertIdx, lineStarts, insertIdx + 1, lineCount - insertIdx)
        }
        lineStarts[insertIdx] = splitOffset + 1
        lineCount++
        docLength++
        for (i in (insertIdx + 1) until lineCount) {
            lineStarts[i] += 1
        }
    }

    /**
     * Incremental line merge when a newline between [lineIndex] and [lineIndex + 1] is deleted.
     */
    fun mergeLines(lineIndex: Int) {
        if (lineIndex < 0 || lineIndex >= lineCount - 1) return
        val removeIdx = lineIndex + 1
        if (removeIdx < lineCount - 1) {
            System.arraycopy(lineStarts, removeIdx + 1, lineStarts, removeIdx, lineCount - 1 - removeIdx)
        }
        lineCount--
        docLength = (docLength - 1).coerceAtLeast(0)
        for (i in removeIdx until lineCount) {
            lineStarts[i] -= 1
        }
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > lineStarts.size) {
            val newCap = maxOf(minCapacity + 128, lineStarts.size * 2)
            lineStarts = lineStarts.copyOf(newCap)
        }
    }

    fun lineStart(index: Int): Int {
        if (index <= 0) return 0
        if (index >= lineCount) return docLength
        return lineStarts[index].coerceIn(0, docLength)
    }

    fun lineEnd(index: Int): Int {
        if (index < 0) return 0
        val nextStart = if (index + 1 < lineCount) lineStarts[index + 1] else docLength + 1
        return (nextStart - 1).coerceIn(0, docLength)
    }

    fun lineLength(index: Int): Int {
        return (lineEnd(index) - lineStart(index)).coerceAtLeast(0)
    }

    fun getLineContent(text: CharSequence, index: Int): String {
        if (text.isEmpty()) return ""
        val start = lineStart(index).coerceIn(0, text.length)
        val end = lineEnd(index).coerceIn(start, text.length)
        return text.subSequence(start, end).toString()
    }

    fun lineIndexForOffset(offset: Int): Int {
        if (docLength <= 0 || lineCount <= 1) return 0
        val target = offset.coerceIn(0, docLength)
        val idx = lineStarts.binarySearch(target, 0, lineCount)
        return if (idx >= 0) {
            idx
        } else {
            val insertionPoint = -(idx + 1)
            (insertionPoint - 1).coerceIn(0, lineCount - 1)
        }
    }
}
