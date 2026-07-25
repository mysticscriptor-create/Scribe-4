package com.primaloptima.scribe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.NoteVersion
import com.primaloptima.scribe.util.MarkdownUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as ScribeApp

    val noteId = remember { app.prefs.activeNoteId ?: "" }
    var currentNoteContent by remember { mutableStateOf("") }

    val versionsFlow = remember(noteId) {
        app.database.noteVersionDao().observeVersions(noteId)
    }
    val versions by versionsFlow.collectAsState(initial = emptyList())

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Version History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                Text("No saved versions for this note yet.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(versions) { ver ->
                    val dateStr = remember(ver.timestamp) {
                        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
                    }

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
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${ver.wordCount} words", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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

    selectedVersion?.let { ver ->
        val dateStr = remember(ver.timestamp) {
            SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
        }

        AlertDialog(
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
        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            title = { Text("Confirm Restore") },
            text = { Text("Are you sure you want to replace current note content with this saved version?") },
            confirmButton = {
                Button(
                    onClick = {
                        val ver = selectedVersion!!
                        scope.launch(Dispatchers.IO) {
                            val db = app.database
                            db.noteDao().updateContent(noteId, ver.content, System.currentTimeMillis())
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Version restored successfully", Toast.LENGTH_SHORT).show()
                                showConfirmRestoreDialog = false
                                selectedVersion = null
                                onBack()
                            }
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
