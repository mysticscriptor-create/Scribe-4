package com.primaloptima.scribe.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {

    // ── No-argument destinations ──────────────────────────────────────────
    @Serializable data object Home      : Route
    @Serializable data object Settings  : Route
    @Serializable data object History   : Route
    @Serializable data object Guide     : Route
    @Serializable data object Shortcuts : Route
    @Serializable data object ThemeList : Route

    // ThemeFlow is removed — it only existed to scope the shared ThemeViewModel
    // in the Nav2 sub-graph pattern. Nav3 uses CompositionLocal instead (see
    // LocalThemeViewModel in ScribeActivity.kt).

    // ── Destinations with arguments ───────────────────────────────────────
    @Serializable data class Book(val bookId: String)                       : Route
    @Serializable data class Editor(val bookId: String, val noteId: String) : Route
    @Serializable data class Sheets(val openCreate: Boolean = false)        : Route

    // ThemeEdit renamed to ThemeEditArgs to be explicit about carrying themeId.
    // The old ThemeEdit data object is removed — it had no fields and was only
    // used as a sub-graph placeholder; the new class carries the themeId directly.
    @Serializable data class ThemeEditArgs(val themeId: String)             : Route
}
