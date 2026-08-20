package com.primaloptima.scribe.engine

data class UndoEntry(
    val bufferEdit: Edit,
    val formatEdit: FormatEdit? = null,
    val cursorBefore: CursorPos,
    val cursorAfter: CursorPos,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String = ""
)

/**
 * 200-step undo/redo stack manager with smart 500ms typing-grouping and snapshot checkpoints.
 */
class UndoManager(val limit: Int = 200) {
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun push(entry: UndoEntry) {
        redoStack.clear()

        if (undoStack.isNotEmpty()) {
            val last = undoStack.last()
            val timeDiff = entry.timestamp - last.timestamp

            // Merge typing keystrokes within 500ms if contiguous single-char inserts or deletes without format changes
            if (timeDiff < 500 && last.formatEdit == null && entry.formatEdit == null && last.label.isEmpty() && entry.label.isEmpty()) {
                val mergedBufferEdit = tryMergeEdits(last.bufferEdit, entry.bufferEdit)
                if (mergedBufferEdit != null) {
                    undoStack.removeLast()
                    undoStack.addLast(
                        UndoEntry(
                            bufferEdit = mergedBufferEdit,
                            formatEdit = null,
                            cursorBefore = last.cursorBefore,
                            cursorAfter = entry.cursorAfter,
                            timestamp = entry.timestamp
                        )
                    )
                    return
                }
            }
        }

        undoStack.addLast(entry)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
    }

    private fun tryMergeEdits(prev: Edit, next: Edit): Edit? {
        if (prev is Edit.Insert && next is Edit.Insert) {
            if (prev.pos + prev.length == next.pos) {
                return Edit.Insert(prev.pos, prev.length + next.length, prev.text + next.text)
            }
        } else if (prev is Edit.Delete && next is Edit.Delete) {
            if (next.start + next.text.length == prev.start) {
                // Backspace grouping backwards
                return Edit.Delete(next.start, prev.end, next.text + prev.text)
            } else if (prev.start == next.start) {
                // Delete forward grouping
                return Edit.Delete(prev.start, prev.start + prev.text.length + next.text.length, prev.text + next.text)
            }
        }
        return null
    }

    fun undo(buffer: DocumentBuffer, formats: FormatRegistry): UndoEntry? {
        if (undoStack.isEmpty()) return null
        val entry = undoStack.removeLast()
        // Invert buffer changes
        buffer.invertEdit(entry.bufferEdit)
        // Invert format changes
        entry.formatEdit?.let { formats.invertFormatEdit(it) }

        redoStack.addLast(entry)
        return entry
    }

    fun redo(buffer: DocumentBuffer, formats: FormatRegistry): UndoEntry? {
        if (redoStack.isEmpty()) return null
        val entry = redoStack.removeLast()
        // Re-apply buffer changes
        buffer.applyEdit(entry.bufferEdit)
        // Re-apply format changes
        entry.formatEdit?.let { formats.applyFormatEdit(it) }

        undoStack.addLast(entry)
        return entry
    }

    fun saveCheckpoint(label: String) {
        if (undoStack.isEmpty()) return
        val last = undoStack.removeLast()
        undoStack.addLast(last.copy(label = label))
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
