package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorLeftDrawer(
    leftDrawerMode   : String,
    onModeChange     : (String) -> Unit,
    currentBookNotes : List<Note>,
    allNotes         : List<Note>,
    activeNoteId     : String?,
    onNoteClick      : (String) -> Unit,
    onAddNote        : () -> Unit,
    hazeState        : dev.chrisbanes.haze.HazeState,
    barBlurBitmap    : Bitmap?,
) {
    var leftPanelTab by remember { mutableIntStateOf(0) }
    val expandedTreeState = remember { mutableStateMapOf<String, Boolean>() }

    val displayNotes = if (leftDrawerMode == "Current") currentBookNotes else allNotes
    val folderGrouped = remember(displayNotes) {
        buildMap<String, MutableList<Note>> {
            displayNotes.forEach { n ->
                getOrPut(n.folderPath.ifBlank { "/" }) { mutableListOf() }.add(n)
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
        ModalDrawerSheet(
            drawerContainerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .frostedPanel(hazeState)
        ) {
            Spacer(Modifier.height(12.dp))

            PrimaryTabRow(selectedTabIndex = leftPanelTab) {
                Tab(
                    selected = leftPanelTab == 0,
                    onClick  = { leftPanelTab = 0 },
                    text     = { Text("Files", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = leftPanelTab == 1,
                    onClick  = { leftPanelTab = 1 },
                    text     = { Text("World", fontWeight = FontWeight.Bold) }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = leftDrawerMode == "Current",
                    onClick  = { onModeChange("Current") },
                    label    = { Text("Current Book", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = leftDrawerMode == "Books",
                    onClick  = { onModeChange("Books") },
                    label    = { Text("All Books", fontSize = 12.sp) }
                )
            }

            HorizontalDivider(Modifier.padding(bottom = 4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Notes",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onAddNote, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "New Note", modifier = Modifier.size(18.dp))
                }
            }

            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                folderGrouped.forEach { (folderPath, notesInFolder) ->
                    val isExpanded = expandedTreeState[folderPath] ?: true

                    item(key = "fd_$folderPath") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expandedTreeState[folderPath] = !isExpanded }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                folderPath.substringAfterLast('/').ifEmpty { "Root" },
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 13.sp,
                                modifier   = Modifier.weight(1f)
                            )
                            Text(
                                "${notesInFolder.size}",
                                fontSize = 11.sp,
                                color    = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (isExpanded) {
                        items(notesInFolder, key = { "nd_${it.id}" }) { note ->
                            val isActive = note.id == activeNoteId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .clickable { onNoteClick(note.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint     = if (isActive) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    note.name,
                                    fontSize   = 14.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color      = if (isActive) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurface,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.weight(1f)
                                )
                                Text(
                                    "${note.wordCount}w",
                                    fontSize = 11.sp,
                                    color    = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
