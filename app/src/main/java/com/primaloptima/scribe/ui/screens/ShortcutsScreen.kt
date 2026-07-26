package com.primaloptima.scribe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    vm: ShortcutsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val shortcuts by vm.shortcuts.observeAsState(emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    var shortcutToEdit by remember { mutableStateOf<ShortcutAction?>(null) }
    var shortcutToDelete by remember { mutableStateOf<ShortcutAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shortcuts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.resetToDefaults()
                        Toast.makeText(context, "Shortcuts reset to defaults", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    shortcutToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.frostedFab(LocalHazeState.current)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Shortcut")
            }
        }
    ) { padding ->
        if (shortcuts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No shortcuts configured. Tap + to create one.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    ShortcutRow(
                        shortcut = shortcut,
                        onEdit = {
                            shortcutToEdit = shortcut
                            showEditDialog = true
                        },
                        onDelete = { shortcutToDelete = shortcut }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditShortcutDialog(
            existing = shortcutToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { shortcut ->
                if (shortcutToEdit == null) {
                    vm.add(shortcut)
                } else {
                    vm.update(shortcut)
                }
                showEditDialog = false
            }
        )
    }

    shortcutToDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { shortcutToDelete = null },
            title = { Text("Delete \"${shortcut.label}\"?") },
            text = { Text("Are you sure you want to delete this shortcut?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(shortcut.id)
                        shortcutToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: ShortcutAction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ShortText,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(shortcut.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Kind: ${shortcut.kind} • Payload: ${shortcut.payload}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = LocalSolidSurface.current
                ) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun EditShortcutDialog(
    existing: ShortcutAction?,
    onDismiss: () -> Unit,
    onSave: (ShortcutAction) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "insert") }
    var payload by remember { mutableStateOf(existing?.payload ?: "") }
    var closing by remember { mutableStateOf(existing?.closing ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Shortcut" else "Edit Shortcut") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Text / Prefix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (kind == "wrap" || kind == "pair") {
                    OutlinedTextField(
                        value = closing,
                        onValueChange = { closing = it },
                        label = { Text("Closing Suffix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == "insert", onClick = { kind = "insert" })
                    Text("Insert")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = kind == "wrap", onClick = { kind = "wrap" })
                    Text("Wrap")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = kind == "pair", onClick = { kind = "pair" })
                    Text("Pair")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank() && payload.isNotBlank()) {
                        onSave(
                            ShortcutAction(
                                id = existing?.id ?: System.currentTimeMillis().toString(),
                                label = label.trim(),
                                kind = kind,
                                payload = payload,
                                closing = closing.ifBlank { null }
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
