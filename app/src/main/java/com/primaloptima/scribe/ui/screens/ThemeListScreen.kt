package com.primaloptima.scribe.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.AppJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeListScreen(
    vm: ThemeViewModel,
    onBack: () -> Unit,
    onEditTheme: (themeId: String) -> Unit
) {
    val context = LocalContext.current
    val themes by vm.themes.collectAsStateWithLifecycle()
    val activeTheme by vm.activeTheme.collectAsStateWithLifecycle()

    var themeToDelete by remember { mutableStateOf<AppTheme?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }

    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    val hazeState = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = themeToDelete != null
        LaunchedEffect(anyDialogOpen) {
            if (anyDialogOpen && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (!anyDialogOpen) {
                dialogCaptured = false
                dialogOneShotBitmap = null
            }
        }
    }

    val importThemeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            importThemeFromUri(context, it, vm)
        }
    }

    val sortedThemes = remember(themes) {
        themes.sortedWith(
            compareByDescending<AppTheme> { it.builtIn }
                .thenBy { it.name.lowercase() }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            Box {
                ScribeTopBar(
                    title             = "Themes",
                    navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavigationClick = onBack,
                    actions           = listOf(
                        ScribeBarAction(Icons.Default.MoreVert, "Menu") { showTopMenu = true }
                    )
                )
                DropdownMenu(
                    expanded         = showTopMenu,
                    onDismissRequest = { showTopMenu = false },
                    containerColor   = LocalSolidSurface.current
                ) {
                    DropdownMenuItem(
                        text        = { Text("Import Theme") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick     = { showTopMenu = false; importThemeLauncher.launch("*/*") }
                    )
                }
            }
        },
        floatingActionButton = {
            ScribeSingleFab(
                icon = Icons.Default.Add,
                contentDescription = "New Theme",
                onClick = {
                    val active = activeTheme ?: DefaultThemes.all.first()
                    val newTheme = active.copy(
                        id = vm.generateId(),
                        name = "Custom Theme",
                        builtIn = false,
                        emoji = "🖊️"
                    )
                    vm.save(newTheme)
                    onEditTheme(newTheme.id)
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
        ) {
            itemsIndexed(
                items = sortedThemes,
                key = { index, theme -> "theme_${index}_${theme.id}_${theme.name}" }
            ) { _, theme ->
                val isSelected = theme.id == activeTheme?.id
                ThemeCard(
                    theme = theme,
                    isSelected = isSelected,
                    onSelect = {
                        vm.setActive(theme.id)
                        Toast.makeText(context, "${theme.name} applied", Toast.LENGTH_SHORT).show()
                    },
                    onEdit = { onEditTheme(theme.id) },
                    onDuplicate = {
                        vm.duplicate(theme.id)
                        Toast.makeText(context, "Duplicated ${theme.name}", Toast.LENGTH_SHORT).show()
                    },
                    onExport = {
                        exportThemeJson(context, theme)
                    },
                    onDelete = { themeToDelete = theme }
                )
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
        themeToDelete?.let { theme ->
            FrostedDialog(
                onDismissRequest = { themeToDelete = null },
                title = { Text("Delete \"${theme.name}\"?") },
                text = { Text("Are you sure you want to delete this theme?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.delete(theme.id)
                            themeToDelete = null
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { themeToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val bgColor = parseComposeColor(theme.colors.background, Color.LightGray)
    val textColor = parseComposeColor(theme.colors.text, Color.Black)
    val mutedColor = parseComposeColor(theme.colors.mutedText, textColor.copy(alpha = 0.7f))
    val accentColor = parseComposeColor(theme.colors.accent, Color.Blue)
    val cardShape = RoundedCornerShape(12.dp)

    val displayName = if (!theme.emoji.isNullOrEmpty()) "${theme.emoji} ${theme.name}" else theme.name

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = cardShape,
                ambientColor = if (isSelected) accentColor else Color.Black,
                spotColor = if (isSelected) accentColor else Color.Black
            )
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    accentColor,
                    cardShape
                ) else Modifier.border(
                    1.dp,
                    parseComposeColor(theme.colors.border, Color.LightGray),
                    cardShape
                )
            )
            .clickable { onSelect() },
        shape = cardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmark tab strip on left edge
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        accentColor,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Aa" circle swatch
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aa",
                        color = bgColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontHelper.getFontFamily(theme.fontFamily)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (theme.builtIn) "Built-in" else "Custom",
                        fontSize = 12.sp,
                        color = mutedColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The quick brown fox jumps over the lazy dog",
                        color = textColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontHelper.getFontFamily(theme.fontFamily)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active",
                            tint = bgColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = textColor
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = LocalSolidSurface.current
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set Active") },
                            onClick = {
                                showMenu = false
                                onSelect()
                            }
                        )
                        if (!theme.builtIn) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        if (!theme.builtIn) {
                            DropdownMenuItem(
                                text = { Text("Export") },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
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

private fun importThemeFromUri(context: Context, uri: Uri, vm: ThemeViewModel) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val json = inputStream?.bufferedReader()?.use { it.readText() }
        if (!json.isNullOrEmpty()) {
            val imported = try { AppJson.decodeFromString<AppTheme>(json) } catch (_: Exception) { null }
            if (imported != null && !imported.name.isNullOrBlank()) {
                val newTheme = imported.copy(
                    id = vm.generateId(),
                    builtIn = false,
                    name = if (imported.name.contains("Imported")) imported.name else "${imported.name} (Imported)"
                )
                vm.save(newTheme)
                Toast.makeText(context, "Theme imported: ${newTheme.name}", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
