package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Maximize
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.FloatingWindow
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.roundToInt

@Composable
fun FloatingWindowOverlay(
    floatingWindows: List<FloatingWindow>,
    notes: List<Note>,
    activeTheme: AppTheme?,
    onCloseWindow: (String) -> Unit,
    onToggleCollapse: (String) -> Unit,
    onMoveWindow: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        floatingWindows.forEach { windowState ->
            val note = notes.firstOrNull { it.id == windowState.noteId }
            if (note != null) {
                FloatingWindowItem(
                    windowState = windowState,
                    note = note,
                    activeTheme = activeTheme,
                    onClose = { onCloseWindow(windowState.id) },
                    onToggleCollapse = { onToggleCollapse(windowState.id) },
                    onMove = { dx, dy ->
                        onMoveWindow(windowState.id, windowState.x + dx, windowState.y + dy)
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingWindowItem(
    windowState: FloatingWindow,
    note: Note,
    activeTheme: AppTheme?,
    onClose: () -> Unit,
    onToggleCollapse: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val windowWidthDp = 280.dp
    val windowWidthPx = with(density) { windowWidthDp.toPx() }

    val offsetX = windowState.x.coerceIn(0f, (screenWidthPx - windowWidthPx).coerceAtLeast(0f))
    val offsetY = windowState.y.coerceIn(0f, (screenHeightPx - 200f).coerceAtLeast(0f))

    Surface(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(windowWidthDp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Header Bar (Draggable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (windowState.collapsed) Icons.Default.Maximize else Icons.Default.Minimize,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!windowState.collapsed) {
                // Read-only Sora viewer — matches the main editor's rendering
                // without syntax highlighting or line numbers.
                AndroidView(
                    modifier = Modifier
                        .heightIn(min = 100.dp, max = 220.dp)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            isEditable             = false
                            isLineNumberEnabled    = false
                            isHighlightCurrentLine = false
                            isWordwrap             = true
                            setText(note.content.ifBlank { "Empty note" })
                            activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { editor ->
                        val incoming = note.content.ifBlank { "Empty note" }
                        if (editor.text.toString() != incoming) editor.setText(incoming)
                        activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                    }
                )
            }
        }
    }
}
