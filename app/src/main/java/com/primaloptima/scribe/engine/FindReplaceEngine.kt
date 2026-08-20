package com.primaloptima.scribe.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

data class SearchResult(
    val lineIndex: Int,
    val lineLocalStart: Int,
    val lineLocalEnd: Int,
    val docOffset: Int,
    val matchLength: Int
)

/**
 * Search and Replace Engine for DocumentBuffer with live reactive results.
 */
class FindReplaceEngine(
    private val getBuffer: () -> DocumentBuffer,
    private val onDocumentModified: () -> Unit
) {
    private val _results = mutableStateListOf<SearchResult>()
    val results: List<SearchResult> get() = _results

    var currentIndex by mutableIntStateOf(-1)
        private set

    var currentQuery by androidx.compose.runtime.mutableStateOf("")
        private set

    fun search(query: String, caseSensitive: Boolean = false, isRegex: Boolean = false) {
        currentQuery = query
        val buffer = getBuffer()
        if (query.isEmpty()) {
            _results.clear()
            currentIndex = -1
            return
        }

        val offsets = buffer.search(query, caseSensitive, isRegex)
        _results.clear()

        offsets.forEach { offset ->
            val lineIdx = buffer.lineIndexAt(offset)
            val lineStart = buffer.lineStart(lineIdx)
            val matchLen = query.length
            _results.add(
                SearchResult(
                    lineIndex = lineIdx,
                    lineLocalStart = (offset - lineStart).coerceAtLeast(0),
                    lineLocalEnd = (offset - lineStart + matchLen).coerceAtLeast(0),
                    docOffset = offset,
                    matchLength = matchLen
                )
            )
        }

        currentIndex = if (_results.isEmpty()) -1 else 0
    }

    fun goToNext(): SearchResult? {
        if (_results.isEmpty()) return null
        currentIndex = (currentIndex + 1) % _results.size
        return _results[currentIndex]
    }

    fun goToPrevious(): SearchResult? {
        if (_results.isEmpty()) return null
        currentIndex = if (currentIndex <= 0) _results.size - 1 else currentIndex - 1
        return _results[currentIndex]
    }

    fun replaceAll(replacement: String) {
        if (_results.isEmpty()) return
        val buffer = getBuffer()
        // Work backwards so prior character offsets remain intact during replacement
        val sortedDesc = _results.sortedByDescending { it.docOffset }
        for (res in sortedDesc) {
            val start = res.docOffset
            val end = start + res.matchLength
            buffer.delete(start, end)
            buffer.insert(start, replacement)
        }
        _results.clear()
        currentIndex = -1
        currentQuery = ""
        onDocumentModified()
    }

    fun replaceCurrent(replacement: String) {
        if (currentIndex !in _results.indices) return
        val buffer = getBuffer()
        val current = _results[currentIndex]
        buffer.delete(current.docOffset, current.docOffset + current.matchLength)
        buffer.insert(current.docOffset, replacement)
        onDocumentModified()
        // Refresh search
        if (currentQuery.isNotEmpty()) {
            search(currentQuery)
        }
    }

    fun clear() {
        _results.clear()
        currentIndex = -1
        currentQuery = ""
    }
}
