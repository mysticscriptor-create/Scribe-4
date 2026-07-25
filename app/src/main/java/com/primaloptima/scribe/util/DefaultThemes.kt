package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors

object DefaultThemes {

    val all: List<AppTheme> = listOf(
        AppTheme(
            id = "obsidian", name = "Obsidian", isDark = true, builtIn = true,
            colors = ThemeColors(
                background = "#1A1A1A", surface = "#242424",
                text = "#FFFFFF", mutedText = "#D0D0D0", accent = "#E0E0E0",
                border = "#3A3A3A", selection = "#444444",
                toolbar = "#242424", toolbarText = "#FFFFFF"
            ),
            fontFamily = "sans", fontSize = 17, lineHeight = 1.65f,
            letterSpacing = 0f, paragraphSpacing = 12,
            paddingHorizontal = 22, paddingVertical = 18, maxWidth = 720
        ),
        AppTheme(
            id = "midnight", name = "Midnight Blue", isDark = true, builtIn = true,
            colors = ThemeColors(
                background = "#0D1B2A", surface = "#1B2A3B",
                text = "#E8EDF2", mutedText = "#B0C4DE", accent = "#90CAF9",
                border = "#2C3E50", selection = "#1E3A5F",
                toolbar = "#1B2A3B", toolbarText = "#E8EDF2"
            ),
            fontFamily = "sans", fontSize = 17, lineHeight = 1.65f,
            letterSpacing = 0f, paragraphSpacing = 12,
            paddingHorizontal = 22, paddingVertical = 18, maxWidth = 720
        ),
        AppTheme(
            id = "focus", name = "Focus", isDark = true, builtIn = true,
            colors = ThemeColors(
                background = "#000000", surface = "#111111",
                text = "#FFFFFF", mutedText = "#AAAAAA", accent = "#CCCCCC",
                border = "#222222", selection = "#333333",
                toolbar = "#111111", toolbarText = "#FFFFFF"
            ),
            fontFamily = "mono", fontSize = 17, lineHeight = 1.7f,
            letterSpacing = 0f, paragraphSpacing = 14,
            paddingHorizontal = 26, paddingVertical = 22, maxWidth = 700
        ),
        AppTheme(
            id = "paper", name = "Paper", isDark = false, builtIn = true,
            colors = ThemeColors(
                background = "#FAFAF7", surface = "#FFFFFF",
                text = "#1A1A1A", mutedText = "#555555", accent = "#333333",
                border = "#E0E0D8", selection = "#E0E0E0",
                toolbar = "#FFFFFF", toolbarText = "#1A1A1A"
            ),
            fontFamily = "serif", fontSize = 18, lineHeight = 1.7f,
            letterSpacing = 0.2f, paragraphSpacing = 14,
            paddingHorizontal = 24, paddingVertical = 20, maxWidth = 720
        ),
        AppTheme(
            id = "sepia", name = "Sepia", isDark = false, builtIn = true,
            colors = ThemeColors(
                background = "#F5ECD7", surface = "#EDE0C4",
                text = "#3B2A1A", mutedText = "#6E523A", accent = "#6B4C2A",
                border = "#D8C8A8", selection = "#DEC89C",
                toolbar = "#EDE0C4", toolbarText = "#3B2A1A"
            ),
            fontFamily = "serif", fontSize = 19, lineHeight = 1.75f,
            letterSpacing = 0.3f, paragraphSpacing = 16,
            paddingHorizontal = 28, paddingVertical = 22, maxWidth = 680
        ),
        AppTheme(
            id = "typewriter", name = "Typewriter", isDark = false, builtIn = true,
            colors = ThemeColors(
                background = "#FFFFFF", surface = "#F5F5F5",
                text = "#111111", mutedText = "#444444", accent = "#222222",
                border = "#E0E0E0", selection = "#DDDDDD",
                toolbar = "#F5F5F5", toolbarText = "#111111"
            ),
            fontFamily = "mono", fontSize = 16, lineHeight = 1.8f,
            letterSpacing = 0f, paragraphSpacing = 14,
            paddingHorizontal = 22, paddingVertical = 20, maxWidth = 680
        )
    )

    fun findById(id: String): AppTheme = all.firstOrNull { it.id == id } ?: all.first()
}
