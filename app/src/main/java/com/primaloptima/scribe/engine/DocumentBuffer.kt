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
    private var nextLineKey = 1L
    private val lineKeys = mutableListOf<Long>()

    // Line start index caching
    private var lineStartsCache: IntArray = intArrayOf(0)
    private var isLineIndexDirty = true

    // Piece access locality cache
    private var cachedPieceIndex = 0
    private var cachedPieceOffset = 0

    init {
        if (initialContent.isNotEmpty()) {
            pieces.add(Piece(Source.ORIGINAL, 0, initialContent.length))
        }
        rebuildLineIndex()
        repeat(lineStartsCache.size) { lineKeys.add(nextLineKey++) }
    }

    // ── Read operations ──────────────────────────────────────────────────

    fun length(): Int {
        var total = 0
        for (i in 0 until pieces.size) {
            total += pieces[i].length
        }
        return total
    }

    fun charAt(pos: Int): Char {
        require(pos in 0 until length()) { "Index out of bounds: $pos (length: ${length()})" }
        val (pieceIndex, localOffset) = findPieceAt(pos)
        val piece = pieces[pieceIndex]
        return when (piece.source) {
            Source.ORIGINAL -> original[piece.start + localOffset]
            Source.APPEND -> appendBuf[piece.start + localOffset]
        }
    }

    fun substring(start: Int, end: Int): String {
        val docLen = length()
        val s = start.coerceIn(0, docLen)
        val e = end.coerceIn(s, docLen)
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
        if (pieces.isEmpty()) return ""
        val sb = StringBuilder(length())
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
        val docLen = length()
        val targetPos = pos.coerceIn(0, docLen)
        val lineAtInsertion = lineIndexAt(targetPos)
        val insertedLineCount = text.count { it == '\n' }

        val appendStart = appendBuf.length
        appendBuf.append(text)
        val newPiece = Piece(Source.APPEND, appendStart, text.length)

        if (pieces.isEmpty()) {
            pieces.add(newPiece)
        } else if (targetPos == 0) {
            pieces.add(0, newPiece)
        } else if (targetPos == docLen) {
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

        if (insertedLineCount > 0) {
            repeat(insertedLineCount) {
                lineKeys.add(lineAtInsertion + 1, nextLineKey++)
            }
        }
        invalidateCaches()
        return Edit.Insert(targetPos, text.length, text)
    }

    fun delete(start: Int, end: Int): Edit {
        val docLen = length()
        val s = start.coerceIn(0, docLen)
        val e = end.coerceIn(s, docLen)
        if (s == e) return Edit.Compound(emptyList())

        val deletedText = substring(s, e)
        val startLine = lineIndexAt(s)
        val deletedLineCount = deletedText.count { it == '\n' }
        val newPieces = mutableListOf<Piece>()
        var currentDocOffset = 0

        for (i in 0 until pieces.size) {
            val piece = pieces[i]
            val pieceEnd = currentDocOffset + piece.length

            if (pieceEnd <= s || currentDocOffset >= e) {
                // Piece completely outside the deletion range
                newPieces.add(piece)
            } else {
                // Piece overlaps deletion range
                if (currentDocOffset < s) {
                    // Retain left part
                    newPieces.add(Piece(piece.source, piece.start, s - currentDocOffset))
                }
                if (pieceEnd > e) {
                    // Retain right part
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
        if (deletedLineCount > 0) {
            repeat(deletedLineCount) {
                if (startLine + 1 < lineKeys.size) lineKeys.removeAt(startLine + 1)
            }
        }
        invalidateCaches()

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

    fun lineCount(): Int {
        ensureLineIndex()
        return lineStartsCache.size
    }

    fun lineStart(lineIndex: Int): Int {
        ensureLineIndex()
        if (lineIndex <= 0) return 0
        if (lineIndex >= lineStartsCache.size) return length()
        return lineStartsCache[lineIndex]
    }

    fun lineLength(lineIndex: Int): Int {
        ensureLineIndex()
        if (lineIndex < 0 || lineIndex >= lineStartsCache.size) return 0
        val start = lineStartsCache[lineIndex]
        val end = if (lineIndex + 1 < lineStartsCache.size) {
            // Subtract trailing newline character
            val nextStart = lineStartsCache[lineIndex + 1]
            if (nextStart > start && charAt(nextStart - 1) == '\n') nextStart - 1 else nextStart
        } else {
            length()
        }
        return (end - start).coerceAtLeast(0)
    }

    fun lineIndexAt(pos: Int): Int {
        ensureLineIndex()
        val target = pos.coerceIn(0, length())
        val search = lineStartsCache.binarySearch(target)
        return if (search >= 0) search else (-search - 2).coerceAtLeast(0)
    }

    /**
     * Stable identity for a logical paragraph. Unlike its index, this survives
     * edits in earlier paragraphs and lets LazyColumn preserve remembered state.
     */
    fun lineKey(lineIndex: Int): Long {
        ensureLineIndex()
        if (lineIndex !in lineKeys.indices) return Long.MIN_VALUE + lineIndex
        return lineKeys[lineIndex]
    }

    fun lineContent(lineIndex: Int): String {
        ensureLineIndex()
        if (lineIndex < 0 || lineIndex >= lineStartsCache.size) return ""
        val start = lineStartsCache[lineIndex]
        val nextStart = if (lineIndex + 1 < lineStartsCache.size) lineStartsCache[lineIndex + 1] else length()
        val end = if (nextStart > start && nextStart <= length() && charAt(nextStart - 1) == '\n') {
            nextStart - 1
        } else {
            nextStart
        }
        return substring(start, end)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

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

    private fun invalidateCaches() {
        isLineIndexDirty = true
        cachedPieceIndex = 0
        cachedPieceOffset = 0
    }

    private fun ensureLineIndex() {
        if (isLineIndexDirty) {
            rebuildLineIndex()
        }
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
        isLineIndexDirty = false
    }
}
