package com.primaloptima.scribe.ui.theme

import android.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme

/**
 * Maps Scribe's [AppTheme] onto Sora 0.24.6's [EditorColorScheme] token set.
 *
 * Construction is cheap (just int assignments); call [ScribeColorScheme(theme)]
 * whenever the active theme changes and assign it to [editor.colorScheme].
 * Sora will redraw automatically on assignment.
 *
 * Tokens covered:
 *  - Background / gutter (hidden)
 *  - Normal text
 *  - Current-line highlight (subtle, ~7% opacity of text colour)
 *  - Selection background + handles + insert cursor line
 *  - Matched-bracket highlight
 *  - Scroll bar thumb/track
 *
 * Note: SEARCH_RESULT_BACKGROUND is not a valid constant in Sora 0.24.x —
 * search highlight colours are managed internally by the editor.
 *
 * Removed: TEXT_SELECTED — not a valid EditorColorScheme constant in Sora 0.24.x;
 * Sora reuses TEXT_NORMAL for selected-text rendering automatically.
 */
class ScribeColorScheme(theme: AppTheme) : EditorColorScheme() {

    init {
        val bg      = parse(theme.colors.background)
        val text    = parse(theme.colors.text)
        val accent  = parse(theme.colors.accent)
        val sel     = parse(theme.colors.selection)

        // ── Background ────────────────────────────────────────────────────────
        setColor(WHOLE_BACKGROUND,         bg)
        setColor(LINE_NUMBER_BACKGROUND,   bg)
        setColor(LINE_NUMBER,              bg)   // gutter text invisible
        setColor(LINE_DIVIDER,             bg)

        // ── Text ──────────────────────────────────────────────────────────────
        setColor(TEXT_NORMAL,              text)
        // Note: TEXT_SELECTED is not a valid constant in Sora 0.24.x.
        // Sora renders selected text using TEXT_NORMAL automatically.

        // ── Current line (very subtle) ────────────────────────────────────────
        val currentLineTint = Color.argb(18,
            Color.red(text), Color.green(text), Color.blue(text))
        setColor(CURRENT_LINE,             currentLineTint)

        // ── Selection ─────────────────────────────────────────────────────────
        setColor(SELECTED_TEXT_BACKGROUND, withAlpha(sel, 160))
        setColor(SELECTION_HANDLE,         accent)
        setColor(SELECTION_INSERT,         accent)  // cursor bar colour

        // ── Matched bracket ───────────────────────────────────────────────────
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, text)
        setColor(HIGHLIGHTED_DELIMITERS_BACKGROUND, withAlpha(accent, 60))
        // HIGHLIGHTED_DELIMITERS_UNDERLINE may not exist in all 0.24.x builds —
        // omitted to avoid a silent no-op or runtime crash on older patch versions.

        // ── Scroll indicators (keep neutral) ──────────────────────────────────
        setColor(SCROLL_BAR_THUMB,         withAlpha(text, 60))
        setColor(SCROLL_BAR_THUMB_PRESSED, withAlpha(text, 120))
        setColor(SCROLL_BAR_TRACK,         withAlpha(text, 20))
    }

    private fun parse(hex: String): Int = ThemeManager.parseColor(hex)

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
