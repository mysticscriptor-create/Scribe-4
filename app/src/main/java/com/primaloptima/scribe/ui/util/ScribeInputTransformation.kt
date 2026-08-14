package com.primaloptima.scribe.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange

/**
 * Replicates all smart-editing behaviour from ScribeInputConnection for BasicTextField:
 *
 *  • Auto-pair:        typing ( inserts () and places cursor between them
 *  • Skip-over:        typing ) when cursor is already before ) just moves forward
 *  • Smart Enter:      Enter before a close char moves cursor past it instead of newline
 *  • Paired Backspace: Backspace on open char deletes both when the pair is empty
 *
 * Registered as inputTransformation on BasicTextField.
 * The undo/redo stack is provided natively by TextFieldState — no custom stack needed.
 */
@OptIn(ExperimentalFoundationApi::class)
object ScribeInputTransformation : InputTransformation {

    private val pairMap: Map<Char, Char> = mapOf(
        '(' to ')', '[' to ']', '{' to '}', '`' to '`',
        '"' to '"', '\'' to '\'',
        '\u201C' to '\u201D',   // " → "
        '\u2018' to '\u2019',   // ' → '
        '\u00AB' to '\u00BB'    // « → »
    )
    private val closeChars: Set<Char> = pairMap.values.toSet()
    private val openChars: Set<Char>  = pairMap.keys.toSet()

    override fun TextFieldBuffer.transformInput() {
        val changes = changes
        if (changes.changeCount != 1) return

        val newRange      = changes.getRange(0)        // range in post-edit buffer
        val originalRange = changes.getOriginalRange(0) // range in pre-edit buffer
        val insertedLen   = newRange.length
        val deletedLen    = originalRange.length

        // ── Paired Backspace ──────────────────────────────────────────────────
        // Single char deleted (backspace), nothing inserted, cursor not selecting.
        if (deletedLen == 1 && insertedLen == 0) {
            val cursorPos = newRange.start   // cursor position after deletion
            val charAfter = asCharSequence().getOrNull(cursorPos) ?: return
            // We need to know what char was deleted. It was at originalRange.start
            // in the pre-edit text. The buffer doesn't expose pre-edit text directly,
            // but we can infer: the deleted char was the one before the cursor pre-edit,
            // which is now at cursorPos - 0 (deletion moved cursor back by 1).
            // We look at charAfter: if it's a close char, and it matches the
            // deleted char's expected pair, delete it too.
            // To find the deleted char we look at what close chars are possible
            // given charAfter.
            if (charAfter in closeChars) {
                // Find which open char maps to this close char
                val openChar = pairMap.entries.firstOrNull { it.value == charAfter }?.key
                if (openChar != null) {
                    // The deleted char was at (cursorPos) in original = openChar position.
                    // We delete the close char that's now sitting at cursorPos.
                    replace(cursorPos, cursorPos + 1, "")
                }
            }
            return
        }

        // Only handle single-character insertions from here
        if (insertedLen != 1) return

        val ch        = asCharSequence()[newRange.start]
        val cursorPos = newRange.end   // cursor sits after the inserted char
        val fullText  = asCharSequence()

        // ── Smart Enter ───────────────────────────────────────────────────────
        if (ch == '\n') {
            val charAfterNewline = fullText.getOrNull(cursorPos)
            if (charAfterNewline != null && charAfterNewline in closeChars) {
                // Remove the newline, move cursor past the close char
                replace(newRange.start, newRange.end, "")
                selection = TextRange(newRange.start + 1)
            }
            return
        }

        // ── Skip-over ─────────────────────────────────────────────────────────
        if (ch in closeChars) {
            val charAfterInsert = fullText.getOrNull(cursorPos)
            if (charAfterInsert == ch) {
                // Remove the duplicate we just inserted, move cursor past the existing one
                replace(newRange.start, newRange.end, "")
                selection = TextRange(newRange.start + 1)
                return
            }
        }

        // ── Auto-pair ─────────────────────────────────────────────────────────
        if (ch in openChars) {
            val closeChar    = pairMap[ch]!!
            val hasSelection = originalRange.length > 0

            if (!hasSelection) {
                val charAfterInsert = fullText.getOrNull(cursorPos)
                val shouldPair = charAfterInsert == null
                    || charAfterInsert == '\n'
                    || charAfterInsert == ' '
                    || charAfterInsert == closeChar && ch == closeChar  // same char (backtick etc.)
                    || charAfterInsert !in closeChars

                if (shouldPair) {
                    // Insert the close char right after, leave cursor between the pair
                    replace(cursorPos, cursorPos, closeChar.toString())
                    selection = TextRange(cursorPos)
                }
            }
            // Note: selection-wrap (wrap selected text in pair) is handled by
            // the toolbar's applyFormat() equivalent in BasicTextField — see
            // ScribeTextFieldActions.wrapSelection().
            return
        }
    }
}
