package com.primaloptima.scribe.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import coil3.BitmapImage
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.ThemeDataStoreRepo
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.graphics.luminance

val LocalHazeState = compositionLocalOf<HazeState?> { null }
val LocalAppTheme = compositionLocalOf<AppTheme?> { null }
val LocalBgAnalysisBitmap = compositionLocalOf<Bitmap?> { null }
val LocalScreenSize = compositionLocalOf { Pair(1080f, 1920f) }

/**
 * Always holds the fully-opaque theme surface color, even when a background image
 * is active and the color scheme's surface is set to alpha=0 for glass effects.
 * Use this for Dropdowns, Dialogs, and any popup that must never be see-through.
 */
val LocalSolidSurface = compositionLocalOf { Color.White }

fun autoTextColor(bg: Color): Color {
    val luminance = bg.luminance()
    return if (luminance > 0.5f) Color.Black else Color.White
}

@Composable
fun Modifier.frostedBar(hazeState: HazeState?): Modifier {
    // On Android 12+ (API 31+) use real GPU blur via Haze.
    // On older Android, RenderEffect is unavailable so we fall back to a
    // semi-transparent tint using the theme's actual surface color instead
    // of hard-coded black, so the bar blends naturally with the active theme.
    val solidSurface = LocalSolidSurface.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (hazeState != null) {
            this.hazeChild(state = hazeState, style = HazeMaterials.thin())
        } else {
            this
        }
    } else {
        this.background(solidSurface.copy(alpha = 0.82f))
    }
}

/**
 * Applies a frosted-glass effect to a FAB (or any component) when a whole-app
 * background image is active.  On Android 12+ this is a real GPU blur via Haze;
 * on older devices it falls back to a semi-transparent surface tint.
 * When there is no background image the modifier is a no-op.
 */
@Composable
fun Modifier.frostedFab(hazeState: HazeState?): Modifier {
    val theme = LocalAppTheme.current
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = theme?.bgImageUri?.isNotEmpty() == true &&
            (theme.bgMode == "image" || theme.bgMode == "blurred")
    return if (!hasBgImage || hazeState == null) {
        this
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.hazeChild(state = hazeState, style = HazeMaterials.regular())
    } else {
        this.background(solidSurface.copy(alpha = 0.75f), shape = androidx.compose.foundation.shape.CircleShape)
    }
}

fun parseComposeColor(hex: String, fallback: Color = Color.Black): Color {
    return try {
        Color(ThemeManager.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun ScribeComposeTheme(
    appTheme: AppTheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val resolvedTheme = if (appTheme != null) {
        appTheme
    } else {
        val repo = remember { ThemeDataStoreRepo(context) }
        val activeThemeId by repo.activeThemeIdFlow.collectAsState(
            initial = themeManager.activeTheme().id
        )
        val customThemesJson by repo.customThemesJsonFlow.collectAsState(initial = "[]")
        remember(activeThemeId, customThemesJson) {
            themeManager.allThemes().firstOrNull { it.id == activeThemeId }
                ?: DefaultThemes.all.first()
        }
    }

    val bgUri = resolvedTheme.backgroundImageUri
    val hasBgImage = !bgUri.isNullOrEmpty() && resolvedTheme.bgMode != "color"
    val view = LocalView.current
    val screenWidthPx = remember(view) { view.resources.displayMetrics.widthPixels.toFloat() }
    val screenHeightPx = remember(view) { view.resources.displayMetrics.heightPixels.toFloat() }
    var analysisBitmap by remember(bgUri, hasBgImage) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(bgUri, hasBgImage) {
        if (!hasBgImage || bgUri.isNullOrEmpty()) {
            analysisBitmap = null
            return@LaunchedEffect
        }
        analysisBitmap = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(bgUri)
                    .size(32, 32)
                    .allowHardware(false) // Prevents hardware bitmap; getPixel() requires software config
                    .build()
                (ImageLoader(context).execute(request).image as? BitmapImage)?.bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    val bg = parseComposeColor(resolvedTheme.colors.background, Color(0xFFFAFAF7))
    val surface = parseComposeColor(resolvedTheme.colors.surface, Color.White)
    val configuredText = parseComposeColor(resolvedTheme.colors.text, Color(0xFF1A1A1A))
    val configuredAccent = parseComposeColor(resolvedTheme.colors.accent, Color(0xFF333333))
    val border = parseComposeColor(resolvedTheme.colors.border, Color(0xFFE0E0D8))
    val surfaceVariant = parseComposeColor(resolvedTheme.colors.surface, surface)

    val isLight = !resolvedTheme.isDark
    val defaultText = if (resolvedTheme.isDark) Color.White else Color(0xFF1A1A1A)
    val defaultAccent = if (resolvedTheme.isDark) Color(0xFFE0E0E0) else Color(0xFF333333)
    val imageContrast = analysisBitmap?.let {
        contrastingTextColor(
            bitmap = it,
            screenRect = Rect(0f, 0f, screenWidthPx, screenHeightPx),
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }
    val text = if (hasBgImage && configuredText == defaultText && imageContrast != null) imageContrast else configuredText
    val accentIcons = if (hasBgImage && configuredAccent == defaultAccent && imageContrast != null) imageContrast else configuredAccent
    val onPrimaryColor = if (accentIcons.luminance() < 0.5f) Color.White else Color.Black

    val rawColorScheme: ColorScheme = if (isLight) {
        lightColorScheme(
            primary = accentIcons,
            onPrimary = onPrimaryColor,
            primaryContainer = surface,
            onPrimaryContainer = text,
            secondary = accentIcons,
            onSecondary = onPrimaryColor,
            // KEY: secondaryContainer was unset → M3 default is purple(#E8DEF8)
            // Setting it to surfaceVariant gives a themed, warm tint instead.
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = accentIcons,
            onTertiary = onPrimaryColor,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = text,
            surfaceContainerLowest = bg,
            surfaceContainerLow = bg,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            // KEY: surfaceContainerHighest was unset → M3 default is lavender(#E6E0E9)
            // Card() in BOM 2026.06.00 uses this slot by default.
            surfaceContainerHighest = surfaceVariant,
            // Keep tonal surface tint on-theme (prevents extra purple tinting)
            surfaceTint = accentIcons,
            inverseSurface = text,
            inverseOnSurface = bg,
            inversePrimary = accentIcons,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.32f)
        )
    } else {
        darkColorScheme(
            primary = accentIcons,
            onPrimary = onPrimaryColor,
            primaryContainer = surface,
            onPrimaryContainer = text,
            secondary = accentIcons,
            onSecondary = onPrimaryColor,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = accentIcons,
            onTertiary = onPrimaryColor,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = text,
            surfaceContainerLowest = bg,
            surfaceContainerLow = bg,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceVariant,
            surfaceTint = accentIcons,
            inverseSurface = text,
            inverseOnSurface = bg,
            inversePrimary = accentIcons,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.32f)
        )
    }

    val duration = 400
    val animSpec = tween<Color>(durationMillis = duration)

    val animPrimary by animateColorAsState(rawColorScheme.primary, animSpec, label = "primary")
    val animOnPrimary by animateColorAsState(rawColorScheme.onPrimary, animSpec, label = "onPrimary")
    val animBg by animateColorAsState(rawColorScheme.background, animSpec, label = "bg")
    val animOnBg by animateColorAsState(rawColorScheme.onBackground, animSpec, label = "onBg")
    val animSurface by animateColorAsState(rawColorScheme.surface, animSpec, label = "surface")
    val animOnSurface by animateColorAsState(rawColorScheme.onSurface, animSpec, label = "onSurface")
    val animSurfaceVariant by animateColorAsState(rawColorScheme.surfaceVariant, animSpec, label = "surfaceVariant")
    val animOnSurfaceVariant by animateColorAsState(rawColorScheme.onSurfaceVariant, animSpec, label = "onSurfaceVariant")
    val animOutline by animateColorAsState(rawColorScheme.outline, animSpec, label = "outline")

    val showWholeAppBg = resolvedTheme.themeScope == "whole_app" && hasBgImage

    // When a whole-app background image is active, surfaces must be transparent so
    // the image shows through and the Haze blur effect works. However, we must NOT
    // use Color.Transparent (= ARGB 0,0,0,0 — transparent BLACK) because any
    // downstream call like surface.copy(alpha = 0.95f) would produce a near-opaque
    // BLACK instead of the theme colour. Instead, we zero only the alpha channel
    // while keeping the RGB channels intact, so copy(alpha = X) restores the
    // correct colour at the requested opacity.
    val glassySurface        = if (showWholeAppBg) animSurface.copy(alpha = 0f)        else animSurface
    val glassySurfaceVariant = if (showWholeAppBg) animSurfaceVariant.copy(alpha = 0f) else animSurfaceVariant
    val glassyBg             = if (showWholeAppBg) animBg.copy(alpha = 0f)             else animBg

    val animatedColorScheme = rawColorScheme.copy(
        primary = animPrimary,
        onPrimary = animOnPrimary,
        primaryContainer = glassySurface,
        onPrimaryContainer = animOnSurface,
        secondary = animPrimary,
        onSecondary = animOnPrimary,
        secondaryContainer = glassySurfaceVariant,
        onSecondaryContainer = animOnSurface,
        tertiary = animPrimary,
        onTertiary = animOnPrimary,
        tertiaryContainer = glassySurfaceVariant,
        onTertiaryContainer = animOnSurface,
        background = glassyBg,
        onBackground = animOnBg,
        surface = glassySurface,
        onSurface = animOnSurface,
        surfaceVariant = glassySurfaceVariant,
        onSurfaceVariant = animOnSurfaceVariant,
        surfaceContainerLowest = glassyBg,
        surfaceContainerLow = glassyBg,
        surfaceContainer = glassySurface,
        surfaceContainerHigh = glassySurface,
        surfaceContainerHighest = glassySurfaceVariant,
        outline = animOutline,
        outlineVariant = animOutline
    )

    val window = (LocalContext.current as? Activity)?.window
    SideEffect {
        window?.let { win ->
            val barColor = (if (showWholeAppBg) Color.Transparent else animSurface).toArgb()
            win.statusBarColor = barColor
            win.navigationBarColor = barColor
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    val hazeState = remember { HazeState() }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        content = {
            CompositionLocalProvider(
                LocalHazeState provides hazeState,
                LocalAppTheme provides resolvedTheme,
                LocalBgAnalysisBitmap provides analysisBitmap,
                LocalScreenSize provides Pair(screenWidthPx, screenHeightPx),
                // Always the fully-opaque surface colour — safe to use in popups
                // and menus that must not be see-through.
                LocalSolidSurface provides animSurface
            ) {
                val bgOpacity = resolvedTheme.backgroundImageOpacity ?: 0.35f
                val bgMode = resolvedTheme.bgMode
                val blurIntensity = resolvedTheme.blurIntensity

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (showWholeAppBg) Color.Transparent else animBg)
                ) {
                    if (showWholeAppBg) {
                        AsyncImage(
                            model = bgUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (bgMode == "blurred" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurIntensity > 0f) {
                                        Modifier.graphicsLayer {
                                            val radiusPx = blurIntensity * density
                                            if (radiusPx > 0f) {
                                                renderEffect = AndroidRenderEffect
                                                    .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                                                    .asComposeRenderEffect()
                                            }
                                        }
                                    } else Modifier
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bg.copy(alpha = bgOpacity))
                        )
                    }

                    content()
                }
            }
        }
    )
}
