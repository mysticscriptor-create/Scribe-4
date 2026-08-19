package com.primaloptima.scribe.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeEditorTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.res.painterResource
import com.primaloptima.scribe.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FloatingWindowOverlay
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.NoteListViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Sora Editor imports ───────────────────────────────────────────────────────
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import com.primaloptima.scribe.util.ScribeProseLanguage
import com.primaloptima.scribe.util.ThemeManager


private enum class PanelState { LeftOpen, Center, RightOpen }

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun MainEditorScreen(
    editorVm: EditorViewModel,
    bookVm: BookViewModel,
    noteListVm: NoteListViewModel,
    shortcutsVm: ShortcutsViewModel,
    initialNoteId: String?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSheets: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Panel gesture state ───────────────────────────────────────────────────
    val localDensity = LocalDensity.current
    val panelState = remember {
        AnchoredDraggableState(
            initialValue        = PanelState.Center,
            positionalThreshold = { distance -> distance * 0.4f },
            velocityThreshold   = { with(localDensity) { 125.dp.toPx() } },
            animationSpec       = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    val isLeftDrawerOpen = panelState.targetValue == PanelState.LeftOpen
    val isRightPanelOpen = panelState.targetValue == PanelState.RightOpen

    // ── Frosted-glass blur bitmaps (pre-API-31 fallback) ─────────────────────
    val view         = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current
        .toInt().coerceIn(1, 25)
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val barBlurBitmap = LocalBarBlurBitmap.current

    val editorTheme  = LocalAppTheme.current
    val editorBgUri  = editorTheme?.backgroundImageUri

    // ── ViewModel state ───────────────────────────────────────────────────────
    val activeNote     by editorVm.activeNote.collectAsStateWithLifecycle()
    val wordCount      by editorVm.wordCount.collectAsStateWithLifecycle()
    val charCount      by editorVm.charCount.collectAsStateWithLifecycle()
    val outline        by editorVm.outline.collectAsStateWithLifecycle()
    val zenMode        by editorVm.zenMode.collectAsStateWithLifecycle()
    val activeTheme    by editorVm.theme.collectAsStateWithLifecycle()
    val goalProgress   by editorVm.goalProgress.collectAsStateWithLifecycle()

    val bgUri        = activeTheme?.backgroundImageUri
    val bgMode       = activeTheme?.bgMode ?: "color"
    val themeScope   = activeTheme?.themeScope ?: "editor_only"
    val bgOpacity    = activeTheme?.backgroundImageOpacity ?: 0.35f
    val blurIntensity = activeTheme?.blurIntensity ?: 0f
    val hasBgImage   = !bgUri.isNullOrEmpty() && bgMode != "color"
    val isEditorOnlyBg = hasBgImage && themeScope == "editor_only"

    val currentBookNotes   by bookVm.notes.collectAsStateWithLifecycle()
    val currentBookFolders by bookVm.folders.collectAsStateWithLifecycle()
    val worldEntries       by bookVm.worldEntries.collectAsStateWithLifecycle()

    val allNotes   by noteListVm.notes.collectAsStateWithLifecycle()
    val allFolders by noteListVm.folders.collectAsStateWithLifecycle()
    val shortcuts  by shortcutsVm.shortcuts.collectAsStateWithLifecycle()

    val floatingWindows    by editorVm.floatingWindows.collectAsStateWithLifecycle()
    val pinnedTopNotes     by editorVm.pinnedTopNotes.collectAsStateWithLifecycle()
    val pinnedTopIndex     by editorVm.pinnedTopIndex.collectAsStateWithLifecycle()
    val pinnedBottomNotes  by editorVm.pinnedBottomNotes.collectAsStateWithLifecycle()
    val pinnedBottomIndex  by editorVm.pinnedBottomIndex.collectAsStateWithLifecycle()
    val companionTabBarBottom   by editorVm.companionTabBarBottom.collectAsStateWithLifecycle()
    val companionSplitHorizontal by editorVm.companionSplitHorizontal.collectAsStateWithLifecycle()

    // ── Local UI state ────────────────────────────────────────────────────────
    var rightPanelTab   by remember { mutableIntStateOf(0) }
    var leftPanelTab    by remember { mutableIntStateOf(0) }
    var leftDrawerMode  by remember { mutableStateOf("Current") }
    var leftSearchQuery by remember { mutableStateOf("") }

    var showFindBar    by remember { mutableStateOf(false) }
    var findQuery      by remember { mutableStateOf("") }
    var replaceQuery   by remember { mutableStateOf("") }

    var showRenameDialog     by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var filePickerTargetSlot by remember { mutableStateOf<String?>(null) }

    val anyDialogOpen = showRenameDialog || showCreateNoteDialog || filePickerTargetSlot != null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) { if (!anyDialogOpen) dialogOneShotBitmap = null }
    }

    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val raw = BitmapBlur.captureOnly(view)
            dialogOneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
            }
        }
        openDialog()
    }

    // ── Sora CodeEditor state ─────────────────────────────────────────────────
    var soraEditorRef  by remember { mutableStateOf<CodeEditor?>(null) }
    var loadedNoteId   by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedTreeState = remember { mutableStateMapOf<String, Boolean>() }

    var pillMode     by remember { mutableIntStateOf(0) }
    var pillOffsetX  by remember { mutableFloatStateOf(0f) }
    var pillOffsetY  by remember { mutableFloatStateOf(0f) }

    var prevWordCount   by remember { mutableIntStateOf(wordCount) }
    var deltaText       by remember { mutableStateOf<String?>(null) }
    var isPositiveDelta by remember { mutableStateOf(true) }
    var goalNotified    by remember { mutableStateOf(false) }

    LaunchedEffect(wordCount) {
        val diff = wordCount - prevWordCount
        if (diff != 0) {
            deltaText       = if (diff > 0) "+$diff" else "$diff"
            isPositiveDelta = diff > 0
            prevWordCount   = wordCount
            delay(800)
            deltaText = null
        }
    }
    LaunchedEffect(goalProgress) {
        if (goalProgress >= 1f && !goalNotified && wordCount > 0) {
            goalNotified = true
            Toast.makeText(context, "Daily writing goal reached!", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(initialNoteId) {
        if (!initialNoteId.isNullOrEmpty()) editorVm.loadNote(initialNoteId)
        else if (currentBookNotes.isNotEmpty()) editorVm.loadNote(currentBookNotes.first().id)
    }
    LaunchedEffect(activeNote?.id, activeNote?.content, soraEditorRef) {
        val note   = activeNote ?: return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        if (loadedNoteId != note.id || (editor.text.length == 0 && note.content.isNotEmpty())) {
            loadedNoteId = note.id
            editor.setText(note.content)
        }
    }

    val soraEditorForDispose = soraEditorRef
    DisposableEffect(activeNote?.id) {
        onDispose {
            activeNote?.let {
                editorVm.saveVersionSnapshotOnLeave(soraEditorForDispose?.text?.toString() ?: "")
            }
        }
    }

    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "External Folder"
        noteListVm.connectExternalFolder(uri, name)
    }

    // ── Back-press handlers ───────────────────────────────────────────────────
    if (isLeftDrawerOpen || isRightPanelOpen) {
        BackHandler { scope.launch { panelState.animateTo(PanelState.Center) } }
    }

    val isKeyboardVisible = WindowInsets.isImeVisible

    Box(modifier = Modifier.fillMaxSize()) {
        val hazeState = LocalHazeState.current ?: dev.chrisbanes.haze.HazeState()

        // ── Editor-only background image ──────────────────────────────────────
        if (isEditorOnlyBg) {
            AsyncImage(
                model            = bgUri,
                contentDescription = null,
                contentScale     = ContentScale.Crop,
                modifier         = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgMode == "blurred" &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            blurIntensity > 0f
                        ) Modifier.graphicsLayer {
                            val r = blurIntensity * density
                            if (r > 0f) renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else Modifier
                    )
            )
            val themeBgColor = parseComposeColor(
                activeTheme?.colors?.background ?: "#FAFAF7", Color(0xFFFAFAF7)
            )
            Box(Modifier.fillMaxSize().background(themeBgColor.copy(alpha = bgOpacity)))
        }

        // ── Custom push-drawer Layout ─────────────────────────────────────────
        // Three children: left drawer (300dp), editor (full), right panel (full).
        // panelState.offset drives all positions: +drawerW=LeftOpen, 0=Center, -screenW=RightOpen.
        Layout(
            content = {
                // Child 0: Left drawer (300dp wide)
                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                    Box(modifier = Modifier.fillMaxHeight().width(300.dp)) {
                        ModalDrawerSheet(
                            drawerContainerColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxSize()
                                .frostedPanel(hazeState)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            PrimaryTabRow(selectedTabIndex = leftPanelTab) {
                                Tab(selected = leftPanelTab == 0, onClick = { leftPanelTab = 0 },
                                    text = { Text("Files", fontWeight = FontWeight.Bold) })
                                Tab(selected = leftPanelTab == 1, onClick = { leftPanelTab = 1 },
                                    text = { Text("World", fontWeight = FontWeight.Bold) })
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(selected = leftDrawerMode == "Current", onClick = { leftDrawerMode = "Current" },
                                    label = { Text("Current Book", fontSize = 12.sp) })
                                FilterChip(selected = leftDrawerMode == "Books", onClick = { leftDrawerMode = "Books" },
                                    label = { Text("All Books", fontSize = 12.sp) })
                            }
                            HorizontalDivider(Modifier.padding(bottom = 4.dp))
                            val displayNotes   = if (leftDrawerMode == "Current") currentBookNotes else allNotes
                            val displayFolders = if (leftDrawerMode == "Current") currentBookFolders else allFolders
                            val folderGrouped2 = remember(displayNotes, displayFolders) {
                                buildMap<String, MutableList<Note>> {
                                    displayNotes.forEach { n ->
                                        getOrPut(n.folderPath.ifBlank { "/" }) { mutableListOf() }.add(n)
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Notes", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(
                                    onClick = { scope.launch { captureForDialog { showCreateNoteDialog = true } } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New Note", modifier = Modifier.size(18.dp))
                                }
                            }
                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                                folderGrouped2.forEach { (folderPath, notesInFolder) ->
                                    val isExpanded = expandedTreeState[folderPath] ?: true
                                    item(key = "fd_$folderPath") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                                .clickable { expandedTreeState[folderPath] = !isExpanded }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(if (isExpanded) Icons.Default.KeyboardArrowDown
                                                 else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                 contentDescription = null, modifier = Modifier.size(16.dp),
                                                 tint = MaterialTheme.colorScheme.outline)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                 contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                                 modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(folderPath.substringAfterLast('/').ifEmpty { "Root" },
                                                 fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                                 modifier = Modifier.weight(1f))
                                            Text("${notesInFolder.size}", fontSize = 11.sp,
                                                 color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    if (isExpanded) {
                                        items(notesInFolder, key = { "nd_${it.id}" }) { note ->
                                            val isActive = note.id == activeNote?.id
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                                    .clickable {
                                                        editorVm.loadNote(note.id)
                                                        scope.launch { panelState.animateTo(PanelState.Center) }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Outlined.Description, contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                                Spacer(Modifier.width(8.dp))
                                                Text(note.name, fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f))
                                                Text("${note.wordCount}w", fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Child 1: Main editor (full screen, pushed right when drawer opens)
                Scaffold(
                    containerColor      = Color.Transparent,
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
                    topBar = {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            ScribeEditorTopBar(
                                title          = activeNote?.name,
                                onNavClick     = { scope.launch { panelState.animateTo(if (isLeftDrawerOpen) PanelState.Center else PanelState.LeftOpen) } },
                                onTitleClick   = {
                                    if (activeNote != null)
                                        scope.launch { captureForDialog { showRenameDialog = true } }
                                },
                                navigationIcon = Icons.Default.Menu,
                                visible        = !zenMode,
                                actions        = listOf(
                                    ScribeBarAction(Icons.Default.Dock,        "Outline & Pinned Notes") { scope.launch { panelState.animateTo(PanelState.RightOpen) } },
                                    ScribeBarAction(Icons.Default.Search,      "Find")                   { showFindBar = !showFindBar },
                                    ScribeBarAction(Icons.Default.BookmarkAdd, "Save Checkpoint")        {
                                        editorVm.saveManualSnapshot(soraEditorRef?.text?.toString() ?: "")
                                        Toast.makeText(context, "Checkpoint saved", Toast.LENGTH_SHORT).show()
                                    },
                                    ScribeBarAction(Icons.Default.MoreVert,    "Menu")                   { showMenu = true },
                                ),
                                extraContent = {
                                    if (!zenMode) {
                                        LinearProgressIndicator(
                                            progress   = { goalProgress },
                                            modifier   = Modifier.fillMaxWidth().height(3.dp),
                                            color      = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded         = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor   = LocalSolidSurface.current
                            ) {
                                DropdownMenuItem(text = { Text("Enter Zen Mode") }, onClick = { showMenu = false; editorVm.setZen(true) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Open as Floating Reference Window") }, onClick = { showMenu = false; activeNote?.let { editorVm.openFloatingWindow(it.id) } })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Export as TXT") },      onClick = { showMenu = false; activeNote?.let { ExportHelper.shareNote(context, it, "txt") } })
                                DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { showMenu = false; activeNote?.let { ExportHelper.shareNote(context, it, "md") } })
                                DropdownMenuItem(text = { Text("Export as HTML") },     onClick = { showMenu = false; activeNote?.let { ExportHelper.shareNote(context, it, "html") } })
                                DropdownMenuItem(text = { Text("Export as PDF") },      onClick = { showMenu = false; activeNote?.let { ExportHelper.shareNote(context, it, "pdf") } })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Version History") }, onClick = { showMenu = false; editorVm.flushContent(soraEditorRef?.text?.toString() ?: ""); onOpenHistory() })
                                DropdownMenuItem(text = { Text("Shortcuts") },       onClick = { showMenu = false; onOpenShortcuts() })
                                DropdownMenuItem(text = { Text("User Guide") },      onClick = { showMenu = false; onOpenGuide() })
                                DropdownMenuItem(text = { Text("Settings") },        onClick = { showMenu = false; onOpenSettings() })
                            }
                        }
                    },
                    bottomBar = {
                        CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                            val isKeyboardVisible = WindowInsets.isImeVisible
                            AnimatedVisibility(
                                visible = isKeyboardVisible,
                                enter   = slideInVertically(initialOffsetY = { it }),
                                exit    = slideOutVertically(targetOffsetY = { it })
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .frostedBar(hazeState)
                                        .imePadding()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    shortcuts.forEach { shortcut ->
                                        FormatButton(label = shortcut.label) {
                                            when (shortcut.kind) {
                                                "wrap" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: shortcut.payload)
                                                "pair" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: "")
                                                else   -> soraEditorRef?.insertAtCursor(shortcut.payload)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Column(Modifier.fillMaxSize()) {

                            // ── Find/Replace bar ──────────────────────────────
                            if (showFindBar) {
                                Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier          = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value         = findQuery,
                                            onValueChange = { findQuery = it },
                                            placeholder   = { Text("Find") },
                                            singleLine    = true,
                                            modifier      = Modifier.weight(1f).height(48.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        OutlinedTextField(
                                            value         = replaceQuery,
                                            onValueChange = { replaceQuery = it },
                                            placeholder   = { Text("Replace") },
                                            singleLine    = true,
                                            modifier      = Modifier.weight(1f).height(48.dp)
                                        )
                                        IconButton(onClick = { soraEditorRef?.searcher?.gotoPrevious() }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                                        }
                                        IconButton(onClick = { soraEditorRef?.searcher?.gotoNext() }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                                        }
                                        IconButton(onClick = {
                                            val editor = soraEditorRef ?: return@IconButton
                                            if (findQuery.isNotEmpty()) {
                                                editor.searcher.replaceAll(replaceQuery)
                                                editorVm.onContentChanged(editor.text.toString())
                                            }
                                        }) {
                                            Icon(Icons.Default.FindReplace, contentDescription = "Replace All")
                                        }
                                        IconButton(onClick = { showFindBar = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(findQuery, showFindBar) {
                                val editor = soraEditorRef ?: return@LaunchedEffect
                                if (showFindBar && findQuery.isNotEmpty()) {
                                    editor.searcher.search(findQuery, EditorSearcher.SearchOptions(true, false))
                                } else {
                                    editor.searcher.stopSearch()
                                }
                            }

                            // ── Sora CodeEditor ───────────────────────────────
                            val currentThemeBg        = MaterialTheme.colorScheme.background
                            val currentThemeTextColor = MaterialTheme.colorScheme.onBackground
                            val currentThemePrimary   = MaterialTheme.colorScheme.primary
                            val hasBgImageLocal = !activeTheme?.backgroundImageUri.isNullOrEmpty()

                            Box(Modifier.fillMaxSize()) {
                                val editorTextSizeSp = (activeTheme?.fontSize ?: 18).toFloat()
                                val editorTypeface   = activeTheme?.fontFamily?.let {
                                    ThemeManager.resolveTypeface(context, it)
                                }
                                val bgArgb = if (hasBgImageLocal) android.graphics.Color.TRANSPARENT
                                             else currentThemeBg.toArgb()

                                AndroidView(
                                    factory = { ctx ->
                                        CodeEditor(ctx).apply {
                                            isLineNumberEnabled    = false
                                            isHighlightCurrentLine = false
                                            isWordwrap             = true
                                            setEditorLanguage(ScribeProseLanguage())

                                            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                                if (loadedNoteId != null)
                                                    editorVm.onContentChanged(text.toString())
                                            }
                                            subscribeEvent(EditorKeyEvent::class.java) { event, _ ->
                                                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@subscribeEvent
                                                if (event.keyCode != android.view.KeyEvent.KEYCODE_ENTER) return@subscribeEvent
                                                val cur = this.cursor
                                                if (cur.isSelected) return@subscribeEvent
                                                val line = this.text.getLine(cur.leftLine)
                                                val col  = cur.leftColumn
                                                val closeChars = setOf(')', ']', '}', '`', '"', '\'', '\u201D', '\u2019', '\u00BB')
                                                if (col < line.length && line[col] in closeChars) {
                                                    setSelection(cur.leftLine, col + 1)
                                                    event.intercept()
                                                }
                                            }
                                        }.also { soraEditorRef = it }
                                    },
                                    update = { editor ->
                                        editor.setTextSize(editorTextSizeSp)
                                        editorTypeface?.let { editor.typefaceText = it }
                                        editor.setBackgroundColor(bgArgb)
                                        activeTheme?.let { theme ->
                                            val scheme = ScribeColorScheme(theme)
                                            scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,       bgArgb)
                                            scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgArgb)
                                            scheme.setColor(EditorColorScheme.LINE_NUMBER,            bgArgb)
                                            editor.colorScheme = scheme
                                            val density    = context.resources.displayMetrics.density
                                            val cornerPx   = 24f * density
                                            val accentArgb = android.graphics.Color.parseColor(theme.colors.accent)
                                            val surfaceArgb = android.graphics.Color.parseColor(theme.colors.surface)
                                            val fill = android.graphics.drawable.GradientDrawable().apply {
                                                setColor(surfaceArgb); cornerRadius = cornerPx
                                            }
                                            val overlay = android.graphics.drawable.GradientDrawable(
                                                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                                                intArrayOf(
                                                    android.graphics.Color.argb(71,
                                                        android.graphics.Color.red(accentArgb),
                                                        android.graphics.Color.green(accentArgb),
                                                        android.graphics.Color.blue(accentArgb)),
                                                    android.graphics.Color.TRANSPARENT
                                                )
                                            ).apply { cornerRadius = cornerPx }
                                            val popupBg = android.graphics.drawable.LayerDrawable(arrayOf(fill, overlay))
                                            try {
                                                val aw = editor.getComponent(
                                                    io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
                                                )
                                                var popup: android.widget.PopupWindow? = null
                                                var cls: Class<*>? = aw.javaClass
                                                outer@ while (cls != null && cls != Any::class.java) {
                                                    for (f in cls.declaredFields) {
                                                        if (android.widget.PopupWindow::class.java.isAssignableFrom(f.type)) {
                                                            f.isAccessible = true
                                                            popup = f.get(aw) as? android.widget.PopupWindow
                                                            break@outer
                                                        }
                                                    }
                                                    cls = cls.superclass
                                                }
                                                popup?.setBackgroundDrawable(popupBg)
                                            } catch (_: Exception) { }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset { IntOffset(pillOffsetX.roundToInt(), pillOffsetY.roundToInt()) }
                                            .padding(12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            AnimatedVisibility(
                                                visible = deltaText != null,
                                                enter   = fadeIn() + slideInVertically { -20 },
                                                exit    = fadeOut() + slideOutVertically { -20 }
                                            ) {
                                                Text(
                                                    text       = deltaText ?: "",
                                                    fontSize   = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = if (isPositiveDelta) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    modifier   = Modifier.padding(bottom = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape          = CircleShape,
                                                color          = frostedContainerColor(MaterialTheme.colorScheme.primaryContainer),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp,
                                                modifier       = Modifier
                                                    .clip(CircleShape)
                                                    .frostedFab(LocalHazeState.current)
                                                    .pointerInput(Unit) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            pillOffsetX += dragAmount.x
                                                            pillOffsetY += dragAmount.y
                                                        }
                                                    }
                                                    .clickable { pillMode = (pillMode + 1) % 3 }
                                            ) {
                                                AnimatedContent(
                                                    targetState  = pillMode,
                                                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                                                ) { mode ->
                                                    Text(
                                                        text = when (mode) {
                                                            1    -> "$wordCount words · $charCount chars"
                                                            2    -> "$wordCount words · ${maxOf(1, wordCount / 200)}m"
                                                            else -> "$wordCount words"
                                                        },
                                                        fontSize   = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (zenMode) {
                                        ScribeSingleFab(
                                            icon               = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Zen",
                                            modifier           = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                            onClick            = { editorVm.setZen(false) }
                                        )
                                    }
                                }
                            } // end editor Box
                        }
                    }
                } // end Scaffold

                // Child 2: Right companion panel (full screen, slides in from right)
                RightCompanionPanel(
                    rightPanelTab         = rightPanelTab,
                    onTabChange           = { rightPanelTab = it },
                    pinnedTopNotes        = pinnedTopNotes,
                    pinnedTopIndex        = pinnedTopIndex,
                    pinnedBottomNotes     = pinnedBottomNotes,
                    pinnedBottomIndex     = pinnedBottomIndex,
                    allNotes              = allNotes,
                    worldEntries          = worldEntries,
                    outline               = outline,
                    activeTheme           = activeTheme,
                    soraEditorRef         = soraEditorRef,
                    tabBarAtBottom        = companionTabBarBottom,
                    splitHorizontal       = companionSplitHorizontal,
                    onToggleTabBarPos     = { editorVm.setCompanionTabBarBottom(!companionTabBarBottom) },
                    onToggleSplitLayout   = { editorVm.setCompanionSplitHorizontal(!companionSplitHorizontal) },
                    onSwapSlots           = { editorVm.swapPinnedSlots() },
                    onPrevTop             = { editorVm.prevPinnedTop() },
                    onNextTop             = { editorVm.nextPinnedTop() },
                    onSwitchTop           = { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } },
                    onEditTop             = { id -> editorVm.loadNote(id) },
                    onRemoveTop           = { id -> editorVm.removePinnedTop(id) },
                    onPrevBottom          = { editorVm.prevPinnedBottom() },
                    onNextBottom          = { editorVm.nextPinnedBottom() },
                    onSwitchBottom        = { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } },
                    onEditBottom          = { id -> editorVm.loadNote(id) },
                    onRemoveBottom        = { id -> editorVm.removePinnedBottom(id) },
                    onPickTop             = { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } },
                    onPickBottom          = { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } },
                    onClose               = { scope.launch { panelState.animateTo(PanelState.Center) } },
                    barBlurBitmap         = barBlurBitmap,
                    hazeState             = hazeState,
                )
            }, // end Layout content lambda
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isKeyboardVisible)
                        Modifier.anchoredDraggable(panelState, Orientation.Horizontal)
                    else Modifier
                )
        ) { measurables, constraints ->
            val drawerWidthPx = (300 * density).toInt()
            val screenWidth   = constraints.maxWidth
            val screenHeight  = constraints.maxHeight

            val drawerPlaceable = measurables[0].measure(Constraints.fixed(drawerWidthPx, screenHeight))
            val editorPlaceable = measurables[1].measure(Constraints.fixed(screenWidth, screenHeight))
            val rightPlaceable  = measurables[2].measure(Constraints.fixed(screenWidth, screenHeight))

            layout(screenWidth, screenHeight) {
                panelState.updateAnchors(DraggableAnchors {
                    PanelState.LeftOpen  at drawerWidthPx.toFloat()
                    PanelState.Center    at 0f
                    PanelState.RightOpen at -screenWidth.toFloat()
                })
                val offset = panelState.requireOffset()
                drawerPlaceable.placeRelative(x = (offset - drawerWidthPx).roundToInt(), y = 0)
                editorPlaceable.placeRelative(x = offset.roundToInt(), y = 0)
                rightPlaceable.placeRelative(x = (screenWidth + offset).roundToInt(), y = 0)
            }
        } // end Layout

        // ── Floating Windows Overlay ──────────────────────────────────────────
        val mappedNotes = remember(currentBookNotes, worldEntries) {
            buildList {
                addAll(currentBookNotes)
                worldEntries.forEach { w ->
                    if (none { it.id == w.id }) add(
                        Note(id = w.id, name = w.name,
                             content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}")
                    )
                }
            }
        }
        FloatingWindowOverlay(
            floatingWindows  = floatingWindows,
            notes            = mappedNotes,
            activeTheme      = activeTheme,
            onCloseWindow    = { id -> editorVm.closeFloatingWindow(id) },
            onToggleCollapse = { id -> editorVm.toggleCollapseFloatingWindow(id) },
            onMoveWindow     = { id, x, y -> editorVm.moveFloatingWindow(id, x, y) }
        )

        // ── Dialogs ───────────────────────────────────────────────────────────
        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
            if (showRenameDialog && activeNote != null) {
                val noteToRename = activeNote
                var renameText by remember { mutableStateOf(noteToRename?.name ?: "") }
                FrostedDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title            = { Text("Rename Note") },
                    text             = {
                        OutlinedTextField(
                            value         = renameText,
                            onValueChange = { renameText = it },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = renameText.trim()
                            if (t.isNotEmpty() && noteToRename != null) bookVm.renameNote(noteToRename.id, t)
                            showRenameDialog = false
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
                )
            }

            if (showCreateNoteDialog) {
                var noteTitle by remember { mutableStateOf("") }
                FrostedDialog(
                    onDismissRequest = { showCreateNoteDialog = false },
                    title            = { Text("New Note") },
                    text             = {
                        OutlinedTextField(
                            value         = noteTitle,
                            onValueChange = { noteTitle = it },
                            label         = { Text("Title") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = noteTitle.trim()
                            if (t.isNotEmpty()) bookVm.createNote(t) { id ->
                                showCreateNoteDialog = false
                                editorVm.loadNote(id)
                            }
                        }) { Text("Create") }
                    },
                    dismissButton = { TextButton(onClick = { showCreateNoteDialog = false }) { Text("Cancel") } }
                )
            }

            filePickerTargetSlot?.let { targetSlot ->
                FileExplorerOverlayDialog(
                    allNotes     = if (leftDrawerMode == "Current") currentBookNotes else allNotes,
                    allFolders   = if (leftDrawerMode == "Current") currentBookFolders else allFolders,
                    onSelectNote = { note ->
                        if (targetSlot == "top") editorVm.addPinnedTop(note.id)
                        else editorVm.addPinnedBottom(note.id)
                        filePickerTargetSlot = null
                    },
                    onDismiss = { filePickerTargetSlot = null }
                )
            }
        }
    } // end outer Box
}

// ─────────────────────────────────────────────────────────────────────────────
// Right Companion Panel
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RightCompanionPanel(
    rightPanelTab       : Int,
    onTabChange         : (Int) -> Unit,
    pinnedTopNotes      : List<String>,
    pinnedTopIndex      : Int,
    pinnedBottomNotes   : List<String>,
    pinnedBottomIndex   : Int,
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    outline             : List<com.primaloptima.scribe.util.model.OutlineEntry>,
    activeTheme         : com.primaloptima.scribe.util.model.AppTheme?,
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
    val haptic = LocalHapticFeedback.current
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

                // ── Top bar ───────────────────────────────────────────────────
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

                // ── Body ──────────────────────────────────────────────────────
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (rightPanelTab) {

                        // ── Pinned Notes tab ──────────────────────────────────
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
                                    headerDragAccX += dx
                                    headerDragAccY += dy
                                }
                                val onBottomHeaderDrag: (Float, Float) -> Unit = { dx, dy ->
                                    headerDragAccX += dx
                                    headerDragAccY += dy
                                }
                                val onHeaderDragEnd: () -> Unit = {
                                    val ax = if (headerDragAccX < 0) -headerDragAccX else headerDragAccX
                                    val ay = if (headerDragAccY < 0) -headerDragAccY else headerDragAccY
                                    if (ax > headerDragThresholdPx || ay > headerDragThresholdPx) {
                                        if (ay > ax) {
                                            onSwapSlots()
                                        } else {
                                            onToggleSplitLayout()
                                        }
                                    }
                                    headerDragAccX = 0f
                                    headerDragAccY = 0f
                                }

                                if (splitHorizontal) {
                                    Row(
                                        modifier            = Modifier.fillMaxSize().padding(gapDp),
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
                                            isHorizontal  = true,
                                            onDrag        = { delta ->
                                                splitFraction = (splitFraction + delta / totalPxFloat)
                                                    .coerceIn(0.2f, 0.8f)
                                            },
                                            onSwap        = onSwapSlots,
                                            accentColor   = accentColor,
                                            hazeState     = hazeState,
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
                                        modifier          = Modifier.fillMaxSize().padding(gapDp),
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
                                            isHorizontal  = false,
                                            onDrag        = { delta ->
                                                splitFraction = (splitFraction + delta / totalPxFloat)
                                                    .coerceIn(0.2f, 0.8f)
                                            },
                                            onSwap        = onSwapSlots,
                                            accentColor   = accentColor,
                                            hazeState     = hazeState,
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

                        // ── Outline tab ───────────────────────────────────────
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

                // ── Bottom bar (when tab bar is at bottom) ────────────────────
                if (tabBarAtBottom) {
                    Surface(
                        color    = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedBar(hazeState)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
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

// ── Split divider ─────────────────────────────────────────────────────────────
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
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwap()
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint              = accentColor,
                        modifier          = Modifier.size(14.dp)
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
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .height(26.dp)
                    .width(44.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwap()
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint              = accentColor,
                        modifier          = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Pill tab ──────────────────────────────────────────────────────────────────
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

// ── Pinned note slot ──────────────────────────────────────────────────────────
@Composable
private fun PinnedNoteSlot(
    modifier        : Modifier = Modifier,
    pinnedIds       : List<String>,
    pinnedIndex     : Int,
    allNotes        : List<Note>,
    worldEntries    : List<WorldEntry>,
    activeTheme     : com.primaloptima.scribe.util.model.AppTheme?,
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
                Surface(
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = "Pin a reference note",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
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
                                onDragStart = { headerDragging = true },
                                onDragEnd   = { headerDragging = false; onHeaderDragEnd?.invoke() },
                                onDragCancel = { headerDragging = false },
                                onDrag      = { change, dragAmount ->
                                    change.consume()
                                    onHeaderDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text(
                            text     = currentNote.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 12.sp,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (pinnedIds.size > 1) {
                        Text(
                            "${pinnedIndex + 1}/${pinnedIds.size}",
                            fontSize = 10.sp,
                            color    = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 2.dp)
                        )
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

// ── File explorer overlay ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerOverlayDialog(
    allNotes     : List<Note>,
    allFolders   : List<Folder>,
    onSelectNote : (Note) -> Unit,
    onDismiss    : () -> Unit
) {
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }
    val folderGrouped = remember(allNotes, allFolders) {
        buildMap<String, MutableList<Note>> {
            allNotes.forEach { n -> getOrPut(n.folderPath.ifBlank { "/" }) { mutableListOf() }.add(n) }
        }
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Pick a note to pin", fontWeight = FontWeight.Bold) },
        text             = {
            LazyColumn(
                modifier             = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement  = Arrangement.spacedBy(4.dp)
            ) {
                folderGrouped.forEach { (folderPath, notesInFolder) ->
                    val isExpanded = expandedPaths[folderPath] ?: true
                    item(key = "f_$folderPath") {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expandedPaths[folderPath] = !isExpanded }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(folderPath.substringAfterLast('/'), fontWeight = FontWeight.Bold,
                                 fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    if (isExpanded) {
                        items(notesInFolder, key = { "n_${it.id}" }) { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectNote(note) }.padding(start = 24.dp),
                                shape    = RoundedCornerShape(6.dp),
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(note.name, fontSize = 14.sp,
                                     modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormatButton(
    label      : String,
    isSelected : Boolean = false,
    onClick    : () -> Unit
) {
    Surface(
        onClick      = onClick,
        shape        = CircleShape,
        color        = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier     = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── CodeEditor extension helpers ──────────────────────────────────────────────

private fun CodeEditor.applyFormat(open: String, close: String) {
    val cur = cursor
    if (cur.isSelected) {
        val indexer  = text.indexer
        val startIdx = indexer.getCharIndex(cur.leftLine,  cur.leftColumn)
        val endIdx   = indexer.getCharIndex(cur.rightLine, cur.rightColumn)
        val selected = text.subSequence(startIdx, endIdx).toString()
        text.replace(
            cur.leftLine,  cur.leftColumn,
            cur.rightLine, cur.rightColumn,
            "$open$selected$close"
        )
    } else {
        val line = cur.leftLine
        val col  = cur.leftColumn
        text.insert(line, col, "$open$close")
        this.cursor.set(line, col + open.length)
    }
}

private fun CodeEditor.applyLinePrefix(prefix: String) {
    val line = cursor.leftLine
    text.insert(line, 0, prefix)
    cursor.set(line, cursor.leftColumn + prefix.length)
}

private fun CodeEditor.insertAtCursor(str: String) {
    commitText(str)
}

private fun parseComposeColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { fallback }
