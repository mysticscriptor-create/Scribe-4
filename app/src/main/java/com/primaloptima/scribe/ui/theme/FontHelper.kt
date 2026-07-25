package com.primaloptima.scribe.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.primaloptima.scribe.R

data class FontOption(
    val key: String,
    val name: String,
    val subtitle: String
)

object FontHelper {

    private val fontProvider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    val fontOptions = listOf(
        FontOption("default", "Default", "System"),
        FontOption("playfair", "Playfair Display", "Serif"),
        FontOption("courier", "Courier Prime", "Mono"),
        FontOption("cormorant", "Cormorant Garamond", "Elegant"),
        FontOption("inter", "Inter", "Clean"),
        FontOption("caveat", "Caveat", "Handwritten"),
        FontOption("lora", "Lora", "Literary")
    )

    fun getFontFamily(fontKey: String): FontFamily {
        return when (fontKey.lowercase()) {
            "playfair", "playfair display", "serif" ->
                FontFamily(Font(googleFont = GoogleFont("Playfair Display"), fontProvider = fontProvider))
            "courier", "courier prime", "mono" ->
                FontFamily(Font(googleFont = GoogleFont("Courier Prime"), fontProvider = fontProvider))
            "cormorant", "cormorant garamond" ->
                FontFamily(Font(googleFont = GoogleFont("Cormorant Garamond"), fontProvider = fontProvider))
            "inter", "inter clean", "sans" ->
                FontFamily(Font(googleFont = GoogleFont("Inter"), fontProvider = fontProvider))
            "caveat", "caveat handwritten" ->
                FontFamily(Font(googleFont = GoogleFont("Caveat"), fontProvider = fontProvider))
            "lora", "lora literary" ->
                FontFamily(Font(googleFont = GoogleFont("Lora"), fontProvider = fontProvider))
            else -> FontFamily.Default
        }
    }
}
