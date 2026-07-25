package com.primaloptima.scribe.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import coil3.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import com.google.gson.GsonBuilder
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditScreen(
    themeId: String,
    vm: ThemeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themes by vm.themes.observeAsState(emptyList())

    val originalTheme = remember(themes, themeId) {
        themes.firstOrNull { it.id == themeId } ?: DefaultThemes.all.first()
    }

    var name by remember(originalTheme) { mutableStateOf(originalTheme.name) }
    var bgHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.background) }
    var textHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.text) }
    var accentHex by remember(originalTheme) { mutableStateOf(originalTheme.colors.accent) }

    var bgMode by remember(originalTheme) { mutableStateOf(originalTheme.bgMode) }
    var bgUri by remember(originalTheme) { mutableStateOf(originalTheme.backgroundImageUri) }
    var bgOpacity by remember(originalTheme) { mutableFloatStateOf(originalTheme.backgroundImageOpacity ?: 0.35f) }
    var blurIntensity by remember(originalTheme) { mutableFloatStateOf(originalTheme.blurIntensity) }

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

    val bgImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            bgUri = it.toString()
            if (bgMode == "color") bgMode = "image"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (originalTheme.builtIn) "View Theme" else "Edit Theme",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val updated = originalTheme.copy(
                            name = name,
                            fontSize = fontSize.toInt(),
                            lineHeight = lineHeight,
                            paragraphSpacing = paragraphSpacing.toInt(),
                            paddingHorizontal = sideMargins.toInt(),
                            fontFamily = fontFamily,
                            backgroundImageUri = bgUri,
                            backgroundImageOpacity = bgOpacity,
                            bgMode = bgMode,
                            blurIntensity = blurIntensity,
                            textAlignment = textAlignment,
                            themeScope = themeScope,
                            emoji = emoji,
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
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        Text("Background Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val modes = listOf("color" to "Color", "image" to "Image", "blurred" to "Blurred")
                            modes.forEachIndexed { index, (modeKey, label) ->
                                SegmentedButton(
                                    selected = bgMode == modeKey,
                                    onClick = {
                                        bgMode = modeKey
                                        if ((modeKey == "image" || modeKey == "blurred") && bgUri.isNullOrEmpty()) {
                                            bgImagePicker.launch("image/*")
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                                ) {
                                    Text(label)
                                }
                            }
                        }

                        if (bgMode == "image" || bgMode == "blurred") {
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
                                    TextButton(onClick = { bgUri = null }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

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

                            Text(
                                "Overlay Opacity: ${(bgOpacity * 100).toInt()}%",
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = bgOpacity,
                                onValueChange = { bgOpacity = it },
                                valueRange = 0.0f..0.90f
                            )
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
                                "left" to Icons.Default.FormatAlignLeft,
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
                        backgroundImageOpacity = bgOpacity,
                        bgMode = bgMode,
                        blurIntensity = blurIntensity,
                        textAlignment = textAlignment,
                        themeScope = themeScope,
                        emoji = emoji,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                backgroundImageOpacity = bgOpacity,
                                bgMode = bgMode,
                                blurIntensity = blurIntensity,
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
        AlertDialog(
            onDismissRequest = { showEmojiDialog = false },
            title = { Text("Choose Emoji") },
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = bgOpacity))
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
                    Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerBottomSheet(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedColor by remember { mutableStateOf(parseComposeColor(initialHex, Color.Red)) }
    var hexText by remember { mutableStateOf(initialHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)

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
    }
}

private fun exportThemeJson(context: Context, theme: AppTheme) {
    try {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(theme)
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
