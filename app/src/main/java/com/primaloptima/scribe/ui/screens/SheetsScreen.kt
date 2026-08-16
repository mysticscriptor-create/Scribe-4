package com.primaloptima.scribe.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.util.AppJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.viewmodel.SheetsViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Category metadata ─────────────────────────────────────────────────────────

private data class CategoryMeta(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val CATEGORY_META = listOf(
    CategoryMeta("All",      "All",       Icons.Default.GridView,                    Color(0xFF9E9E9E)),
    CategoryMeta("character","Characters", Icons.Default.Person,                      Color(0xFF5C9EF0)),
    CategoryMeta("location", "Locations",  Icons.Default.Place,                       Color(0xFF4CAF82)),
    CategoryMeta("faction",  "Factions",   Icons.Default.Group,                       Color(0xFFE07B54)),
    CategoryMeta("item",     "Items",      Icons.Default.Category,                    Color(0xFFB07FD4)),
    CategoryMeta("lore",     "Lore",       Icons.AutoMirrored.Filled.MenuBook,        Color(0xFFD4A74A)),
    CategoryMeta("timeline", "Timeline",   Icons.Default.Timeline,                    Color(0xFF4AB8D4)),
)

private fun categoryMeta(key: String): CategoryMeta =
    CATEGORY_META.find { it.key.equals(key, ignoreCase = true) } ?: CATEGORY_META[0]

// ── Time-ago helper ───────────────────────────────────────────────────────────

private fun timeAgo(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours   = TimeUnit.MILLISECONDS.toHours(diff)
    val days    = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1  -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours   < 24 -> "$hours h ago"
        days    < 7  -> "$days day${if (days > 1) "s" else ""} ago"
        else         -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetsScreen(
    vm: SheetsViewModel,
    onBack: () -> Unit,
    openCreateOnLaunch: Boolean = false
) {
    val context    = LocalContext.current
    val allEntries by vm.allEntries.collectAsStateWithLifecycle()

    val categoryKeys = listOf("All", "character", "location", "faction", "item", "lore", "timeline")
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery      by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (openCreateOnLaunch) showCreateDialog = true }

    var entryToDetail by remember { mutableStateOf<WorldEntry?>(null) }
    var entryToEdit   by remember { mutableStateOf<WorldEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<WorldEntry?>(null) }

    // Pre-API-31 one-shot blur for dialogs
    val view         = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    val hazeState    = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured      by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = showCreateDialog || entryToDetail != null ||
                entryToEdit != null || entryToDelete != null
        LaunchedEffect(anyDialogOpen) {
            if (anyDialogOpen && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (!anyDialogOpen) {
                dialogCaptured      = false
                dialogOneShotBitmap = null
            }
        }
    }

    // Count per category for chip badges
    val countByType = remember(allEntries) {
        allEntries.groupingBy { it.type.lowercase() }.eachCount()
    }

    val filteredEntries = remember(allEntries, selectedCategory, searchQuery) {
        allEntries.filter { entry ->
            val matchesCategory = selectedCategory == "All" ||
                    entry.type.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    entry.name.contains(searchQuery, ignoreCase = true) ||
                    entry.summary.contains(searchQuery, ignoreCase = true) ||
                    entry.fieldsJson.contains(searchQuery, ignoreCase = true) ||
                    entry.tagsJson.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            ScribeTopBar(
                title             = "World Building Sheets",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        },
        floatingActionButton = {
            ScribeSingleFab(
                icon               = Icons.Default.Add,
                contentDescription = "Add Entry",
                onClick            = { showCreateDialog = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search names, details, tags…") },
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon  = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Category filter chips with counts ─────────────────────────────
            LazyRow(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryKeys, key = { it }) { key ->
                    val meta  = categoryMeta(key)
                    val count = if (key == "All") allEntries.size
                                else countByType[key] ?: 0
                    val selected = selectedCategory == key
                    FilterChip(
                        selected = selected,
                        onClick  = { selectedCategory = key },
                        label    = {
                            Text(
                                if (count > 0) "${meta.label} ($count)"
                                else meta.label
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector        = meta.icon,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                       else meta.color
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── List or empty state ───────────────────────────────────────────
            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = if (searchQuery.isNotBlank()) Icons.Default.SearchOff
                                                 else categoryMeta(selectedCategory).icon,
                            contentDescription = null,
                            modifier           = Modifier.size(48.dp),
                            tint               = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text  = if (searchQuery.isNotBlank())
                                        "No results for \"$searchQuery\""
                                    else
                                        "No ${categoryMeta(selectedCategory).label.lowercase()} yet.\nTap + to create one.",
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier
                        .fillMaxSize()
                        .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        WorldEntryCard(
                            entry     = entry,
                            onClick   = { entryToDetail = entry },
                            onEdit    = { entryToEdit   = entry },
                            onDuplicate = { vm.duplicateEntry(entry.id) },
                            onDelete  = { entryToDelete = entry }
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {

        if (showCreateDialog) {
            CreateEntryDialog(
                selectedCategory = selectedCategory,
                onDismiss        = { showCreateDialog = false },
                onConfirm        = { name, type ->
                    vm.createEntry(type, name) { created ->
                        showCreateDialog = false
                        entryToEdit      = created
                    }
                }
            )
        }

        entryToDetail?.let { entry ->
            WorldEntryDetailDialog(
                entry     = entry,
                onDismiss = { entryToDetail = null },
                onEdit    = {
                    entryToEdit   = entry
                    entryToDetail = null
                }
            )
        }

        entryToEdit?.let { entry ->
            EditWorldEntryDialog(
                entry     = entry,
                onDismiss = { entryToEdit = null },
                onSave    = { updated ->
                    vm.updateEntry(updated)
                    entryToEdit = null
                }
            )
        }

        entryToDelete?.let { entry ->
            FrostedDialog(
                onDismissRequest = { entryToDelete = null },
                title            = { Text("Delete Entry?") },
                text             = { Text("This will permanently delete \"${entry.name}\".") },
                confirmButton    = {
                    TextButton(onClick = {
                        vm.deleteEntry(entry.id)
                        entryToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton    = {
                    TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// ── World entry card ──────────────────────────────────────────────────────────

@Composable
private fun WorldEntryCard(
    entry:       WorldEntry,
    onClick:     () -> Unit,
    onEdit:      () -> Unit,
    onDuplicate: () -> Unit,
    onDelete:    () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val meta = categoryMeta(entry.type)

    ScribeCard(onClick = onClick) {
        Row(
            modifier          = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (!entry.imageUri.isNullOrEmpty()) {
                AsyncImage(
                    model              = entry.imageUri,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, meta.color.copy(alpha = 0.6f), CircleShape)
                )
            } else {
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(meta.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = meta.icon,
                        contentDescription = null,
                        tint               = meta.color,
                        modifier           = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Name + type dot on same row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = entry.name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(meta.color)
                    )
                }

                // Type label
                Text(
                    text      = meta.label.dropLast(1).ifEmpty { meta.label }, // "Characters" → "Character"
                    fontSize  = 11.sp,
                    color     = meta.color,
                    fontWeight = FontWeight.SemiBold
                )

                // Summary
                if (entry.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text     = entry.summary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Updated timestamp
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = "Updated ${timeAgo(entry.updatedAt)}",
                    fontSize = 10.sp,
                    color    = MaterialTheme.colorScheme.outline
                )
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded          = showMenu,
                    onDismissRequest  = { showMenu = false },
                    containerColor    = LocalSolidSurface.current
                ) {
                    DropdownMenuItem(
                        text    = { Text("View") },
                        onClick = { showMenu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Edit") },
                        onClick = { showMenu = false; onEdit() }   // goes straight to edit
                    )
                    DropdownMenuItem(
                        text    = { Text("Duplicate") },
                        onClick = { showMenu = false; onDuplicate() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ── Create entry dialog ───────────────────────────────────────────────────────

@Composable
private fun CreateEntryDialog(
    selectedCategory: String,
    onDismiss:        () -> Unit,
    onConfirm:        (name: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember {
        mutableStateOf(if (selectedCategory == "All") "character" else selectedCategory)
    }
    val typeKeys = listOf("character", "location", "faction", "item", "lore", "timeline")

    FrostedDialog(
        onDismissRequest = onDismiss,
        title            = { Text("New World Sheet") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Name / Title") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                Text("Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(typeKeys, key = { it }) { key ->
                        val meta     = categoryMeta(key)
                        val selected = type == key
                        FilterChip(
                            selected    = selected,
                            onClick     = { type = key },
                            label       = { Text(meta.label.dropLast(1).ifEmpty { meta.label }) },
                            leadingIcon = {
                                Icon(
                                    imageVector        = meta.icon,
                                    contentDescription = null,
                                    modifier           = Modifier.size(14.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                           else meta.color
                                )
                            }
                        )
                    }
                }

                // Preview what fields will be created
                val previewFields = when (type) {
                    "character" -> SheetsViewModel.CHARACTER_FIELDS
                    "location"  -> SheetsViewModel.LOCATION_FIELDS
                    "faction"   -> SheetsViewModel.FACTION_FIELDS
                    "item"      -> SheetsViewModel.ITEM_FIELDS
                    "lore"      -> SheetsViewModel.LORE_FIELDS
                    "timeline"  -> SheetsViewModel.TIMELINE_FIELDS
                    else        -> SheetsViewModel.GENERAL_FIELDS
                }
                Text(
                    text  = "Will create: ${previewFields.joinToString(" · ") { it.label }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, type) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Edit entry dialog ─────────────────────────────────────────────────────────

@Composable
private fun EditWorldEntryDialog(
    entry:    WorldEntry,
    onDismiss: () -> Unit,
    onSave:   (WorldEntry) -> Unit
) {
    val initialFields: List<SheetsViewModel.Companion.Field> = remember(entry) {
        try { AppJson.decodeFromString(entry.fieldsJson) } catch (_: Exception) { emptyList() }
    }
    val initialTags: List<String> = remember(entry) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    var name     by remember { mutableStateOf(entry.name) }
    var summary  by remember { mutableStateOf(entry.summary) }
    var imageUri by remember { mutableStateOf(entry.imageUri) }
    var fields   by remember { mutableStateOf(initialFields) }
    var tags     by remember { mutableStateOf(initialTags) }
    var newTag   by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { imageUri = it.toString() } }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Edit ${entry.name}") },
        text             = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image picker
                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(12.dp)
                ) {
                    if (!imageUri.isNullOrEmpty()) {
                        AsyncImage(
                            model              = imageUri,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.size(56.dp).clip(CircleShape)
                        )
                    }
                    Column {
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (imageUri.isNullOrEmpty()) "Pick Photo" else "Change Photo")
                        }
                        if (!imageUri.isNullOrEmpty()) {
                            TextButton(onClick = { imageUri = null }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Name
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                // Summary
                OutlinedTextField(
                    value         = summary,
                    onValueChange = { summary = it },
                    label         = { Text("Summary / Overview") },
                    maxLines      = 4,
                    modifier      = Modifier.fillMaxWidth()
                )

                // Tags
                HorizontalDivider()
                Text("Tags", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                // Existing tags as removable chips
                if (tags.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick  = {},
                                label    = { Text(tag, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier           = Modifier
                                            .size(14.dp)
                                            .clickable { tags = tags - tag }
                                    )
                                }
                            )
                        }
                    }
                }

                // Add tag input
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value         = newTag,
                        onValueChange = { newTag = it },
                        placeholder   = { Text("Add tag…") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick  = {
                            val trimmed = newTag.trim()
                            if (trimmed.isNotEmpty() && !tags.contains(trimmed)) {
                                tags   = tags + trimmed
                                newTag = ""
                            }
                        },
                        enabled = newTag.trim().isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }

                // Fields / Attributes
                HorizontalDivider()
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Attributes & Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = {
                        fields = fields + SheetsViewModel.Companion.Field("New Attribute")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Attribute")
                    }
                }

                fields.forEachIndexed { index, field ->
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value         = field.label,
                                onValueChange = { newLabel ->
                                    val list = fields.toMutableList()
                                    list[index] = field.copy(label = newLabel)
                                    fields = list
                                },
                                label     = { Text("Label") },
                                singleLine = true,
                                modifier  = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value         = field.value,
                                onValueChange = { newVal ->
                                    val list = fields.toMutableList()
                                    list[index] = field.copy(value = newVal)
                                    fields = list
                                },
                                label    = { Text("Value") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(onClick = {
                            val list = fields.toMutableList()
                            list.removeAt(index)
                            fields = list
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint               = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(entry.copy(
                    name      = name,
                    summary   = summary,
                    imageUri  = imageUri,
                    fieldsJson = AppJson.encodeToString(fields),
                    tagsJson   = AppJson.encodeToString(tags)
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

@Composable
private fun WorldEntryDetailDialog(
    entry:    WorldEntry,
    onDismiss: () -> Unit,
    onEdit:   () -> Unit
) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val meta      = categoryMeta(entry.type)

    val fields: List<SheetsViewModel.Companion.Field> = remember(entry) {
        try { AppJson.decodeFromString(entry.fieldsJson) } catch (_: Exception) { emptyList() }
    }
    val tags: List<String> = remember(entry) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title            = null,
        text             = {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                if (!entry.imageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model              = entry.imageUri,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(2.dp, meta.color, CircleShape)
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(meta.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = meta.icon,
                            contentDescription = null,
                            tint               = meta.color,
                            modifier           = Modifier.size(50.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text       = entry.name,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Type badge with category color
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = meta.color.copy(alpha = 0.18f)
                ) {
                    Text(
                        text       = meta.label.dropLast(1).ifEmpty { meta.label },
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = meta.color,
                        modifier   = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }

                // Tags
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                onClick = {},
                                label   = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Updated time
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text     = "Updated ${timeAgo(entry.updatedAt)}",
                    fontSize = 10.sp,
                    color    = MaterialTheme.colorScheme.outline
                )

                // Summary
                if (entry.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = LocalSolidSurface.current.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Overview", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                 color = meta.color)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(entry.summary, fontSize = 14.sp)
                        }
                    }
                }

                // Fields — each value tappable to copy
                if (fields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = LocalSolidSurface.current.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            fields.forEachIndexed { idx, field ->
                                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text       = field.label,
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = meta.color
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Tappable value → copies to clipboard
                                    Row(
                                        modifier          = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = field.value.isNotBlank()) {
                                                clipboard.setText(AnnotatedString(field.value))
                                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text     = field.value.ifBlank { "—" },
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            color    = if (field.value.isBlank())
                                                           MaterialTheme.colorScheme.outline
                                                       else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (field.value.isNotBlank()) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                modifier           = Modifier.size(14.dp),
                                                tint               = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
