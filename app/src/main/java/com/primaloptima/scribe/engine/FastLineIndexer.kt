package com.primaloptima.scribe.engine

/**
 * High-performance, zero-allocation line indexer inspired by Sora Editor's Piece/Line Index.
 * Scans CharSequence directly without creating intermediate string allocations or boxed objects.
 */
class FastLineIndexer {
    private var lineStarts = IntArray(512) { 0 }
    var lineCount: Int = 1
        private set
    private var docLength: Int = 0

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
