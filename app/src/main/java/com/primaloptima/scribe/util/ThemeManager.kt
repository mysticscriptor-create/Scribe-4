package com.primaloptima.scribe.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.primaloptima.scribe.R
import com.primaloptima.scribe.util.model.AppTheme

/**
 * Manages the active theme and provides helper methods for applying theme
 * colours / typefaces to views at runtime.
 */
class ThemeManager(private val context: Context) {

    private val prefs = (context.applicationContext as com.primaloptima.scribe.ScribeApp).prefs
    private val gson = Gson()

    /** All themes = built-ins + custom themes from SharedPreferences. */
    fun allThemes(): List<AppTheme> {
        val custom = try {
            val type = object : TypeToken<List<AppTheme>>() {}.type
            gson.fromJson<List<AppTheme>>(prefs.customThemesJson, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val builtInIds = DefaultThemes.all.map { it.id }.toSet()
        val customMap = custom.associateBy { it.id }
        val updatedBuiltIns = DefaultThemes.all.map { builtIn -> customMap[builtIn.id] ?: builtIn }
        val newCustoms = custom.filter { it.id !in builtInIds }
        return (updatedBuiltIns + newCustoms).distinctBy { it.id }
    }

    fun activeTheme(): AppTheme {
        val id = prefs.activeThemeId
        return allThemes().firstOrNull { it.id == id } ?: DefaultThemes.all.first()
    }

    fun setActiveTheme(id: String) { prefs.activeThemeId = id }

    fun saveCustomTheme(theme: AppTheme) {
        val list = allCustomThemes().toMutableList()
        val idx = list.indexOfFirst { it.id == theme.id }
        if (idx >= 0) list[idx] = theme else list.add(theme)
        prefs.customThemesJson = gson.toJson(list)
    }

    fun deleteCustomTheme(id: String) {
        val list = allCustomThemes().filter { it.id != id }
        prefs.customThemesJson = gson.toJson(list)
        if (prefs.activeThemeId == id) prefs.activeThemeId = "paper"
    }

    fun duplicateTheme(id: String): AppTheme? {
        val source = allThemes().firstOrNull { it.id == id } ?: return null
        val copy = source.copy(
            id = System.currentTimeMillis().toString() + Math.random().toString().takeLast(6),
            name = "${source.name} Copy",
            builtIn = false
        )
        saveCustomTheme(copy)
        return copy
    }

    private fun allCustomThemes(): List<AppTheme> {
        return try {
            val type = object : TypeToken<List<AppTheme>>() {}.type
            gson.fromJson<List<AppTheme>>(prefs.customThemesJson, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun applyThemeToActivity(activity: AppCompatActivity, rootLayout: View? = null, bgImageView: ImageView? = null): AppTheme {
        val theme = activeTheme()
        val window = activity.window
        val bgColor = parseColor(theme.colors.background)
        val toolbarColor = parseColor(theme.colors.toolbar)
        val accentColor = parseColor(theme.colors.accent)
        val textColor = parseColor(theme.colors.text)
        val surfaceColor = parseColor(theme.colors.surface)

        rootLayout?.setBackgroundColor(bgColor)

        // Status & Navigation bar colors
        window.statusBarColor = toolbarColor
        window.navigationBarColor = surfaceColor

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isDarkTheme = theme.isDark || isColorDark(bgColor)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme

        // Custom Background Image handling
        if (bgImageView != null) {
            val imageUriStr = theme.backgroundImageUri
            if (!imageUriStr.isNullOrEmpty()) {
                try {
                    bgImageView.visibility = View.VISIBLE
                    bgImageView.setImageURI(Uri.parse(imageUriStr))
                    bgImageView.alpha = theme.backgroundImageOpacity ?: 0.35f
                    bgImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (_: Exception) {
                    bgImageView.visibility = View.GONE
                }
            } else {
                bgImageView.visibility = View.GONE
            }
        }

        // Apply theme colors to window


        return theme
    }

    companion object {

        /** Parse a hex color string (#RRGGBB or #AARRGGBB) to an int. */
        fun parseColor(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (_: Exception) { Color.BLACK }

        fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.4
        }

        /**
         * Resolve a font family key to a Typeface using Android Downloadable Fonts.
         * Falls back to the system default serif/sans/mono on failure.
         */
        fun resolveTypeface(context: Context, fontFamilyKey: String): Typeface {
            val fontResId = when (fontFamilyKey) {
                "serif", "serif-medium", "serif-bold" -> R.font.playfair_display
                "sans", "sans-medium", "sans-semibold", "sans-bold" -> R.font.inter
                "mono", "mono-medium" -> R.font.jetbrains_mono
                else -> 0
            }
            if (fontResId != 0) {
                try {
                    val tf = ResourcesCompat.getFont(context, fontResId)
                    if (tf != null) {
                        return when (fontFamilyKey) {
                            "serif-bold", "sans-bold" ->
                                Typeface.create(tf, Typeface.BOLD)
                            "serif-medium", "sans-medium", "sans-semibold", "mono-medium" ->
                                if (Build.VERSION.SDK_INT >= 28)
                                    Typeface.create(tf, 500, false)
                                else Typeface.create(tf, Typeface.NORMAL)
                            else -> tf
                        }
                    }
                } catch (_: Exception) {}
            }
            // Fallback to system typefaces
            return when {
                fontFamilyKey.startsWith("serif") -> Typeface.SERIF
                fontFamilyKey.startsWith("mono")  -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
        }

        fun lineSpacingMultiplier(key: String): Float = when (key) {
            "compact"     -> 1.4f
            "spacious"    -> 2.0f
            else          -> 1.7f  // comfortable
        }
    }
}
