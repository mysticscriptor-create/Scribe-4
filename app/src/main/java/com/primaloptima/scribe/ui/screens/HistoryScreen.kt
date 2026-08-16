package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.NoteVersion
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.MarkdownUtil
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    editorVm: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as ScribeApp

    // Phase 6-F: read activeNoteId from DataStore instead of prefs snapshot.
    // collectAsStateWithLifecycle stops collecting when the UI is invisible,
    // saving resources (replaces collectAsState throughout this screen).
    val activeNoteIdState by app.dataStore.activeNoteIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val noteId = activeNoteIdState ?: ""
    var currentNoteContent by remember { mutableStateOf("") }

    val versionsFlow = remember(noteId) {
        app.database.noteVersionDao().observeVersions(noteId)
    }
    val versions by versionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Tracks whether the DB has responded at least once so we don't flash
    // "No saved versions" on the initial empty-list emission before the
    // query returns. Only show the empty state after the first real result.
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(versions) {
        if (!hasLoaded) hasLoaded = true
    }

    LaunchedEffect(noteId) {
        if (noteId.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val n = app.database.noteDao().getById(noteId)
                if (n != null) {
                    currentNoteContent = n.content
                }
            }
        }
    }

    var selectedVersion by remember { mutableStateOf<NoteVersion?>(null) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    val hazeState = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = selectedVersion != null || showConfirmRestoreDialog
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

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = "Version History",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        if (versions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                // Show the empty-state message only after the DB has responded.
                // Before hasLoaded, the list is the initial emptyList() placeholder —
                // showing the message immediately would flash before any data arrives.
                if (hasLoaded) {
                    Text("No saved versions for this note yet.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            ) {
                itemsIndexed(versions, key = { _, ver -> ver.timestamp }) { index, ver ->
                    val dateStr = remember(ver.timestamp) {
                        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
                    }
                    // Word delta vs. the next older version (index+1 is older because list is DESC)
                    val deltaStr = remember(versions, index) {
                        val olderWords = versions.getOrNull(index + 1)?.wordCount ?: 0
                        val delta = ver.wordCount - olderWords
                        when {
                            index == versions.lastIndex -> null // oldest — no prior to compare
                            delta > 0  -> "+$delta words"
                            delta < 0  -> "$delta words"
                            else       -> "no change"
                        }
                    }
                    val isManual = ver.type == com.primaloptima.scribe.data.NoteVersion.TYPE_MANUAL

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVersion = ver },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isManual) Icons.Default.Bookmark else Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (isManual) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Checkpoint",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${ver.wordCount} words", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    if (deltaStr != null) {
                                        val deltaColor = when {
                                            deltaStr.startsWith("+") -> Color(0xFF2E7D32)
                                            deltaStr.startsWith("-") -> Color(0xFFC62828)
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                        Text(deltaStr, fontSize = 12.sp, color = deltaColor, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    ver.content.take(100).replace("\n", " "),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
        selectedVersion?.let { ver ->
            val dateStr = remember(ver.timestamp) {
                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
            }

            FrostedDialog(
                onDismissRequest = { selectedVersion = null },
                title = {
                    Column {
                        Text("Version Preview", fontWeight = FontWeight.Bold)
                        Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        val diffAnnotated = remember(currentNoteContent, ver.content) {
                            buildDiffAnnotatedString(currentNoteContent, ver.content)
                        }
                        Text(text = diffAnnotated, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showConfirmRestoreDialog = true }
                    ) {
                        Text("Restore this version")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedVersion = null }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showConfirmRestoreDialog && selectedVersion != null) {
            FrostedDialog(
                onDismissRequest = { showConfirmRestoreDialog = false },
                title = { Text("Confirm Restore") },
                text = { Text("Are you sure you want to replace current note content with this saved version?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val ver = selectedVersion!!
                            // Fix (Bug 1): Route restore through EditorViewModel so
                            // lastWordCount is reset to the restored baseline and a
                            // corrective delta is written to writing_log atomically.
                            // Previously this called db.noteDao() directly, bypassing
                            // the ViewModel entirely and corrupting subsequent deltas.
                            editorVm.restoreSnapshot(ver.content)
                            scope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Version restored successfully", Toast.LENGTH_SHORT).show()
                                showConfirmRestoreDialog = false
                                selectedVersion = null
                                onBack()
                            }
                        }
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestoreDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun buildDiffAnnotatedString(currentText: String, versionText: String) = buildAnnotatedString {
    val currentLines = currentText.lines()
    val versionLines = versionText.lines()

    val oldSet = currentLines.toSet()
    val newSet = versionLines.toSet()

    for (line in versionLines) {
        if (!oldSet.contains(line)) {
            // Added in this version
            withStyle(
                style = SpanStyle(
                    background = Color(0x334CAF50),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("+ $line\n")
            }
        } else {
            append("  $line\n")
        }
    }

    for (line in currentLines) {
        if (!newSet.contains(line)) {
            // Removed in this version
            withStyle(
                style = SpanStyle(
                    background = Color(0x33F44336),
                    textDecoration = TextDecoration.LineThrough,
                    color = Color.Red
                )
            ) {
                append("- $line\n")
            }
        }
    }
}
