package com.primaloptima.scribe.util.model

// ── Shortcut ─────────────────────────────────────────────────────────────────

data class ShortcutAction(
    val id: String,
    val label: String,
    /** "insert" | "wrap" | "pair" */
    val kind: String,
    val payload: String,
    /** Non-null for wrap/pair */
    val closing: String? = null
)

// ── Pinned item ───────────────────────────────────────────────────────────────

data class PinnedItem(
    /** "top" | "bottom" */
    val slot: String,
    val noteId: String
)

// ── Writing streak ────────────────────────────────────────────────────────────

data class StreakData(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastWriteDate: String? = null
)

// ── Floating window ───────────────────────────────────────────────────────────

data class FloatingWindow(
    val id: String,
    val noteId: String,
    var x: Float,
    var y: Float,
    var width: Int,
    var height: Int,
    var zOrder: Int,
    var collapsed: Boolean = false
)

// ── External root ─────────────────────────────────────────────────────────────

data class ExternalRoot(
    val uri: String,
    val name: String
)

// ── Outline entry ─────────────────────────────────────────────────────────────

data class OutlineEntry(
    val level: Int,   // 1–4
    val text: String,
    val lineIndex: Int,
    val preview: String? = null
)

// ── History snapshot ──────────────────────────────────────────────────────────

data class HistorySnapshot(
    val content: String,
    val savedAt: Long
)

// ── App theme ─────────────────────────────────────────────────────────────────

data class ThemeColors(
    val background: String,
    val surface: String,
    val text: String,
    val mutedText: String,
    val accent: String,
    val border: String,
    val selection: String,
    val toolbar: String,
    val toolbarText: String
)

data class AppTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val builtIn: Boolean,
    val colors: ThemeColors,
    /** Font family key matching Google Fonts or system fonts */
    val fontFamily: String,
    val fontSize: Int,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Int,
    val paddingHorizontal: Int,
    val paddingVertical: Int,
    val maxWidth: Int,
    val backgroundImageUri: String? = null,
    val backgroundImageOpacity: Float? = 0.35f,
    val bgMode: String = "color", // "color" | "image" | "blurred"
    val blurIntensity: Float = 15f,
    val textAlignment: String = "left", // "left" | "justified" | "center"
    val themeScope: String = "whole_app", // "editor_only" | "whole_app"
    val emoji: String? = null
)

// ── SAF scan result ───────────────────────────────────────────────────────────

data class SafFile(
    val uri: String,
    val name: String,
    val ext: String,
    val folderPath: String
)

data class SafFolder(
    val uri: String,
    val relativePath: String
)

data class SafCover(
    val uri: String,
    val folderPath: String,
    val ext: String
)

data class SafScanResult(
    val files: List<SafFile> = emptyList(),
    val folders: List<SafFolder> = emptyList(),
    val covers: List<SafCover> = emptyList()
)
