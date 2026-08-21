package com.primaloptima.scribe.engine

import java.util.regex.Pattern

data class Piece(
    val source: Source,
    val start: Int,
    val length: Int
)

enum class Source { ORIGINAL, APPEND }

data class CursorPos(
    val line: Int,
    val column: Int
)

sealed class Edit {
    data class Insert(val pos: Int, val length: Int, val text: String) : Edit()
    data class Delete(val start: Int, val end: Int, val text: String) : Edit()
    data class Compound(val edits: List<Edit>) : Edit()
}

/**
 * High-performance Piece Table document buffer for prose editing.
 * Handles 100,000+ words with instant insertions, deletions, and line virtualization.
 */
class DocumentBuffer(initialContent: String = "") {
    private val original: String = initialContent
    private val appendBuf = StringBuilder()
    private val pieces = mutableListOf<Piece>()
    private var totalLength = 0
    private var nextLineKey = 1L
    private val lineKeys = mutableListOf<Long>()

    // Line start index caching
    private var lineStartsCache: IntArray = intArrayOf(0)

    // Piece access locality cache
    private var cachedPieceIndex = 0
    private var cachedPieceOffset = 0

    init {
        if (initialContent.isNotEmpty()) {
            pieces.add(Piece(Source.ORIGINAL, 0, initialContent.length))
            totalLength = initialContent.length
        }
        rebuildLineIndex()
        repeat(lineStartsCache.size) { lineKeys.add(nextLineKey++) }
    }

    // ── Read operations ──────────────────────────────────────────────────

    fun length(): Int = totalLength

    fun charAt(pos: Int): Char {
        require(pos in 0 until totalLength) { "Index out of bounds: $pos (length: $totalLength)" }
        val (pieceIndex, localOffset) = findPieceAt(pos)
        val piece = pieces[pieceIndex]
        return when (piece.source) {
            Source.ORIGINAL -> original[piece.start + localOffset]
            Source.APPEND -> appendBuf[piece.start + localOffset]
        }
    }

    fun substring(start: Int, end: Int): String {
        val s = start.coerceIn(0, totalLength)
        val e = end.coerceIn(s, totalLength)
        if (s == e) return ""

        val sb = StringBuilder(e - s)
        var currentDocOffset = 0

        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            val pieceEnd = currentDocOffset + piece.length
            if (pieceEnd > s && currentDocOffset < e) {
                val overlapStart = maxOf(s, currentDocOffset)
                val overlapEnd = minOf(e, pieceEnd)
                val localStart = piece.start + (overlapStart - currentDocOffset)
                val localEnd = piece.start + (overlapEnd - currentDocOffset)

                when (piece.source) {
                    Source.ORIGINAL -> sb.append(original, localStart, localEnd)
                    Source.APPEND -> sb.append(appendBuf, localStart, localEnd)
                }
            }
            currentDocOffset = pieceEnd
            if (currentDocOffset >= e) break
        }
        return sb.toString()
    }

    fun asString(): String {
        if (pieces.isEmpty() || totalLength == 0) return ""
        val sb = StringBuilder(totalLength)
        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            when (piece.source) {
                Source.ORIGINAL -> sb.append(original, piece.start, piece.start + piece.length)
                Source.APPEND -> sb.append(appendBuf, piece.start, piece.start + piece.length)
            }
        }
        return sb.toString()
    }

    // ── Write operations ─────────────────────────────────────────────────

    fun insert(pos: Int, text: String): Edit {
        if (text.isEmpty()) return Edit.Compound(emptyList())
        val targetPos = pos.coerceIn(0, totalLength)
        val lineAtInsertion = lineIndexAt(targetPos)

        val appendStart = appendBuf.length
        appendBuf.append(text)
        val newPiece = Piece(Source.APPEND, appendStart, text.length)

        if (pieces.isEmpty()) {
            pieces.add(newPiece)
        } else if (targetPos == 0) {
            pieces.add(0, newPiece)
        } else if (targetPos == totalLength) {
            pieces.add(newPiece)
        } else {
            val (pieceIndex, localOffset) = findPieceAt(targetPos)
            val origPiece = pieces[pieceIndex]
            val leftPiece = Piece(origPiece.source, origPiece.start, localOffset)
            val rightPiece = Piece(
                origPiece.source,
                origPiece.start + localOffset,
                origPiece.length - localOffset
            )

            pieces.removeAt(pieceIndex)
            val toInsert = mutableListOf<Piece>()
            if (leftPiece.length > 0) toInsert.add(leftPiece)
            toInsert.add(newPiece)
            if (rightPiece.length > 0) toInsert.add(rightPiece)
            pieces.addAll(pieceIndex, toInsert)
        }

        totalLength += text.length

        // Find newline offsets within the inserted text
        val newlinesInText = mutableListOf<Int>()
        for (idx in 0 until text.length) {
            if (text[idx] == '\n') {
                newlinesInText.add(targetPos + idx + 1)
            }
        }

        if (newlinesInText.isEmpty()) {
            // Fast-path: no newlines inserted, shift subsequent line start offsets in-place
            for (k in (lineAtInsertion + 1) until lineStartsCache.size) {
                lineStartsCache[k] += text.length
            }
        } else {
            // Newlines inserted: splice into lineStartsCache and lineKeys
            val oldCache = lineStartsCache
            val newCache = IntArray(oldCache.size + newlinesInText.size)
            System.arraycopy(oldCache, 0, newCache, 0, lineAtInsertion + 1)

            for (nlIdx in 0 until newlinesInText.size) {
                newCache[lineAtInsertion + 1 + nlIdx] = newlinesInText[nlIdx]
                val keyPos = (lineAtInsertion + 1 + nlIdx).coerceIn(0, lineKeys.size)
                lineKeys.add(keyPos, nextLineKey++)
            }

            val remainingCount = oldCache.size - (lineAtInsertion + 1)
            if (remainingCount > 0) {
                val destPos = lineAtInsertion + 1 + newlinesInText.size
                System.arraycopy(oldCache, lineAtInsertion + 1, newCache, destPos, remainingCount)
                for (k in destPos until newCache.size) {
                    newCache[k] += text.length
                }
            }
            lineStartsCache = newCache
        }

        cachedPieceIndex = 0
        cachedPieceOffset = 0
        return Edit.Insert(targetPos, text.length, text)
    }

    fun delete(start: Int, end: Int): Edit {
        val s = start.coerceIn(0, totalLength)
        val e = end.coerceIn(s, totalLength)
        if (s == e) return Edit.Compound(emptyList())

        val deletedLength = e - s
        val deletedText = substring(s, e)
        val startLine = lineIndexAt(s)
        val newPieces = mutableListOf<Piece>()
        var currentDocOffset = 0

        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            val pieceEnd = currentDocOffset + piece.length

            if (pieceEnd <= s || currentDocOffset >= e) {
                newPieces.add(piece)
            } else {
                if (currentDocOffset < s) {
                    newPieces.add(Piece(piece.source, piece.start, s - currentDocOffset))
                }
                if (pieceEnd > e) {
                    val rightOffset = e - currentDocOffset
                    newPieces.add(
                        Piece(
                            piece.source,
                            piece.start + rightOffset,
                            piece.length - rightOffset
                        )
                    )
                }
            }
            currentDocOffset = pieceEnd
        }

        pieces.clear()
        pieces.addAll(newPieces)
        totalLength -= deletedLength

        // Check if any line boundaries were deleted in (s, e]
        var deletedLineStartsCount = 0
        val remainingStarts = mutableListOf<Int>()
        val keptIndices = mutableListOf<Int>()

        for (k in 0 until lineStartsCache.size) {
            val lineOffset = lineStartsCache[k]
            if (lineOffset in (s + 1)..e) {
                deletedLineStartsCount++
            } else {
                keptIndices.add(k)
                if (lineOffset > e) {
                    remainingStarts.add(lineOffset - deletedLength)
                } else {
                    remainingStarts.add(lineOffset)
                }
            }
        }

        if (deletedLineStartsCount == 0) {
            // Fast-path: no line starts removed, simply shift subsequent line starts in-place
            for (k in (startLine + 1) until lineStartsCache.size) {
                lineStartsCache[k] -= deletedLength
            }
        } else {
            // Re-sync lineKeys and lineStartsCache
            val newKeys = mutableListOf<Long>()
            for (idx in keptIndices) {
                if (idx in lineKeys.indices) {
                    newKeys.add(lineKeys[idx])
                }
            }
            lineKeys.clear()
            lineKeys.addAll(newKeys)
            if (lineKeys.isEmpty()) lineKeys.add(nextLineKey++)

            lineStartsCache = if (remainingStarts.isNotEmpty()) remainingStarts.toIntArray() else intArrayOf(0)
        }

        cachedPieceIndex = 0
        cachedPieceOffset = 0

        return Edit.Delete(s, e, deletedText)
    }

    fun applyEdit(edit: Edit) {
        when (edit) {
            is Edit.Insert -> insert(edit.pos, edit.text)
            is Edit.Delete -> delete(edit.start, edit.end)
            is Edit.Compound -> {
                for (subEdit in edit.edits) {
                    applyEdit(subEdit)
                }
            }
        }
    }

    fun invertEdit(edit: Edit) {
        when (edit) {
            is Edit.Insert -> delete(edit.pos, edit.pos + edit.length)
            is Edit.Delete -> insert(edit.start, edit.text)
            is Edit.Compound -> {
                for (subEdit in edit.edits.reversed()) {
                    invertEdit(subEdit)
                }
            }
        }
    }

    // ── Search ───────────────────────────────────────────────────────────

    fun search(query: String, caseSensitive: Boolean, isRegex: Boolean): List<Int> {
        if (query.isEmpty()) return emptyList()
        val fullText = asString()
        val results = mutableListOf<Int>()

        if (isRegex) {
            try {
                val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
                val pattern = Pattern.compile(query, flags)
                val matcher = pattern.matcher(fullText)
                while (matcher.find()) {
                    results.add(matcher.start())
                }
            } catch (_: Exception) {
                // Invalid regex pattern, fallback to empty
            }
        } else {
            var startIndex = 0
            while (startIndex < fullText.length) {
                val index = fullText.indexOf(query, startIndex, ignoreCase = !caseSensitive)
                if (index == -1) break
                results.add(index)
                startIndex = index + maxOf(1, query.length)
            }
        }

        return results
    }

    // ── Line/Paragraph Indexing ──────────────────────────────────────────

    fun lineCount(): Int = lineStartsCache.size

    fun lineStart(lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        if (lineIndex >= lineStartsCache.size) return totalLength
        return lineStartsCache[lineIndex]
    }

    fun lineLength(lineIndex: Int): Int {
        if (lineIndex < 0 || lineIndex >= lineStartsCache.size) return 0
        val start = lineStartsCache[lineIndex]
        val end = if (lineIndex + 1 < lineStartsCache.size) {
            // Subtract trailing newline character
            val nextStart = lineStartsCache[lineIndex + 1]
            if (nextStart > start && charAt(nextStart - 1) == '\n') nextStart - 1 else nextStart
        } else {
            totalLength
        }
        return (end - start).coerceAtLeast(0)
    }

    fun lineIndexAt(pos: Int): Int {
        val target = pos.coerceIn(0, totalLength)
        val search = lineStartsCache.binarySearch(target)
        return if (search >= 0) search else (-search - 2).coerceAtLeast(0)
    }

    /**
     * Stable identity for a logical paragraph. Unlike its index, this survives
     * edits in earlier paragraphs and lets LazyColumn preserve remembered state.
     */
    fun lineKey(lineIndex: Int): Long {
        if (lineIndex !in lineKeys.indices) return Long.MIN_VALUE + lineIndex
        return lineKeys[lineIndex]
    }

    fun lineContent(lineIndex: Int): String {
        if (lineIndex < 0 || lineIndex >= lineStartsCache.size) return ""
        val start = lineStartsCache[lineIndex]
        val nextStart = if (lineIndex + 1 < lineStartsCache.size) lineStartsCache[lineIndex + 1] else totalLength
        val end = if (nextStart > start && nextStart <= totalLength && charAt(nextStart - 1) == '\n') {
            nextStart - 1
        } else {
            nextStart
        }
        return substring(start, end)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    /**
     * Coalesces adjacent pieces that share the same source buffer and contiguous offsets.
     * Shrinks piece count back towards minimal allocations during idle periods.
     */
    fun coalesce() {
        if (pieces.size <= 2) return
        val merged = mutableListOf<Piece>()
        var i = 0
        while (i < pieces.size) {
            val cur = pieces[i]
            var j = i + 1
            var totalLen = cur.length
            while (j < pieces.size) {
                val next = pieces[j]
                if (next.source == cur.source && next.start == cur.start + totalLen) {
                    totalLen += next.length
                    j++
                } else break
            }
            merged.add(Piece(cur.source, cur.start, totalLen))
            i = j
        }
        pieces.clear()
        pieces.addAll(merged)
    }

    /**
     * Iterates line by line without allocating a full document string.
     */
    inline fun forEachLine(action: (lineIndex: Int, lineStart: Int, lineContent: String) -> Unit) {
        val total = lineCount()
        for (i in 0 until total) {
            val start = lineStart(i)
            val content = lineContent(i)
            action(i, start, content)
        }
    }

    private fun findPieceAt(pos: Int): Pair<Int, Int> {
        var currentOffset = 0
        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            if (pos < currentOffset + piece.length) {
                cachedPieceIndex = i
                cachedPieceOffset = currentOffset
                return Pair(i, pos - currentOffset)
            }
            currentOffset += piece.length
        }
        val lastIdx = (pieces.size - 1).coerceAtLeast(0)
        return Pair(lastIdx, if (pieces.isNotEmpty()) pieces[lastIdx].length else 0)
    }

    private fun rebuildLineIndex() {
        val starts = mutableListOf(0)
        var docOffset = 0
        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            when (piece.source) {
                Source.ORIGINAL -> {
                    for (c in piece.start until (piece.start + piece.length)) {
                        if (original[c] == '\n') {
                            starts.add(docOffset + (c - piece.start) + 1)
                        }
                    }
                }
                Source.APPEND -> {
                    for (c in piece.start until (piece.start + piece.length)) {
                        if (appendBuf[c] == '\n') {
                            starts.add(docOffset + (c - piece.start) + 1)
                        }
                    }
                }
            }
            docOffset += piece.length
        }
        lineStartsCache = starts.toIntArray()
    }
}
