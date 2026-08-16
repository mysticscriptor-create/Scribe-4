package com.primaloptima.scribe.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.BitmapBlur
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.withContext
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.AppTheme
import kotlinx.serialization.encodeToString
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import java.io.File
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditScreen(
    themeId: String,
    vm: ThemeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themes by vm.themes.collectAsStateWithLifecycle()

    val originalTheme = remember(themes, themeId) {
        themes.firstOrNull { it.id == themeId } ?: DefaultThemes.all.first()
    }

    var name by remember(originalTheme) { mutableStateOf(originalTheme.name) }
    var bgHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.background) }
    var textHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.text) }
    var accentHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.accent) }

    var bgMode by remember(originalTheme) { mutableStateOf(originalTheme.bgMode) }
    var bgUri by remember(originalTheme) { mutableStateOf(originalTheme.backgroundImageUri) }
    var bgOriginalUri by remember(originalTheme) { mutableStateOf(originalTheme.backgroundImageOriginalUri) }
    var bgOpacity by remember(originalTheme) { mutableFloatStateOf(originalTheme.backgroundImageOpacity ?: 0.35f) }
    var blurIntensity by remember(originalTheme) { mutableFloatStateOf(originalTheme.blurIntensity) }
    var frostedGlassEnabled by remember(originalTheme) { mutableStateOf(originalTheme.frostedGlassEnabled) }
    var frostedTintEnabled by remember(originalTheme) { mutableStateOf(originalTheme.frostedTintEnabled) }
    var frostedBlurRadius by remember(originalTheme) { mutableFloatStateOf(originalTheme.frostedBlurRadius) }

    var fontFamily by remember(originalTheme) { mutableStateOf(originalTheme.fontFamily) }
    var fontSize by remember(originalTheme) { mutableFloatStateOf(originalTheme.fontSize.toFloat()) }
    var lineHeight by remember(originalTheme) { mutableFloatStateOf(originalTheme.lineHeight) }
    var paragraphSpacing by remember(originalTheme) { mutableFloatStateOf(originalTheme.paragraphSpacing.toFloat()) }
    var sideMargins by remember(originalTheme) { mutableFloatStateOf(originalTheme.paddingHorizontal.toFloat()) }

    var textAlignment by remember(originalTheme) { mutableStateOf(originalTheme.textAlignment) }
    var themeScope by remember(originalTheme) { mutableStateOf(originalTheme.themeScope) }
    var emoji by remember(originalTheme) { mutableStateOf(originalTheme.emoji ?: "🖊️") }

    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    // Average luminance of the background image — computed once at crop-confirm time
    // via Coil (same pipeline as ScribeComposeTheme) so content:// URIs work correctly.
    // -1f means not yet computed (no image, or pre-existing theme).
    var bgLuminance by remember(originalTheme) { mutableFloatStateOf(originalTheme.savedBgLuminance) }

    // Crop screen: shown after the user picks a new background image
    var showCropScreen by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<String?>(null) }

    val view = LocalView.current
    val hazeState = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }
    // True while computeBgLuminance is running after a crop confirm.
    // Save buttons are disabled during this window so savedBgLuminance is
    // never written with a stale value — the root cause of the intermittent
    // "text reverts to theme colour" bug.
    var isLuminancePending by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = showEmojiDialog || activeColorPickerTarget != null
        LaunchedEffect(anyDialogOpen) {
            if (anyDialogOpen && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = frostedBlurRadius.toInt().coerceIn(1, 25)) }
                    // applyFrostedGlassLook is now called inside blurBitmap — no chain needed.
                }
            } else if (!anyDialogOpen) {
                dialogCaptured = false
                dialogOneShotBitmap = null
            }
        }
    }

    val scope = rememberCoroutineScope()

    // Image picker: copies to internal storage, then opens the crop screen so
    // the user can choose which region of the image to display as background.
    val bgImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                // Copy to internal storage first so the crop screen has a stable
                // file:// URI it can read (SAF content:// may lose permission).
                val localUri = SAFHelper.copyBgImageToInternalStorage(context, uri, themeId)
                val stableUri = (localUri ?: uri).toString()
                pendingCropUri = stableUri
                // Store original so user can re-crop later without quality loss
                bgOriginalUri = stableUri
                showCropScreen = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            ScribeTopBar(
                title             = if (originalTheme.builtIn) "View Theme" else "Edit Theme",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions           = if (originalTheme.builtIn) emptyList() else listOf(
                    ScribeBarAction(Icons.Default.Check, "Save") {
                        if (!isLuminancePending) {
                            val updated = originalTheme.copy(
                                name = name,
                                fontSize = fontSize.toInt(),
                                lineHeight = lineHeight,
                                paragraphSpacing = paragraphSpacing.toInt(),
                                paddingHorizontal = sideMargins.toInt(),
                                fontFamily = fontFamily,
                                backgroundImageUri = bgUri,
                                backgroundImageOriginalUri = bgOriginalUri,
                                backgroundImageOpacity = bgOpacity,
                                bgMode = bgMode,
                                blurIntensity = blurIntensity,
                                frostedGlassEnabled = frostedGlassEnabled,
                                frostedTintEnabled = frostedTintEnabled,
                                frostedBlurRadius = frostedBlurRadius,
                                textAlignment = textAlignment,
                                themeScope = themeScope,
                                emoji = emoji,
                                savedBgLuminance = bgLuminance,
                                colors = originalTheme.colors.copy(
                                    background = bgHex,
                                    surface = bgHex,
                                    text = textHex,
                                    accent = accentHex,
                                    toolbar = bgHex,
                                    toolbarText = textHex
                                )
                            )
                            vm.save(updated)
                            Toast.makeText(context, "Theme saved", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                )
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
        ) {
            // SECTION 1: APPEARANCE
            item {
                SectionHeader("APPEARANCE")
            }

            // Live Preview
            item {
                Text("Live Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LivePreviewCard(
                    themeName = if (emoji.isNotBlank()) "$emoji $name" else name,
                    bgHex = bgHex,
                    textHex = textHex,
                    accentHex = accentHex,
                    bgMode = bgMode,
                    bgUri = bgUri,
                    bgOpacity = bgOpacity,
                    blurIntensity = blurIntensity,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    textAlignment = textAlignment,
                    sideMargins = sideMargins
                )
            }

            // Background options
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Background Image", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        // Image picker row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(onClick = { bgImagePicker.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (bgUri.isNullOrEmpty()) "Pick Image" else "Change Image")
                            }
                            if (!bgUri.isNullOrEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Re-open crop screen for the current image
                                    TextButton(onClick = {
                                        pendingCropUri = bgOriginalUri ?: bgUri
                                        showCropScreen = true
                                    }) {
                                        Icon(
                                            Icons.Default.Crop,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Crop")
                                    }
                                    TextButton(onClick = {
                                        bgUri = null
                                        bgMode = "color"
                                    }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Image-specific settings (only when an image is set)
                        if (!bgUri.isNullOrEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            // Blur background toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Blur Background", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Soften the image with a blur effect",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = bgMode == "blurred",
                                    onCheckedChange = { blur ->
                                        bgMode = if (blur) "blurred" else "image"
                                    }
                                )
                            }

                            // Blur intensity slider — only when blur is on
                            if (bgMode == "blurred") {
                                Text(
                                    "Blur Intensity: ${blurIntensity.toInt()} dp",
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = blurIntensity,
                                    onValueChange = { blurIntensity = it },
                                    valueRange = 0f..30f
                                )
                            }

                            // Overlay opacity
                            Text(
                                "Overlay Opacity: ${(bgOpacity * 100).toInt()}%",
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = bgOpacity,
                                onValueChange = { bgOpacity = it },
                                valueRange = 0.0f..0.90f
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            // Frosted glass toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Frosted Glass", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Apply glass morphism to bars, drawers, and cards",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = frostedGlassEnabled,
                                    onCheckedChange = { frostedGlassEnabled = it }
                                )
                            }

                            // Frosted glass sub-settings — only visible when frosted glass is on
                            if (frostedGlassEnabled) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                                // Tint toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Glass Tint", fontWeight = FontWeight.Medium)
                                        Text(
                                            "Overlay a surface colour on the blur. Turn off for pure glass.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = frostedTintEnabled,
                                        onCheckedChange = { frostedTintEnabled = it }
                                    )
                                }

                                // Blur radius slider
                                Text(
                                    "Glass Blur: ${frostedBlurRadius.toInt()} dp" +
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                                            " (applies on next theme load)" else "",
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = frostedBlurRadius,
                                    onValueChange = { frostedBlurRadius = it },
                                    valueRange = 0f..40f
                                )
                            }
                        }
                    }
                }
            }

            // Theme Colors
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Theme Colors", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ColorTile(
                                label = "Background",
                                hex = bgHex,
                                onClick = { activeColorPickerTarget = ColorPickerTarget.BACKGROUND }
                            )
                            ColorTile(
                                label = "Text",
                                hex = textHex,
                                onClick = { activeColorPickerTarget = ColorPickerTarget.TEXT }
                            )
                            ColorTile(
                                label = "Accent",
                                hex = accentHex,
                                onClick = { activeColorPickerTarget = ColorPickerTarget.ACCENT }
                            )
                        }
                    }
                }
            }

            // SECTION 2: TYPOGRAPHY
            item {
                SectionHeader("TYPOGRAPHY")
            }

            // Font family selector
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Font Family", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(FontHelper.fontOptions) { fontOpt ->
                                val isSelected = fontFamily.equals(fontOpt.key, ignoreCase = true) ||
                                        fontFamily.equals(fontOpt.name, ignoreCase = true)
                                Surface(
                                    selected = isSelected,
                                    onClick = { fontFamily = fontOpt.key },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.height(64.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Scribe",
                                            fontFamily = FontHelper.getFontFamily(fontOpt.key),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = fontOpt.name,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Typography sliders
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Typography & Margins", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        Text("Font Size: ${fontSize.toInt()} sp")
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 12f..28f
                        )

                        Text("Line Height: ${String.format("%.2f", lineHeight)}")
                        Slider(
                            value = lineHeight,
                            onValueChange = { lineHeight = it },
                            valueRange = 1.2f..2.5f
                        )

                        Text("Paragraph Spacing: ${paragraphSpacing.toInt()} dp")
                        Slider(
                            value = paragraphSpacing,
                            onValueChange = { paragraphSpacing = it },
                            valueRange = 0f..24f
                        )

                        Text("Side Margins: ${sideMargins.toInt()} dp")
                        Slider(
                            value = sideMargins,
                            onValueChange = { sideMargins = it },
                            valueRange = 0f..32f
                        )
                    }
                }
            }

            // SECTION 3: LAYOUT
            item {
                SectionHeader("LAYOUT")
            }

            // Text alignment
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Text Alignment", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val alignOptions = listOf(
                                "left" to Icons.AutoMirrored.Filled.FormatAlignLeft,
                                "justified" to Icons.Default.FormatAlignJustify,
                                "center" to Icons.Default.FormatAlignCenter
                            )
                            alignOptions.forEach { (key, icon) ->
                                OutlinedIconToggleButton(
                                    checked = textAlignment == key,
                                    onCheckedChange = { textAlignment = key }
                                ) {
                                    Icon(icon, contentDescription = key)
                                }
                            }
                        }
                    }
                }
            }

            // Theme scope toggle
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Theme Scope", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val scopes = listOf("editor_only" to "Editor Only", "whole_app" to "Whole App")
                            scopes.forEachIndexed { index, (scopeKey, label) ->
                                SegmentedButton(
                                    selected = themeScope == scopeKey,
                                    onClick = { themeScope = scopeKey },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: DETAILS
            if (!originalTheme.builtIn) {
                item {
                    SectionHeader("DETAILS")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Name & Emoji", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showEmojiDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Theme Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Save button
            item {
                val saveAction = {
                    val updated = originalTheme.copy(
                        name = name,
                        fontSize = fontSize.toInt(),
                        lineHeight = lineHeight,
                        paragraphSpacing = paragraphSpacing.toInt(),
                        paddingHorizontal = sideMargins.toInt(),
                        fontFamily = fontFamily,
                        backgroundImageUri = bgUri,
                        backgroundImageOriginalUri = bgOriginalUri,
                        backgroundImageOpacity = bgOpacity,
                        bgMode = bgMode,
                        blurIntensity = blurIntensity,
                        frostedGlassEnabled = frostedGlassEnabled,
                        frostedTintEnabled = frostedTintEnabled,
                        frostedBlurRadius = frostedBlurRadius,
                        textAlignment = textAlignment,
                        themeScope = themeScope,
                        emoji = emoji,
                        savedBgLuminance = bgLuminance,
                        colors = originalTheme.colors.copy(
                            background = bgHex,
                            surface = bgHex,
                            text = textHex,
                            accent = accentHex,
                            toolbar = bgHex,
                            toolbarText = textHex
                        )
                    )
                    vm.save(updated)
                    Toast.makeText(context, "Theme saved", Toast.LENGTH_SHORT).show()
                    onBack()
                }

                Button(
                    onClick = saveAction,
                    enabled = !isLuminancePending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLuminancePending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analysing image…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Export button
            if (!originalTheme.builtIn) {
                item {
                    Button(
                        onClick = {
                            val currentThemeToExport = originalTheme.copy(
                                name = name,
                                fontSize = fontSize.toInt(),
                                lineHeight = lineHeight,
                                paragraphSpacing = paragraphSpacing.toInt(),
                                paddingHorizontal = sideMargins.toInt(),
                                fontFamily = fontFamily,
                                backgroundImageUri = bgUri,
                                backgroundImageOriginalUri = bgOriginalUri,
                                backgroundImageOpacity = bgOpacity,
                                bgMode = bgMode,
                                blurIntensity = blurIntensity,
                                frostedGlassEnabled = frostedGlassEnabled,
                                frostedTintEnabled = frostedTintEnabled,
                                frostedBlurRadius = frostedBlurRadius,
                                textAlignment = textAlignment,
                                themeScope = themeScope,
                                emoji = emoji,
                                colors = originalTheme.colors.copy(
                                    background = bgHex,
                                    surface = bgHex,
                                    text = textHex,
                                    accent = accentHex
                                )
                            )
                            exportThemeJson(context, currentThemeToExport)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Theme")
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
        // Modal Color Picker Bottom Sheet
        activeColorPickerTarget?.let { target ->
            val title = when (target) {
                ColorPickerTarget.BACKGROUND -> "Background Color"
                ColorPickerTarget.TEXT -> "Text Color"
                ColorPickerTarget.ACCENT -> "Accent Color"
            }
            val currentHex = when (target) {
                ColorPickerTarget.BACKGROUND -> bgHex
                ColorPickerTarget.TEXT -> textHex
                ColorPickerTarget.ACCENT -> accentHex
            }

            ColorPickerBottomSheet(
                title = title,
                initialHex = currentHex,
                onDismiss = { activeColorPickerTarget = null },
                onColorSelected = { newHex ->
                    when (target) {
                        ColorPickerTarget.BACKGROUND -> bgHex = newHex
                        ColorPickerTarget.TEXT -> textHex = newHex
                        ColorPickerTarget.ACCENT -> accentHex = newHex
                    }
                }
            )
        }

        // Emoji Picker Dialog
        if (showEmojiDialog) {
        val emojis = listOf("🖊️", "📖", "🌙", "⭐", "🌿", "🔥", "🌊", "🌸", "🏔️", "🌌", "📜", "✨", "🎭", "🌅", "🍂", "❄️", "🌙", "🪶", "🕯️", "🌺")
        FrostedDialog(
            onDismissRequest = { showEmojiDialog = false },
            title = { Text("Theme Badge") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(emojis) { em ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    emoji = em
                                    showEmojiDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(em, fontSize = 22.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmojiDialog = false }) { Text("Close") }
            }
        )
        }
    }

    // ── Crop Screen Overlay ───────────────────────────────────────────────────
    // Shown full-screen after the user picks a new background image.
    // Rendered inside a Box so it properly covers the Scaffold on all devices,
    // including tablets, split-screen, and Android 15 windowing modes.
    if (showCropScreen && pendingCropUri != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImageCropScreen(
                imageUri = pendingCropUri!!,
                themeId = themeId,
                onConfirm = { croppedUri ->
                    bgOriginalUri = pendingCropUri  // preserve the full-res original
                    bgUri = croppedUri
                    if (bgMode == "color") bgMode = "image"
                    showCropScreen = false
                    pendingCropUri = null
                    // Compute average luminance from the freshly-saved image using Coil —
                    // the same pipeline ScribeComposeTheme uses, so content:// URIs are
                    // handled correctly on all API levels. Stored in bgLuminance so that
                    // both save sites include it in savedBgLuminance without re-processing.
                    // isLuminancePending gates the save buttons so the user can't save
                    // before this finishes (which would write a stale savedBgLuminance
                    // and cause the text colour to auto-compute from the wrong luminance).
                    isLuminancePending = true
                    scope.launch {
                        bgLuminance = computeBgLuminance(context, croppedUri)
                        isLuminancePending = false
                    }
                },
                onCancel = {
                    showCropScreen = false
                    pendingCropUri = null
                }
            )
        }
    }
    } // end outer Box
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 1.sp
        )
    }
}

enum class ColorPickerTarget {
    BACKGROUND, TEXT, ACCENT
}

@Composable
private fun ColorTile(
    label: String,
    hex: String,
    onClick: () -> Unit
) {
    val color = parseComposeColor(hex, Color.Gray)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val squareSize = 8.dp.toPx()
                val rows = (size.height / squareSize).toInt() + 1
                val cols = (size.width / squareSize).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val isDark = (r + c) % 2 == 0
                        drawRect(
                            color = if (isDark) Color(0xFFE0E0E0) else Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(c * squareSize, r * squareSize),
                            size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Text(hex, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LivePreviewCard(
    themeName: String,
    bgHex: String,
    textHex: String,
    accentHex: String,
    bgMode: String,
    bgUri: String?,
    bgOpacity: Float,
    blurIntensity: Float,
    fontFamily: String,
    fontSize: Float,
    lineHeight: Float,
    textAlignment: String,
    sideMargins: Float
) {
    val bgColor = parseComposeColor(bgHex, Color.White)
    val textColor = parseComposeColor(textHex, Color.Black)
    val accentColor = parseComposeColor(accentHex, Color.Blue)
    val font = FontHelper.getFontFamily(fontFamily)

    val textAlign = when (textAlignment) {
        "justified" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Left
    }

    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    val cursorAlpha by animateFloatAsState(
        targetValue = if (cursorVisible) 1f else 0f,
        animationSpec = tween(150),
        label = "cursorAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image if set
            if ((bgMode == "image" || bgMode == "blurred") && !bgUri.isNullOrEmpty()) {
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
                // Overlay: on API 31+ the GPU blur handles it; on older devices we
                // compensate by boosting the overlay opacity when blur intensity is raised,
                // simulating the visual weight the blur would normally add.
                val overlayAlpha = if (bgMode == "blurred" && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurIntensity > 0f) {
                    (bgOpacity + blurIntensity / 35f).coerceIn(0f, 0.90f)
                } else {
                    bgOpacity
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }

            // Preview Layout with Fake Top Bar, Content, and Fake Bottom Toolbar
            Column(modifier = Modifier.fillMaxSize()) {
                // Fake Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                    Text(themeName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                }

                // Main Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = sideMargins.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Experience seamless distraction-free writing.",
                            color = textColor,
                            fontFamily = font,
                            fontSize = (fontSize * 0.7f).sp,
                            lineHeight = (fontSize * 0.7f * lineHeight).sp,
                            textAlign = textAlign,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .graphicsLayer { alpha = cursorAlpha }
                                .background(accentColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Accent & Highlight Color",
                        color = accentColor,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSize * 0.75f).sp
                    )
                }

                // Fake Bottom Toolbar Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Icon(Icons.Default.FormatItalic, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomColorPicker(
    currentColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(currentColor) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(currentColor.toArgb(), array)
        array
    }
    var hue by remember(currentColor) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(currentColor) { mutableFloatStateOf(hsv[1]) }
    var valVal by remember(currentColor) { mutableFloatStateOf(hsv[2]) }

    fun update(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        valVal = v
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        onColorChanged(Color(argb))
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            val presets = listOf("#FAFAF7", "#1E1E2E", "#0D1117", "#000000", "#3366FF", "#E11D48", "#10B981", "#F59E0B", "#8B5CF6")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { p ->
                    val c = parseComposeColor(p)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable {
                                onColorChanged(c)
                            }
                    )
                }
            }
        }

        Column {
            Text("Hue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = hue,
                onValueChange = { update(it, sat, valVal) },
                valueRange = 0f..360f
            )
        }

        Column {
            Text("Saturation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sat,
                onValueChange = { update(hue, it, valVal) },
                valueRange = 0f..1f
            )
        }

        Column {
            Text("Brightness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = valVal,
                onValueChange = { update(hue, sat, it) },
                valueRange = 0f..1f
            )
        }
    }
}

@Composable
private fun ColorPickerBottomSheet(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var selectedColor by remember { mutableStateOf(parseComposeColor(initialHex, Color.Red)) }
    var hexText by remember { mutableStateOf(initialHex) }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )

                Text(
                    text = hexText.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                CustomColorPicker(
                    modifier = Modifier.fillMaxWidth(),
                    currentColor = selectedColor,
                    onColorChanged = { color ->
                        selectedColor = color
                        val argb = color.toArgb()
                        hexText = String.format("#%06X", 0xFFFFFF and argb)
                    }
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        if (input.startsWith("#") && (input.length == 7 || input.length == 9)) {
                            try {
                                selectedColor = parseComposeColor(input)
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(hexText)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    )
}

private fun exportThemeJson(context: Context, theme: AppTheme) {
    try {
        val json = AppJson.encodeToString(theme)
        val fileName = "${theme.name.lowercase().replace(Regex("[^a-z0-9]"), "_")}_theme.json"
        val dir = File(context.cacheDir, "exported_themes").also { it.mkdirs() }
        val file = File(dir, fileName).also { it.writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Theme: ${theme.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Image Crop Screen
//
// Full-screen overlay that lets the user position and resize a crop window.
//
// Key behaviours:
//  • Crop box aspect ratio is always locked to the device screen ratio.
//  • User can drag the box to pan, drag a corner handle to resize, or
//    pinch with two fingers to zoom the box in/out — all keep ratio locked.
//  • Box is always clamped inside the visible image area.
//  • On confirm, the fraction coordinates are mapped to real bitmap pixels
//    (accounting for letterbox/pillarbox offset) before cropping.
//  • Saved JPEG is scaled to exact screen pixels.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ImageCropScreen(
    imageUri: String,
    themeId: String,
    onConfirm: (croppedUri: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    // Screen pixel dimensions — used to lock crop aspect ratio and scale the saved bitmap.
    // The app is edge-to-edge so displayMetrics gives the true full-screen size.
    val screenW = remember(view) { view.resources.displayMetrics.widthPixels.toFloat() }
    val screenH = remember(view) { view.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f) }
    val screenAspect = screenW / screenH

    // Real intrinsic dimensions of the source image (bounds-only decode, no full bitmap).
    var intrinsicW by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var intrinsicH by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    // True once intrinsic size is known. "Use this crop" is disabled until then so the
    // user can't save with wrong fractions computed from the placeholder 1×1 defaults.
    var intrinsicsLoaded by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        intrinsicsLoaded = false
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val src = android.net.Uri.parse(imageUri)
                val stream = if (src.scheme == "file") java.io.File(src.path!!).inputStream()
                             else context.contentResolver.openInputStream(src)
                stream?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    intrinsicW = opts.outWidth.toFloat()
                    intrinsicH = opts.outHeight.toFloat()
                }
            } catch (_: Exception) {}
        }
        intrinsicsLoaded = true
    }

    // ── Crop box state ────────────────────────────────────────────────────────
    // boxLeft / boxTop : top-left corner as fractions of the DISPLAYED IMAGE rect (0..1).
    // boxW             : width as fraction of the displayed image width.
    // boxH()           : derived from boxW so aspect ratio NEVER drifts.
    //
    // All fractions are relative to the displayed image rect (after letterboxing),
    // NOT to the full screen — this is critical for correct pixel mapping on save.

    // Initial box: as large as possible while fitting inside the displayed image.
    // boxH (image-fraction) = boxW * imageAspect / screenAspect.
    // For boxH <= 1.0: boxW <= screenAspect / imageAspect.
    // For boxW <= 1.0: always enforced by coerceIn.
    // So maxBoxW = min(1f, screenAspect / imageAspect) covers both cases.
    val imageAspect = (intrinsicW / intrinsicH.coerceAtLeast(1f)).coerceAtLeast(0.01f)
    val initialBoxW = remember(screenAspect, intrinsicW, intrinsicH) {
        (screenAspect / imageAspect).coerceIn(0.1f, 1f)
    }
    val initialBoxH = remember(initialBoxW, screenAspect, intrinsicW, intrinsicH) {
        (initialBoxW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
    }

    var boxLeft by remember(initialBoxW) { androidx.compose.runtime.mutableFloatStateOf((1f - initialBoxW) / 2f) }
    var boxTop  by remember(initialBoxH) { androidx.compose.runtime.mutableFloatStateOf((1f - initialBoxH) / 2f) }
    var boxW    by remember(initialBoxW) { androidx.compose.runtime.mutableFloatStateOf(initialBoxW) }

    // Height is ALWAYS derived — never stored — so ratio can never drift.
    // Correct formula: visual pixels = boxW*imgW wide, boxH*imgH tall.
    // For visual ratio = screenAspect: boxH = boxW * imageAspect / screenAspect.
    fun boxH(): Float = (boxW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)

    // Resize the box to a new width, anchoring around a chosen pivot (0=left, 0.5=center, 1=right
    // for X; same for Y). Clamps so box stays inside [0,1].
    fun resizeBox(newW: Float, pivotX: Float = 0.5f, pivotY: Float = 0.5f) {
        val maxW = (screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceIn(0.05f, 1f)
        val clampedW = newW.coerceIn(0.05f, maxW)
        val newH = (clampedW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
        val oldH = boxH()
        // Shift left/top so the pivot point stays fixed.
        val newLeft = (boxLeft + (boxW - clampedW) * pivotX).coerceIn(0f, maxOf(0f, 1f - clampedW))
        val newTop  = (boxTop  + (oldH  - newH)    * pivotY).coerceIn(0f, maxOf(0f, 1f - newH))
        boxW    = clampedW
        boxLeft = newLeft
        boxTop  = newTop
    }

    // ── Gesture state ─────────────────────────────────────────────────────────
    var activeCorner  by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isDraggingBox by remember { androidx.compose.runtime.mutableStateOf(false) }
    var isSaving      by remember { androidx.compose.runtime.mutableStateOf(false) }

    // ── Layout helper ─────────────────────────────────────────────────────────
    // Returns [imgX, imgY, imgW, imgH] — the visible image rect inside the display,
    // accounting for ContentScale.Fit letterboxing / pillarboxing.
    fun imageRect(displayW: Float, displayH: Float): FloatArray {
        val imageAspect = intrinsicW / intrinsicH.coerceAtLeast(1f)
        val viewAspect  = displayW / displayH.coerceAtLeast(1f)
        return if (imageAspect > viewAspect) {
            val w = displayW; val h = w / imageAspect
            floatArrayOf((displayW - w) / 2f, (displayH - h) / 2f, w, h)
        } else {
            val h = displayH; val w = h * imageAspect
            floatArrayOf((displayW - w) / 2f, (displayH - h) / 2f, w, h)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // ── Overlay canvas ────────────────────────────────────────────────────
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val (imgX, imgY, imgW, imgH) = imageRect(size.width, size.height)
            val cL = imgX + boxLeft         * imgW
            val cT = imgY + boxTop          * imgH
            val cR = imgX + (boxLeft + boxW)  * imgW
            val cB = imgY + (boxTop + boxH()) * imgH

            // Dim outside crop box
            val dim = androidx.compose.ui.graphics.Color(0x99000000)
            drawRect(dim, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),   size = androidx.compose.ui.geometry.Size(size.width, cT))
            drawRect(dim, topLeft = androidx.compose.ui.geometry.Offset(0f, cB),   size = androidx.compose.ui.geometry.Size(size.width, size.height - cB))
            drawRect(dim, topLeft = androidx.compose.ui.geometry.Offset(0f, cT),   size = androidx.compose.ui.geometry.Size(cL, cB - cT))
            drawRect(dim, topLeft = androidx.compose.ui.geometry.Offset(cR, cT),   size = androidx.compose.ui.geometry.Size(size.width - cR, cB - cT))

            // Border
            val white = androidx.compose.ui.graphics.Color.White
            drawRect(white,
                topLeft = androidx.compose.ui.geometry.Offset(cL, cT),
                size = androidx.compose.ui.geometry.Size(cR - cL, cB - cT),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

            // Corner handles
            val arm = 40f; val sw = 5f
            drawLine(white, androidx.compose.ui.geometry.Offset(cL, cT + arm), androidx.compose.ui.geometry.Offset(cL, cT), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cL, cT), androidx.compose.ui.geometry.Offset(cL + arm, cT), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cR - arm, cT), androidx.compose.ui.geometry.Offset(cR, cT), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cR, cT), androidx.compose.ui.geometry.Offset(cR, cT + arm), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cL, cB - arm), androidx.compose.ui.geometry.Offset(cL, cB), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cL, cB), androidx.compose.ui.geometry.Offset(cL + arm, cB), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cR - arm, cB), androidx.compose.ui.geometry.Offset(cR, cB), sw)
            drawLine(white, androidx.compose.ui.geometry.Offset(cR, cB), androidx.compose.ui.geometry.Offset(cR, cB - arm), sw)

            // Rule-of-thirds grid
            val thirdW = (cR - cL) / 3f; val thirdH = (cB - cT) / 3f
            val grid = white.copy(alpha = 0.25f)
            drawLine(grid, androidx.compose.ui.geometry.Offset(cL + thirdW,     cT), androidx.compose.ui.geometry.Offset(cL + thirdW,     cB), 1f)
            drawLine(grid, androidx.compose.ui.geometry.Offset(cL + thirdW * 2, cT), androidx.compose.ui.geometry.Offset(cL + thirdW * 2, cB), 1f)
            drawLine(grid, androidx.compose.ui.geometry.Offset(cL, cT + thirdH),     androidx.compose.ui.geometry.Offset(cR, cT + thirdH),     1f)
            drawLine(grid, androidx.compose.ui.geometry.Offset(cL, cT + thirdH * 2), androidx.compose.ui.geometry.Offset(cR, cT + thirdH * 2), 1f)
        }

        // ── Unified gesture handler ───────────────────────────────────────────
        // Uses a single awaitEachGesture loop. On first pointer down we decide if this
        // is a corner/pan drag (1 finger) or a pinch (2 fingers). High-level detectors
        // like detectDragGestures + detectTransformGestures cannot safely coexist even
        // via concurrent launches — detectDragGestures consumes the first-down event so
        // detectTransformGestures never sees the pointer pair it needs to start a pinch.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val displayW = size.width.toFloat()
                    val displayH = size.height.toFloat()
                    val cornerHit = 52f

                    awaitEachGesture {
                        // ── Wait for first finger down ────────────────────────
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()

                        val startOffset = firstDown.position
                        val (imgX0, imgY0, imgW0, imgH0) = imageRect(displayW, displayH)
                        val cL0 = imgX0 + boxLeft         * imgW0
                        val cT0 = imgY0 + boxTop          * imgH0
                        val cR0 = imgX0 + (boxLeft + boxW)  * imgW0
                        val cB0 = imgY0 + (boxTop + boxH()) * imgH0

                        // Classify the touch immediately on first down.
                        val corner = when {
                            startOffset.x in (cL0-cornerHit)..(cL0+cornerHit) && startOffset.y in (cT0-cornerHit)..(cT0+cornerHit) -> "TL"
                            startOffset.x in (cR0-cornerHit)..(cR0+cornerHit) && startOffset.y in (cT0-cornerHit)..(cT0+cornerHit) -> "TR"
                            startOffset.x in (cL0-cornerHit)..(cL0+cornerHit) && startOffset.y in (cB0-cornerHit)..(cB0+cornerHit) -> "BL"
                            startOffset.x in (cR0-cornerHit)..(cR0+cornerHit) && startOffset.y in (cB0-cornerHit)..(cB0+cornerHit) -> "BR"
                            else -> null
                        }
                        val panHit = corner == null &&
                                startOffset.x in cL0..cR0 && startOffset.y in cT0..cB0

                        // Track previous centroid / span for incremental pinch math.
                        var prevCentroid = startOffset
                        var prevSpan     = 0f

                        // ── Event loop ────────────────────────────────────────
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }

                            if (pointers.size >= 2) {
                                // ── Pinch ─────────────────────────────────────
                                val p1 = pointers[0].position
                                val p2 = pointers[1].position
                                val centroid = androidx.compose.ui.geometry.Offset(
                                    (p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f
                                )
                                val span = kotlin.math.sqrt(
                                    ((p2.x - p1.x) * (p2.x - p1.x) +
                                     (p2.y - p1.y) * (p2.y - p1.y)).toDouble()
                                ).toFloat()

                                if (prevSpan > 0f) {
                                    val zoom = (span / prevSpan.coerceAtLeast(1f))
                                        .coerceIn(0.5f, 2f) // sanity-clamp per frame
                                    if (kotlin.math.abs(zoom - 1f) > 0.001f) {
                                        val (imgX, imgY, imgW, imgH) = imageRect(displayW, displayH)
                                        val cx = ((centroid.x - imgX) / imgW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        val cy = ((centroid.y - imgY) / imgH.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        resizeBox(boxW * zoom, pivotX = cx, pivotY = cy)
                                    }
                                }
                                prevSpan     = span
                                prevCentroid = centroid
                                event.changes.forEach { it.consume() }

                            } else if (pointers.size == 1) {
                                // ── Single-finger drag ─────────────────────────
                                val change = pointers[0]
                                val drag = change.position - change.previousPosition
                                val (_, _, imgW, imgH) = imageRect(displayW, displayH)
                                val dx = drag.x / imgW.coerceAtLeast(1f)
                                val dy = drag.y / imgH.coerceAtLeast(1f)

                                when (corner) {
                                    "BR" -> {
                                        val maxWbyRight  = (1f - boxLeft).coerceAtLeast(0.05f)
                                        val maxWbyBottom = ((1f - boxTop) * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        boxW = (boxW + dx).coerceIn(0.05f, minOf(maxWbyRight, maxWbyBottom))
                                    }
                                    "BL" -> {
                                        val rightEdge    = boxLeft + boxW
                                        val maxWbyLeft   = rightEdge.coerceAtLeast(0.05f)
                                        val maxWbyBottom = ((1f - boxTop) * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW - dx).coerceIn(0.05f, minOf(maxWbyLeft, maxWbyBottom))
                                        boxW    = newW
                                        boxLeft = (rightEdge - newW).coerceIn(0f, maxOf(0f, 1f - newW))
                                    }
                                    "TR" -> {
                                        val bottomEdge  = boxTop + boxH()
                                        val maxWbyRight = (1f - boxLeft).coerceAtLeast(0.05f)
                                        val maxWbyTop   = (bottomEdge * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW + dx).coerceIn(0.05f, minOf(maxWbyRight, maxWbyTop))
                                        val newH = (newW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
                                        boxW   = newW
                                        boxTop = (bottomEdge - newH).coerceIn(0f, maxOf(0f, 1f - newH))
                                    }
                                    "TL" -> {
                                        val rightEdge  = boxLeft + boxW
                                        val bottomEdge = boxTop  + boxH()
                                        val maxWbyLeft = rightEdge.coerceAtLeast(0.05f)
                                        val maxWbyTop  = (bottomEdge * screenAspect / imageAspect.coerceAtLeast(0.01f)).coerceAtLeast(0.05f)
                                        val newW = (boxW - dx).coerceIn(0.05f, minOf(maxWbyLeft, maxWbyTop))
                                        val newH = (newW * imageAspect / screenAspect.coerceAtLeast(0.01f)).coerceIn(0.01f, 1f)
                                        boxLeft = (rightEdge  - newW).coerceIn(0f, maxOf(0f, 1f - newW))
                                        boxTop  = (bottomEdge - newH).coerceIn(0f, maxOf(0f, 1f - newH))
                                        boxW    = newW
                                    }
                                    null -> if (panHit) {
                                        boxLeft = (boxLeft + dx).coerceIn(0f, maxOf(0f, 1f - boxW))
                                        boxTop  = (boxTop  + dy).coerceIn(0f, maxOf(0f, 1f - boxH()))
                                    }
                                }
                                change.consume()
                                prevSpan = 0f // reset span so next 2-finger event starts fresh
                            }
                        } while (event.changes.any { it.pressed })

                        // Gesture ended — reset state.
                        activeCorner  = null
                        isDraggingBox = false
                    }
                }
        )

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
            }
            Text(
                "Crop Background",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drag to move · pinch or corners to resize",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val src = android.net.Uri.parse(imageUri)

                                    // Decode full bitmap. For file:// URIs use a direct stream;
                                    // for content:// URIs use ContentResolver (may return null on
                                    // permission loss — we handle that explicitly below).
                                    val full: android.graphics.Bitmap? = if (src.scheme == "file") {
                                        val f = java.io.File(src.path!!)
                                        if (!f.exists()) {
                                            null
                                        } else {
                                            f.inputStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                                        }
                                    } else {
                                        context.contentResolver.openInputStream(src)?.use {
                                            android.graphics.BitmapFactory.decodeStream(it)
                                        }
                                    }

                                    if (full == null) {
                                        return@withContext null
                                    }

                                    val fw = full.width.toFloat()
                                    val fh = full.height.toFloat()

                                    // Fractions → pixel coordinates, clamped so we never
                                    // request a rect that exceeds the bitmap bounds.
                                    val bx = (boxLeft * fw).toInt().coerceIn(0, full.width - 1)
                                    val by = (boxTop  * fh).toInt().coerceIn(0, full.height - 1)
                                    val bw = (boxW    * fw).toInt().coerceIn(1, full.width  - bx)
                                    val bh = (boxH()  * fh).toInt().coerceIn(1, full.height - by)

                                    val cropped = android.graphics.Bitmap.createBitmap(full, bx, by, bw, bh)
                                    full.recycle()

                                    // Scale to exact screen pixels.
                                    val targetW = screenW.toInt().coerceAtLeast(1)
                                    val targetH = screenH.toInt().coerceAtLeast(1)
                                    val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                                    if (scaled !== cropped) cropped.recycle()

                                    // Per-theme folder. Use a timestamped filename so the URI
                                    // always changes when the user replaces an existing image.
                                    // This is critical for two reasons:
                                    //  1. bgUri state change → LivePreviewCard recomposes with new image.
                                    //  2. Different URI → Coil fetches from disk instead of serving the
                                    //     old cached bitmap for the stale "crop.jpg" key.
                                    val dir = java.io.File(context.filesDir, "bg_images/$themeId").also { it.mkdirs() }
                                    val dest = java.io.File(dir, "crop_${System.currentTimeMillis()}.jpg")
                                    dest.outputStream().use { out ->
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    scaled.recycle()
                                    // Clean up old crops AFTER the new one is safely written so
                                    // there is never a gap where no valid crop exists on disk.
                                    // Deletes legacy "crop.jpg" and all prior "crop_*.jpg" files
                                    // except the one we just created, preventing accumulation.
                                    dir.listFiles { f ->
                                        f.name != dest.name &&
                                        (f.name == "crop.jpg" ||
                                         (f.name.startsWith("crop_") && f.name.endsWith(".jpg")))
                                    }?.forEach { it.delete() }
                                    val resultUri = android.net.Uri.fromFile(dest).toString()
                                    resultUri
                                } catch (e: Exception) {
                                    "ERROR:${e.javaClass.simpleName}: ${e.message}"
                                }
                            }
                            isSaving = false
                            if (result != null && !result.startsWith("ERROR:")) {
                                onConfirm(result)
                            } else {
                                // Show the actual error so the user can report it.
                                val msg = if (result != null) result.removePrefix("ERROR:") else "decodeStream returned null"
                                android.widget.Toast.makeText(
                                    context, "Crop failed: $msg", android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                // Disabled until intrinsics are loaded (prevents wrong crop on same-aspect
                // images where the box hasn't settled yet) and while a save is in progress.
                enabled = !isSaving && intrinsicsLoaded
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else if (!intrinsicsLoaded) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Use this crop")
                }
            }
        }
    }
}

/**
 * Loads [imageUri] through Coil at a tiny 32×32 resolution (same as ScribeComposeTheme's
 * analysisBitmap) and returns the average linear luminance across all pixels.
 *
 * Using Coil — not BitmapFactory.decodeFile — is critical because [imageUri] may be a
 * content:// URI (produced by SAF after a crop). BitmapFactory.decodeFile only handles
 * file:// paths and returns null for content:// URIs, making luminance always -1f.
 * Coil resolves both URI types correctly on all API levels.
 *
 * Returns -1f on any error so callers can treat it as "not computed".
 * This function is suspend so it must be called from a coroutine (e.g. scope.launch).
 */
private suspend fun computeBgLuminance(context: android.content.Context, imageUri: String): Float {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(imageUri)
                .size(coil3.size.Size(32, 32))
                .allowHardware(false) // getPixel() requires software bitmap config
                .build()
            val bitmap = (coil3.ImageLoader(context).execute(request) as? coil3.request.SuccessResult)
                ?.image
                ?.let { (it as? coil3.BitmapImage)?.bitmap }
                ?: return@withContext -1f

            val w = bitmap.width
            val h = bitmap.height
            if (w == 0 || h == 0) return@withContext -1f

            var total = 0.0
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val pixel = bitmap.getPixel(x, y)
                    // Convert sRGB channels to linear luminance (WCAG relative luminance formula)
                    val r = android.graphics.Color.red(pixel) / 255.0
                    val g = android.graphics.Color.green(pixel) / 255.0
                    val b = android.graphics.Color.blue(pixel) / 255.0
                    total += 0.2126 * r + 0.7152 * g + 0.0722 * b
                }
            }
            bitmap.recycle()
            (total / (w * h)).toFloat().coerceIn(0f, 1f)
        } catch (_: Exception) {
            -1f
        }
    }
}
