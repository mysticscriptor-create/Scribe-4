package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * Minimal Sora [EmptyLanguage] subclass for prose editing.
 *
 * Provides three of the four smart-editing behaviours from ScribeInputTransformation:
 *  1. Auto-pair    — typing ( inserts () and places cursor between them
 *  2. Skip-over    — typing ) when already before ) moves cursor forward
 *  4. Paired BS    — Backspace on open char deletes both when pair is empty
 *
 * These three are handled natively by Sora's [SymbolPairMatch] system.
 *
 * Behaviour 3 (Smart Enter — Enter before a close char moves past it instead
 * of inserting a newline) is NOT implemented here. Sora's NewlineHandler API
 * signature differs from what documentation suggests; implement Smart Enter
 * later via subscribeEvent(EditorKeyEvent) once the exact API is confirmed.
 *
 * No syntax highlighting, no auto-completion, no LSP — zero overhead.
 *
 * IMPORTANT: SymbolPair takes String arguments, not Char. All pair registrations
 * use string literals ("(" not '(') to match the Java method signature.
 */
class ScribeProseLanguage : EmptyLanguage() {

    private val pairs = SymbolPairMatch().apply {
        // ASCII pairs — note: String literals required, not Char literals
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        // Typographic pairs
        putPair('\u201C', SymbolPairMatch.SymbolPair("\u201C", "\u201D"))  // " "
        putPair('\u2018', SymbolPairMatch.SymbolPair("\u2018", "\u2019"))  // ' '
        putPair('\u00AB', SymbolPairMatch.SymbolPair("\u00AB", "\u00BB"))  // « »
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    // Smart Enter (behaviour 3) — TODO:
    // Sora's NewlineHandler API needs to be verified against the actual 0.24.6
    // source before implementing. Subscribe to EditorKeyEvent in the AndroidView
    // factory block and intercept KEYCODE_ENTER there instead:
    //
    //   editor.subscribeEvent(EditorKeyEvent::class.java) { event, _ ->
    //       if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
    //           val cursor = editor.cursor
    //           val line = editor.text.getLine(cursor.leftLine)
    //           val col  = cursor.leftColumn
    //           if (col < line.length && line[col] in closeChars) {
    //               editor.setSelection(cursor.leftLine, col + 1)
    //               event.intercept()
    //           }
    //       }
    //   }
}
