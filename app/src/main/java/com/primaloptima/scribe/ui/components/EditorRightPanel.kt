package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.primaloptima.scribe.R
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.OutlineEntry
import io.github.rosemoe.sora.widget.CodeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorRightPanel(
    rightPanelTab       : Int,
    onTabChange         : (Int) -> Unit,
    pinnedTopNotes      : List<String>,
    pinnedTopIndex      : Int,
    pinnedBottomNotes   : List<String>,
    pinnedBottomIndex   : Int,
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    outline             : List<OutlineEntry>,
    activeTheme         : AppTheme?,
    soraEditorRef       : CodeEditor?,
    tabBarAtBottom      : Boolean,
    splitHorizontal     : Boolean,
    onToggleTabBarPos   : () -> Unit,
    onToggleSplitLayout : () -> Unit,
    onSwapSlots         : () -> Unit,
    onPrevTop           : () -> Unit,
    onNextTop           : () -> Unit,
    onSwitchTop         : () -> Unit,
    onEditTop           : (String) -> Unit,
    onRemoveTop         : (String) -> Unit,
    onPrevBottom        : () -> Unit,
    onNextBottom        : () -> Unit,
    onSwitchBottom      : () -> Unit,
    onEditBottom        : (String) -> Unit,
    onRemoveBottom      : (String) -> Unit,
    onPickTop           : () -> Unit,
    onPickBottom        : () -> Unit,
    onClose             : () -> Unit,
    barBlurBitmap       : Bitmap?,
    hazeState           : dev.chrisbanes.haze.HazeState,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    val tabBarContent: @Composable () -> Unit = {
        Surface(
            shape    = RoundedCornerShape(50),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(3.dp)
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                PillTab(label = "Pinned",  selected = rightPanelTab == 0, onClick = { onTabChange(0) })
                PillTab(label = "Outline", selected = rightPanelTab == 1, onClick = { onTabChange(1) })
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .frostedPanel(hazeState)
        ) {
            Column(Modifier.fillMaxSize()) {

                if (!tabBarAtBottom) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor             = Color.Transparent,
                            titleContentColor          = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary,
                            actionIconContentColor     = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.frostedBar(hazeState),
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                            }
                        },
                        title = { tabBarContent() },
                        actions = {
                            IconButton(onClick = onToggleTabBarPos) {
                                Icon(Icons.Default.VerticalAlignBottom, "Move tabs to bottom",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (rightPanelTab) {
                        0 -> {
                            val gapDp = 3.dp
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val totalPxFloat = with(density) {
                                    if (splitHorizontal) maxWidth.toPx() else maxHeight.toPx()
                                }
                                val headerDragThresholdPx = with(density) { 40.dp.toPx() }
                                var headerDragAccX by remember { mutableFloatStateOf(0f) }
                                var headerDragAccY by remember { mutableFloatStateOf(0f) }

                                val onTopHeaderDrag: (Float, Float) -> Unit = { dx, dy ->
                                    headerDragAccX += dx; headerDragAccY += dy
                                }
                                val onBottomHeaderDrag: (Float, Float) -> Unit = { dx, dy ->
                                    headerDragAccX += dx; headerDragAccY += dy
                                }
                                val onHeaderDragEnd: () -> Unit = {
                                    val ax = if (headerDragAccX < 0) -headerDragAccX else headerDragAccX
                                    val ay = if (headerDragAccY < 0) -headerDragAccY else headerDragAccY
                                    if (ax > headerDragThresholdPx || ay > headerDragThresholdPx) {
                                        if (ay > ax) onSwapSlots() else onToggleSplitLayout()
                                    }
                                    headerDragAccX = 0f; headerDragAccY = 0f
                                }

                                if (splitHorizontal) {
                                    Row(
                                        modifier              = Modifier.fillMaxSize().padding(gapDp),
                                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                                    ) {
                                        PinnedNoteSlot(
                                            modifier        = Modifier.fillMaxHeight().weight(splitFraction),
                                            pinnedIds       = pinnedTopNotes,
                                            pinnedIndex     = pinnedTopIndex,
                                            allNotes        = allNotes,
                                            worldEntries    = worldEntries,
                                            activeTheme     = activeTheme,
                                            onPrev          = onPrevTop,
                                            onNext          = onNextTop,
                                            onSwitch        = onSwitchTop,
                                            onEdit          = onEditTop,
                                            onRemove        = onRemoveTop,
                                            onPick          = onPickTop,
                                            hazeState       = hazeState,
                                            onHeaderDrag    = onTopHeaderDrag,
                                            onHeaderDragEnd = onHeaderDragEnd,
                                        )
                                        SplitDivider(
                                            isHorizontal = true,
                                            onDrag       = { delta ->
                                                splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                            },
                                            onSwap      = onSwapSlots,
                                            accentColor = accentColor,
                                            hazeState   = hazeState,
                                        )
                                        PinnedNoteSlot(
                                            modifier        = Modifier.fillMaxHeight().weight(1f - splitFraction),
                                            pinnedIds       = pinnedBottomNotes,
                                            pinnedIndex     = pinnedBottomIndex,
                                            allNotes        = allNotes,
                                            worldEntries    = worldEntries,
                                            activeTheme     = activeTheme,
                                            onPrev          = onPrevBottom,
                                            onNext          = onNextBottom,
                                            onSwitch        = onSwitchBottom,
                                            onEdit          = onEditBottom,
                                            onRemove        = onRemoveBottom,
                                            onPick          = onPickBottom,
                                            hazeState       = hazeState,
                                            onHeaderDrag    = onBottomHeaderDrag,
                                            onHeaderDragEnd = onHeaderDragEnd,
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier            = Modifier.fillMaxSize().padding(gapDp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp)
                                    ) {
                                        PinnedNoteSlot(
                                            modifier        = Modifier.fillMaxWidth().weight(splitFraction),
                                            pinnedIds       = pinnedTopNotes,
                                            pinnedIndex     = pinnedTopIndex,
                                            allNotes        = allNotes,
                                            worldEntries    = worldEntries,
                                            activeTheme     = activeTheme,
                                            onPrev          = onPrevTop,
                                            onNext          = onNextTop,
                                            onSwitch        = onSwitchTop,
                                            onEdit          = onEditTop,
                                            onRemove        = onRemoveTop,
                                            onPick          = onPickTop,
                                            hazeState       = hazeState,
                                            onHeaderDrag    = onTopHeaderDrag,
                                            onHeaderDragEnd = onHeaderDragEnd,
                                        )
                                        SplitDivider(
                                            isHorizontal = false,
                                            onDrag       = { delta ->
                                                splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                            },
                                            onSwap      = onSwapSlots,
                                            accentColor = accentColor,
                                            hazeState   = hazeState,
                                        )
                                        PinnedNoteSlot(
                                            modifier        = Modifier.fillMaxWidth().weight(1f - splitFraction),
                                            pinnedIds       = pinnedBottomNotes,
                                            pinnedIndex     = pinnedBottomIndex,
                                            allNotes        = allNotes,
                                            worldEntries    = worldEntries,
                                            activeTheme     = activeTheme,
                                            onPrev          = onPrevBottom,
                                            onNext          = onNextBottom,
                                            onSwitch        = onSwitchBottom,
                                            onEdit          = onEditBottom,
                                            onRemove        = onRemoveBottom,
                                            onPick          = onPickBottom,
                                            hazeState       = hazeState,
                                            onHeaderDrag    = onBottomHeaderDrag,
                                            onHeaderDragEnd = onHeaderDragEnd,
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (outline.isEmpty()) {
                                Box(
                                    modifier         = Modifier.fillMaxSize().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint     = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Text(
                                            "No headings yet",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 16.sp,
                                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Use # Heading to structure\nyour writing",
                                            fontSize  = 13.sp,
                                            color     = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(outline) { entry ->
                                        val indentDp   = ((entry.level - 1) * 16).dp
                                        val isTopLevel = entry.level == 1
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = indentDp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isTopLevel) MaterialTheme.colorScheme.surfaceVariant
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    soraEditorRef?.let { editor ->
                                                        val pos = editor.text.toString().indexOf(entry.text)
                                                        if (pos >= 0) {
                                                            val line = editor.text.indexer.getCharLine(pos)
                                                            val col  = editor.text.indexer.getCharColumn(pos)
                                                            editor.cursor.set(line, col)
                                                            editor.ensurePositionVisible(line, col)
                                                        }
                                                    }
                                                    onClose()
                                                }
                                                .padding(
                                                    horizontal = if (isTopLevel) 14.dp else 10.dp,
                                                    vertical   = if (isTopLevel) 12.dp else 8.dp
                                                ),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (isTopLevel) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    "H${entry.level}",
                                                    fontSize   = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = if (isTopLevel) MaterialTheme.colorScheme.onPrimary
                                                                 else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text       = entry.text,
                                                fontSize   = if (isTopLevel) 15.sp else 13.sp,
                                                fontWeight = if (isTopLevel) FontWeight.SemiBold else FontWeight.Normal,
                                                color      = MaterialTheme.colorScheme.onSurface,
                                                maxLines   = 2,
                                                overflow   = TextOverflow.Ellipsis,
                                                modifier   = Modifier.weight(1f)
                                            )
                                            if (isTopLevel) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint     = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (tabBarAtBottom) {
                    Surface(
                        color    = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().frostedBar(hazeState)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                            }
                            tabBarContent()
                            IconButton(onClick = onToggleTabBarPos) {
                                Icon(Icons.Default.VerticalAlignTop, "Move tabs to top",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDivider(
    isHorizontal : Boolean,
    onDrag       : (Float) -> Unit,
    onSwap       : () -> Unit,
    accentColor  : Color,
    hazeState    : dev.chrisbanes.haze.HazeState,
) {
    val haptic = LocalHapticFeedback.current

    if (isHorizontal) {
        Box(
            modifier         = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume(); onDrag(dragAmount.x)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(26.dp).pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwap()
                    })
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint               = accentColor,
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume(); onDrag(dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(26.dp).width(44.dp).pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwap()
                    })
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint               = accentColor,
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape           = RoundedCornerShape(50),
        color           = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier        = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PinnedNoteSlot(
    modifier        : Modifier = Modifier,
    pinnedIds       : List<String>,
    pinnedIndex     : Int,
    allNotes        : List<Note>,
    worldEntries    : List<WorldEntry>,
    activeTheme     : AppTheme?,
    onPrev          : () -> Unit,
    onNext          : () -> Unit,
    onSwitch        : () -> Unit,
    onEdit          : (String) -> Unit,
    onRemove        : (String) -> Unit,
    onPick          : () -> Unit,
    hazeState       : dev.chrisbanes.haze.HazeState,
    onHeaderDrag    : ((dx: Float, dy: Float) -> Unit)? = null,
    onHeaderDragEnd : (() -> Unit)? = null,
) {
    val currentId = pinnedIds.getOrNull(pinnedIndex)
    val currentNote = remember(currentId, allNotes, worldEntries) {
        allNotes.firstOrNull { it.id == currentId }
            ?: worldEntries.firstOrNull { it.id == currentId }?.let { w ->
                Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
    ) {
        if (currentNote == null) {
            Column(
                modifier            = Modifier.fillMaxSize().clickable(onClick = onPick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Pin a reference note",
                             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Pin a reference note", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text("Tap to browse your vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                var headerDragging by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (headerDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
                        .pointerInput(onHeaderDrag, onHeaderDragEnd) {
                            if (onHeaderDrag == null) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart  = { headerDragging = true },
                                onDragEnd    = { headerDragging = false; onHeaderDragEnd?.invoke() },
                                onDragCancel = { headerDragging = false },
                                onDrag       = { change, dragAmount ->
                                    change.consume()
                                    onHeaderDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape    = RoundedCornerShape(50),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(
                            text       = currentNote.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 12.sp,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (pinnedIds.size > 1) {
                        Text("${pinnedIndex + 1}/${pinnedIds.size}", fontSize = 10.sp,
                             color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 2.dp))
                        IconButton(onClick = onPrev, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(14.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = onSwitch, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.SwapHoriz, "Switch note", Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onEdit(currentNote.id) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, "Edit in main editor", Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onRemove(currentNote.id) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, "Unpin", Modifier.size(14.dp))
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp),
                    factory  = { ctx ->
                        CodeEditor(ctx).apply {
                            isEditable             = false
                            isLineNumberEnabled    = false
                            isHighlightCurrentLine = false
                            isWordwrap             = true
                            setText(currentNote.content.ifBlank { "(Empty note content)" })
                            activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { editor ->
                        val incoming = currentNote.content.ifBlank { "(Empty note content)" }
                        if (editor.text.toString() != incoming) editor.setText(incoming)
                        activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                    }
                )
            }
        }
    }
}
