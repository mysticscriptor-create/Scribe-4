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
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FloatingWindowOverlay
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.view.ScribeEditText
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.NoteListViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
// ── BasicTextField experiment imports ─────────────────────────────────────────
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.SolidColor
import com.primaloptima.scribe.ui.util.ScribeInputTransformation
import com.primaloptima.scribe.util.ThemeManager

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val scope = rememberCoroutineScope()

    val leftDrawerState  = rememberDrawerState(initialValue = DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Snap both drawers to Closed on first composition — prevents the 1-frame flash
    // visible during NavDisplay slide transitions (drawer Animatables initialise at
    // offset 0 before clamping to their off-screen closed positions).
    LaunchedEffect(Unit) {
        leftDrawerState.snapTo(DrawerValue.Closed)
        rightDrawerState.snapTo(DrawerValue.Closed)
    }

    // One-shot blurred captures for pre-API-31 frosted glass.
    // Captured once when each drawer starts opening; cleared when it closes.
    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    var leftOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rightOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Track whether we've already captured for the current open gesture
    var leftCaptured by remember { mutableStateOf(false) }
    var rightCaptured by remember { mutableStateOf(false) }
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Persistent blur bitmap for always-visible bars and FABs on Android 10.
    // Captured once after the background image has rendered (150ms delay),
    // and refreshed whenever the theme/background changes.
    var barBlurBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val editorTheme = LocalAppTheme.current
    val editorBgUri = editorTheme?.backgroundImageUri
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(editorBgUri) {
            barBlurBitmap = null
            if (!editorBgUri.isNullOrEmpty()) {
                kotlinx.coroutines.delay(150)
                val raw = BitmapBlur.captureOnly(view)
                barBlurBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            }
        }
    }


    // Trigger capture when drawers start sliding open (pre-API-31 only)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(leftDrawerState.currentValue, leftDrawerState.targetValue) {
            if (leftDrawerState.targetValue == DrawerValue.Open && !leftCaptured) {
                leftCaptured = true
                val raw = BitmapBlur.captureOnly(view)  // must stay on Main thread
                leftOneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (leftDrawerState.currentValue == DrawerValue.Closed &&
                       leftDrawerState.targetValue == DrawerValue.Closed) {
                leftCaptured = false
                leftOneShotBitmap = null
            }
        }
        LaunchedEffect(rightDrawerState.currentValue, rightDrawerState.targetValue) {
            if (rightDrawerState.targetValue == DrawerValue.Open && !rightCaptured) {
                rightCaptured = true
                val rawRight = BitmapBlur.captureOnly(view)  // must stay on Main thread
                rightOneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    rawRight?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (rightDrawerState.currentValue == DrawerValue.Closed &&
                       rightDrawerState.targetValue == DrawerValue.Closed) {
                rightCaptured = false
                rightOneShotBitmap = null
            }
        }

    }

    val activeNote by editorVm.activeNote.collectAsStateWithLifecycle()
    val wordCount by editorVm.wordCount.collectAsStateWithLifecycle()
    val charCount by editorVm.charCount.collectAsStateWithLifecycle()
    val outline by editorVm.outline.collectAsStateWithLifecycle()
    val zenMode by editorVm.zenMode.collectAsStateWithLifecycle()
    val activeTheme by editorVm.theme.collectAsStateWithLifecycle()
    val bgUri = activeTheme?.backgroundImageUri
    val bgMode = activeTheme?.bgMode ?: "color"
    val themeScope = activeTheme?.themeScope ?: "editor_only"
    val bgOpacity = activeTheme?.backgroundImageOpacity ?: 0.35f
    val blurIntensity = activeTheme?.blurIntensity ?: 0f
    val hasBgImage = !bgUri.isNullOrEmpty() && bgMode != "color"
    val isEditorOnlyBg = hasBgImage && themeScope == "editor_only"

    val currentBookNotes by bookVm.notes.collectAsStateWithLifecycle()
    val currentBookFolders by bookVm.folders.collectAsStateWithLifecycle()
    val worldEntries by bookVm.worldEntries.collectAsStateWithLifecycle()

    val allNotes by noteListVm.notes.collectAsStateWithLifecycle()
    val allFolders by noteListVm.folders.collectAsStateWithLifecycle()
    val shortcuts by shortcutsVm.shortcuts.collectAsStateWithLifecycle()

    val floatingWindows by editorVm.floatingWindows.collectAsStateWithLifecycle()

    val pinnedTopNotes by editorVm.pinnedTopNotes.collectAsStateWithLifecycle()
    val pinnedTopIndex by editorVm.pinnedTopIndex.collectAsStateWithLifecycle()
    val pinnedBottomNotes by editorVm.pinnedBottomNotes.collectAsStateWithLifecycle()
    val pinnedBottomIndex by editorVm.pinnedBottomIndex.collectAsStateWithLifecycle()

    var rightDrawerTab by remember { mutableIntStateOf(0) } // 0: Pinned, 1: Outline
    var leftPanelTab by remember { mutableIntStateOf(0) } // 0: Files, 1: World Sheet
    var leftDrawerMode by remember { mutableStateOf("Current") } // "Current" or "Books"
    var leftSearchQuery by remember { mutableStateOf("") }

    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var filePickerTargetSlot by remember { mutableStateOf<String?>(null) } // "top" or "bottom"

    // Clear dialog bitmap when all dialogs close.
    val anyDialogOpen = showRenameDialog || showCreateNoteDialog || filePickerTargetSlot != null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) {
            if (!anyDialogOpen) dialogOneShotBitmap = null
        }
    }

    // KEY FIX: capture BEFORE setting the show flag so FrostedDialog's first
    // composition already has a valid blur bitmap behind it.
    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val raw = BitmapBlur.captureOnly(view)
            dialogOneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
            }
        }
        openDialog()
    }

    // ── BasicTextField experiment ─────────────────────────────────────────────
    // TextFieldState is the single source of truth for editor content.
    // It is hoisted here so toolbar buttons and find/replace can read/write it.
    val textFieldState = rememberTextFieldState()

    // editorRef kept as null — only used by toolbar extension fns.
    // Those fns are replaced below by textFieldState equivalents.
    // Set to null explicitly so the compiler doesn't warn about unused var.
    val editorRef: ScribeEditText? = null

    // Bug 2 fix (preserved): rememberSaveable so loadedNoteId survives
    // recomposition when returning from History.
    var loadedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedTreeState = remember { mutableStateMapOf<String, Boolean>() }

    // Floating pill state
    var pillMode by remember { mutableIntStateOf(0) } // 0: words, 1: words+chars, 2: words+time
    var pillOffsetX by remember { mutableFloatStateOf(0f) }
    var pillOffsetY by remember { mutableFloatStateOf(0f) }

    var prevWordCount by remember { mutableIntStateOf(wordCount) }
    var deltaText by remember { mutableStateOf<String?>(null) }
    var isPositiveDelta by remember { mutableStateOf(true) }

    // Daily writing goal — sourced from ViewModel so it matches the user-defined goal
    val goalProgress by editorVm.goalProgress.collectAsStateWithLifecycle()
    var goalNotified by remember { mutableStateOf(false) }

    LaunchedEffect(wordCount) {
        val diff = wordCount - prevWordCount
        if (diff != 0) {
            deltaText = if (diff > 0) "+$diff" else "$diff"
            isPositiveDelta = diff > 0
            prevWordCount = wordCount
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
        if (!initialNoteId.isNullOrEmpty()) {
            editorVm.loadNote(initialNoteId)
        } else if (currentBookNotes.isNotEmpty()) {
            editorVm.loadNote(currentBookNotes.first().id)
        }
    }

    // ── Load note content into TextFieldState ─────────────────────────────────
    // Mirrors the AndroidView update lambda's loadedNoteId guard:
    // only load when the note actually changes or the state is empty.
    LaunchedEffect(activeNote?.id, activeNote?.content) {
        val note = activeNote ?: return@LaunchedEffect
        if (loadedNoteId != note.id ||
            (textFieldState.text.isEmpty() && note.content.isNotEmpty())) {
            loadedNoteId = note.id
            // setTextAndPlaceCursorAtEnd replaces content without triggering
            // the onValueChange observer (it's a programmatic edit, not user input),
            // so it won't re-fire onContentChanged — matching setContentSilently.
            textFieldState.setTextAndPlaceCursorAtEnd(note.content)
        }
    }

    // ── Observe TextFieldState for autosave / stats ───────────────────────────
    // snapshotFlow emits on every text change caused by user input.
    // Programmatic edits (setTextAndPlaceCursorAtEnd above) also emit,
    // but EditorViewModel.onContentChanged is idempotent when content hasn't changed.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { text ->
                // Only fire when a note is actually loaded to avoid spurious
                // autosave calls during initialisation.
                if (loadedNoteId != null) {
                    editorVm.onContentChanged(text)
                }
            }
    }

    // Auto-save snapshot when leaving note.
    // Capture the state reference in a val so onDispose can read it
    // without capturing the composable scope.
    val textFieldStateRef = textFieldState
    DisposableEffect(activeNote?.id) {
        onDispose {
            activeNote?.let { _ ->
                val currentText = textFieldStateRef.text.toString()
                editorVm.saveVersionSnapshotOnLeave(currentText)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "External Folder"
        noteListVm.connectExternalFolder(uri, name)
    }

    val swipeGestureModifier = Modifier.pointerInput(leftDrawerState, rightDrawerState) {
        var startX = 0f
        var totalX = 0f
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                startX = offset.x
                totalX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                val threshold = 36.dp.toPx()
                if (totalX > threshold && leftDrawerState.isClosed
                        && !leftDrawerState.isAnimationRunning && startX < size.width * 0.5f) {
                    change.consume()
                    scope.launch { leftDrawerState.open() }
                } else if (totalX < -threshold && rightDrawerState.isClosed
                        && !rightDrawerState.isAnimationRunning && startX > size.width * 0.5f) {
                    change.consume()
                    scope.launch { rightDrawerState.open() }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isEditorOnlyBg) {
            val editorHazeState = LocalHazeState.current
            AsyncImage(
                model = bgUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgMode == "blurred" && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && blurIntensity > 0f) {
                            Modifier.graphicsLayer {
                                val radiusPx = blurIntensity * density
                                if (radiusPx > 0f) {
                                    renderEffect = android.graphics.RenderEffect
                                        .createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
                                        .asComposeRenderEffect()
                                }
                            }
                        } else Modifier
                    )
            )
            val themeBgColor = parseComposeColor(activeTheme?.colors?.background ?: "#FAFAF7", Color(0xFFFAFAF7))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeBgColor.copy(alpha = bgOpacity))
            )
        }

        ModalNavigationDrawer(
            drawerState = leftDrawerState,
            gesturesEnabled = true,
            drawerContent = {
                CompositionLocalProvider(LocalOneShotBitmap provides leftOneShotBitmap) {
                ModalDrawerSheet(
                    drawerContainerColor = Color.Transparent,
                    modifier = Modifier
                        .width(320.dp)
                        .frostedPanel(LocalHazeState.current)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vault Explorer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = leftDrawerMode == "Current",
                                    onClick = { leftDrawerMode = "Current" },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text("Current", fontSize = 10.sp) }
                                SegmentedButton(
                                    selected = leftDrawerMode == "Books",
                                    onClick = { leftDrawerMode = "Books" },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text("Books", fontSize = 10.sp) }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (leftPanelTab == 0) {
                            // Files Tab
                            OutlinedTextField(
                                value = leftSearchQuery,
                                onValueChange = { leftSearchQuery = it },
                                placeholder = { Text("Search files & folders...") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (leftSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { leftSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val displayNotes = if (leftDrawerMode == "Current") currentBookNotes else allNotes
                            val displayFolders = if (leftDrawerMode == "Current") currentBookFolders else allFolders

                            val folderGrouped = remember(displayNotes, displayFolders) {
                                val map = mutableMapOf<String, MutableList<Note>>()
                                displayNotes.forEach { n ->
                                    val f = n.folderPath.ifBlank { "/" }
                                    map.getOrPut(f) { mutableListOf() }.add(n)
                                }
                                map
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                folderGrouped.forEach { (folderPath, notesInFolder) ->
                                    val isExpanded = expandedTreeState[folderPath] ?: true

                                    item(key = "folder_$folderPath") {
                                        var showFolderMenu by remember { mutableStateOf(false) }
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .combinedClickable(
                                                        onClick = { expandedTreeState[folderPath] = !isExpanded },
                                                        onLongClick = { showFolderMenu = true }
                                                    )
                                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (isExpanded) Icons.Default.KeyboardArrowDown
                                                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = folderPath.substringAfterLast('/'),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${notesInFolder.size}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            var showFolderPinSubMenu by remember { mutableStateOf(false) }
                                            DropdownMenu(
                                                expanded = showFolderMenu,
                                                onDismissRequest = {
                                                    showFolderMenu = false
                                                    showFolderPinSubMenu = false
                                                },
                                                containerColor = LocalSolidSurface.current
                                            ) {
                                                if (!showFolderPinSubMenu) {
                                                    DropdownMenuItem(
                                                        text = { Text("Pin") },
                                                        onClick = { showFolderPinSubMenu = true }
                                                    )
                                                } else {
                                                    DropdownMenuItem(
                                                        text = { Text("‹ Back") },
                                                        onClick = { showFolderPinSubMenu = false }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("Pin First Note to Top") },
                                                        onClick = {
                                                            showFolderMenu = false
                                                            showFolderPinSubMenu = false
                                                            notesInFolder.firstOrNull()?.let { editorVm.addPinnedTop(it.id) }
                                                            scope.launch { leftDrawerState.close() }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Pin First Note to Bottom") },
                                                        onClick = {
                                                            showFolderMenu = false
                                                            showFolderPinSubMenu = false
                                                            notesInFolder.firstOrNull()?.let { editorVm.addPinnedBottom(it.id) }
                                                            scope.launch { leftDrawerState.close() }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        items(notesInFolder, key = { "note_${it.id}" }) { note ->
                                            var showMenu by remember { mutableStateOf(false) }
                                            val isSelected = note.id == activeNote?.id

                                            Box(modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp)) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .combinedClickable(
                                                            onClick = {
                                                                editorVm.loadNote(note.id)
                                                                scope.launch { leftDrawerState.close() }
                                                            },
                                                            onLongClick = { showMenu = true }
                                                        ),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                Icons.Outlined.Description,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = note.name,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        val preview = remember(note.content) {
                                                            note.content.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" ")
                                                        }
                                                        if (preview.isNotBlank()) {
                                                            Text(
                                                                text = preview,
                                                                fontSize = 11.sp,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                var showPinSubMenu by remember { mutableStateOf(false) }
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = {
                                                        showMenu = false
                                                        showPinSubMenu = false
                                                    },
                                                    containerColor = LocalSolidSurface.current
                                                ) {
                                                    if (!showPinSubMenu) {
                                                        DropdownMenuItem(
                                                            text = { Text("Pin") },
                                                            onClick = { showPinSubMenu = true }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Open as Floating Window") },
                                                            onClick = {
                                                                showMenu = false
                                                                editorVm.openFloatingWindow(note.id)
                                                                scope.launch { leftDrawerState.close() }
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Delete") },
                                                            onClick = {
                                                                showMenu = false
                                                                bookVm.deleteNote(note.id)
                                                            }
                                                        )
                                                    } else {
                                                        DropdownMenuItem(
                                                            text = { Text("‹ Back") },
                                                            onClick = { showPinSubMenu = false }
                                                        )
                                                        HorizontalDivider()
                                                        DropdownMenuItem(
                                                            text = { Text("Pin to Top") },
                                                            onClick = {
                                                                showMenu = false
                                                                showPinSubMenu = false
                                                                editorVm.addPinnedTop(note.id)
                                                                scope.launch { leftDrawerState.close() }
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Pin to Bottom") },
                                                            onClick = {
                                                                showMenu = false
                                                                showPinSubMenu = false
                                                                editorVm.addPinnedBottom(note.id)
                                                                scope.launch { leftDrawerState.close() }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // World Sheet Tab
                            Text("World Building Entries", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (worldEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No world entries yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(worldEntries, key = { it.id }) { entry ->
                                        var showMenu by remember { mutableStateOf(false) }

                                        Box {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = {
                                                            // Load or show world entry
                                                            scope.launch { leftDrawerState.close() }
                                                        },
                                                        onLongClick = { showMenu = true }
                                                    ),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        if (entry.type == "character") Icons.Default.Person else Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text(
                                                            entry.summary.ifBlank { entry.type.uppercase() },
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }

                                            var showPinSubMenu by remember { mutableStateOf(false) }
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = {
                                                    showMenu = false
                                                    showPinSubMenu = false
                                                },
                                                containerColor = LocalSolidSurface.current
                                            ) {
                                                if (!showPinSubMenu) {
                                                    DropdownMenuItem(
                                                        text = { Text("Pin") },
                                                        onClick = { showPinSubMenu = true }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Open as Floating Window") },
                                                        onClick = {
                                                            showMenu = false
                                                            editorVm.openFloatingWindow(entry.id)
                                                            scope.launch { leftDrawerState.close() }
                                                        }
                                                    )
                                                } else {
                                                    DropdownMenuItem(
                                                        text = { Text("‹ Back") },
                                                        onClick = { showPinSubMenu = false }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("Pin to Top") },
                                                        onClick = {
                                                            showMenu = false
                                                            showPinSubMenu = false
                                                            editorVm.addPinnedTop(entry.id)
                                                            scope.launch { leftDrawerState.close() }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Pin to Bottom") },
                                                        onClick = {
                                                            showMenu = false
                                                            showPinSubMenu = false
                                                            editorVm.addPinnedBottom(entry.id)
                                                            scope.launch { leftDrawerState.close() }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Bottom Tab Bar (Files / World Sheet)
                        PrimaryTabRow(selectedTabIndex = leftPanelTab, modifier = Modifier.fillMaxWidth()) {
                            Tab(
                                selected = leftPanelTab == 0,
                                onClick = { leftPanelTab = 0 },
                                icon = { Icon(Icons.Outlined.Folder, contentDescription = "Files") },
                                text = { Text("Files", fontSize = 11.sp) }
                            )
                            Tab(
                                selected = leftPanelTab == 1,
                                onClick = { leftPanelTab = 1 },
                                icon = { Icon(Icons.Outlined.Public, contentDescription = "World Sheet") },
                                text = { Text("World Sheet", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                } // end CompositionLocalProvider(LocalOneShotBitmap provides leftOneShotBitmap)
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = rightDrawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        CompositionLocalProvider(LocalOneShotBitmap provides rightOneShotBitmap) {
                            ModalDrawerSheet(
                                drawerContainerColor = Color.Transparent,
                                modifier = Modifier
                                    .width(320.dp)
                                    .frostedPanel(LocalHazeState.current)
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                PrimaryTabRow(selectedTabIndex = rightDrawerTab) {
                                    Tab(
                                        selected = rightDrawerTab == 0,
                                        onClick = { rightDrawerTab = 0 },
                                        text = { Text("Pinned Notes", fontWeight = FontWeight.Bold) }
                                    )
                                    Tab(
                                        selected = rightDrawerTab == 1,
                                        onClick = { rightDrawerTab = 1 },
                                        text = { Text("Outline (${outline.size})", fontWeight = FontWeight.Bold) }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                if (rightDrawerTab == 0) {
                                    // Split Screen Pinned Notes View (Top & Bottom)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    ) {
                                        // Top Slot Half
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .frostedCard(LocalHazeState.current, RoundedCornerShape(12.dp),
                                                    applyFallbackBackground = true)
                                        ) {
                                            val currentTopId = pinnedTopNotes.getOrNull(pinnedTopIndex)
                                            val currentTopNote = remember(currentTopId, allNotes, worldEntries) {
                                                allNotes.firstOrNull { it.id == currentTopId }
                                                    ?: worldEntries.firstOrNull { it.id == currentTopId }?.let { w ->
                                                        Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
                                                    }
                                            }

                                            if (currentTopNote == null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } },
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.AddCircleOutline,
                                                        contentDescription = "Pick a note to pin",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        "Pick a note to pin",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            } else {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxWidth()
                                                            .verticalScroll(rememberScrollState())
                                                            .padding(12.dp)
                                                    ) {
                                                        Column {
                                                            Text(
                                                                currentTopNote.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                currentTopNote.content.ifBlank { "(Empty note content)" },
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                            CircleShape
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (pinnedTopNotes.size > 1) {
                                                        IconButton(onClick = { editorVm.prevPinnedTop() }, modifier = Modifier.size(24.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(onClick = { editorVm.nextPinnedTop() }, modifier = Modifier.size(24.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    IconButton(onClick = { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch Note", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { editorVm.loadNote(currentTopNote.id) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit in Main", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { editorVm.removePinnedTop(currentTopNote.id) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                                        // Bottom Slot Half
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .frostedCard(LocalHazeState.current, RoundedCornerShape(12.dp),
                                                    applyFallbackBackground = true)
                                        ) {
                                            val currentBottomId = pinnedBottomNotes.getOrNull(pinnedBottomIndex)
                                            val currentBottomNote = remember(currentBottomId, allNotes, worldEntries) {
                                                allNotes.firstOrNull { it.id == currentBottomId }
                                                    ?: worldEntries.firstOrNull { it.id == currentBottomId }?.let { w ->
                                                        Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
                                                    }
                                            }

                                            if (currentBottomNote == null) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } },
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.AddCircleOutline,
                                                        contentDescription = "Pick a note to pin",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        "Pick a note to pin",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            } else {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxWidth()
                                                            .verticalScroll(rememberScrollState())
                                                            .padding(12.dp)
                                                    ) {
                                                        Column {
                                                            Text(
                                                                currentBottomNote.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                currentBottomNote.content.ifBlank { "(Empty note content)" },
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                            CircleShape
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (pinnedBottomNotes.size > 1) {
                                                        IconButton(onClick = { editorVm.prevPinnedBottom() }, modifier = Modifier.size(24.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(onClick = { editorVm.nextPinnedBottom() }, modifier = Modifier.size(24.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    IconButton(onClick = { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch Note", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { editorVm.loadNote(currentBottomNote.id) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit in Main", modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { editorVm.removePinnedBottom(currentBottomNote.id) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Outline Tab
                                    if (outline.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No headings yet. Use # Heading to structure your writing.",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            contentPadding = PaddingValues(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(outline) { entry ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val pos = textFieldState.text.indexOf(entry.text)
                                                            if (pos >= 0) {
                                                                textFieldState.edit {
                                                                    selection = androidx.compose.ui.text.TextRange(pos)
                                                                }
                                                            }
                                                            scope.launch { rightDrawerState.close() }
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.primary
                                                            ) {
                                                                Text(
                                                                    "H${entry.level}",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = entry.text,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } // end CompositionLocalProvider(LocalOneShotBitmap provides rightOneShotBitmap)
                    }
                ) {
                    val hazeState = LocalHazeState.current

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            modifier = Modifier.then(swipeGestureModifier),
                            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
                            topBar = {
                                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                if (!zenMode) {
                                    Column {
                                        TopAppBar(
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.primary,
                                                navigationIconContentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.frostedBar(hazeState),
                                            navigationIcon = {
                                                IconButton(onClick = { scope.launch { leftDrawerState.open() } }) {
                                                    Icon(Icons.Default.Menu, contentDescription = "Vault Explorer")
                                                }
                                            },
                                            title = {
                                                val (titleColor, titleModifier) = rememberAdaptiveTextColor(
                                                    fallback = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    activeNote?.name ?: "Scribe Editor",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = titleColor,
                                                    modifier = titleModifier.clickable {
                                                        if (activeNote != null) scope.launch { captureForDialog { showRenameDialog = true } }
                                                    }
                                                )
                                            },
                                            actions = {
                                                IconButton(onClick = { scope.launch { rightDrawerState.open() } }) {
                                                    Icon(Icons.Default.Dock, contentDescription = "Outline & Pinned Notes")
                                                }
                                                IconButton(onClick = { showFindBar = !showFindBar }) {
                                                    Icon(Icons.Default.Search, contentDescription = "Find")
                                                }
                                                IconButton(onClick = {
                                                    val text = textFieldState.text.toString()
                                                    editorVm.saveManualSnapshot(text)
                                                    Toast.makeText(context, "Checkpoint saved", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Checkpoint")
                                                }

                                                var showMenu by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showMenu = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                                }
                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false },
                                                    containerColor = LocalSolidSurface.current
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Open as Floating Reference Window") },
                                                        onClick = {
                                                            showMenu = false
                                                            activeNote?.let { n -> editorVm.openFloatingWindow(n.id) }
                                                        }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("Export as TXT") },
                                                        onClick = {
                                                            showMenu = false
                                                            activeNote?.let { n -> ExportHelper.shareNote(context, n, "txt") }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Export as Markdown") },
                                                        onClick = {
                                                            showMenu = false
                                                            activeNote?.let { n -> ExportHelper.shareNote(context, n, "md") }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Export as HTML") },
                                                        onClick = {
                                                            showMenu = false
                                                            activeNote?.let { n -> ExportHelper.shareNote(context, n, "html") }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Export as PDF") },
                                                        onClick = {
                                                            showMenu = false
                                                            activeNote?.let { n -> ExportHelper.shareNote(context, n, "pdf") }
                                                        }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("Version History") },
                                                        onClick = {
                                                            showMenu = false
                                                            // Flush unsaved content to DB before navigating away.
                                                            // Navigation Compose destroys and recreates the Editor
                                                            // composable on return from the back stack, so setText()
                                                            // will be called with note.content read from the DB.
                                                            // Without this flush, anything typed within the 500ms
                                                            // autosave debounce window is silently discarded.
                                                            editorVm.flushContent(textFieldState.text.toString())
                                                            onOpenHistory()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Shortcuts") },
                                                        onClick = {
                                                            showMenu = false
                                                            onOpenShortcuts()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("User Guide") },
                                                        onClick = {
                                                            showMenu = false
                                                            onOpenGuide()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Settings") },
                                                        onClick = {
                                                            showMenu = false
                                                            onOpenSettings()
                                                        }
                                                    )
                                                }
                                            }
                                        )

                                        // Word Goal Progress Bar (3dp height)
                                        LinearProgressIndicator(
                                            progress = { goalProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                                } // end CompositionLocalProvider(barBlurBitmap for topBar)
                            },
                            bottomBar = {
                                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                val isKeyboardVisible = WindowInsets.isImeVisible
                                AnimatedVisibility(
                                    visible = isKeyboardVisible,
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .frostedBar(hazeState)
                                            // imePadding() lifts the toolbar above the keyboard.
                                            // Must come before horizontalScroll so the scroll
                                            // area itself isn't shrunk by the IME inset.
                                            .imePadding()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                            FormatButton(label = "B") { textFieldState.applyFormat("**", "**") }
                                            FormatButton(label = "I") { textFieldState.applyFormat("*", "*") }
                                            FormatButton(label = "H1") { textFieldState.applyLinePrefix("# ") }
                                            FormatButton(label = "H2") { textFieldState.applyLinePrefix("## ") }
                                            FormatButton(label = "Quote") { textFieldState.applyLinePrefix("> ") }
                                            FormatButton(label = "List") { textFieldState.applyLinePrefix("- ") }
                                            FormatButton(label = "Code") { textFieldState.applyFormat("`", "`") }

                                            shortcuts.forEach { shortcut ->
                                                FormatButton(label = shortcut.label) {
                                                    when (shortcut.kind) {
                                                        "wrap" -> textFieldState.applyFormat(shortcut.payload, shortcut.closing ?: shortcut.payload)
                                                        "pair" -> textFieldState.applyFormat(shortcut.payload, shortcut.closing ?: "")
                                                        else -> textFieldState.insertAtCursor(shortcut.payload)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                } // end CompositionLocalProvider(barBlurBitmap for bottomBar)
                        ) { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    if (showFindBar) {
                                        Surface(
                                            shadowElevation = 4.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = findQuery,
                                                    onValueChange = { findQuery = it },
                                                    placeholder = { Text("Find") },
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                OutlinedTextField(
                                                    value = replaceQuery,
                                                    onValueChange = { replaceQuery = it },
                                                    placeholder = { Text("Replace") },
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                )
                                                IconButton(onClick = {
                                                    if (findQuery.isNotEmpty()) {
                                                        // Replace all occurrences directly in TextFieldState.
                                                        // TextFieldState.edit{} is undoable via its built-in undo stack.
                                                        val current = textFieldState.text.toString()
                                                        val replaced = current.replace(
                                                            findQuery,
                                                            replaceQuery,
                                                            ignoreCase = true
                                                        )
                                                        if (replaced != current) {
                                                            textFieldState.setTextAndPlaceCursorAtEnd(replaced)
                                                            editorVm.onContentChanged(replaced)
                                                        }
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

                                    val currentThemeBg = MaterialTheme.colorScheme.background
                                    val currentThemeTextColor = MaterialTheme.colorScheme.onBackground
                                    val currentThemePrimary = MaterialTheme.colorScheme.primary
                                    val hasBgImage = !activeTheme?.backgroundImageUri.isNullOrEmpty()

                                    Box(modifier = Modifier.fillMaxSize()) {
                                    // Gesture wrapper: AndroidView is a child so PointerEventPass.Initial
                                    // fires here BEFORE the editor sees the touch. Double-taps are
                                    // consumed (blocking the editor); single taps pass through normally.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                                                val doubleTapMinTime = viewConfiguration.doubleTapMinTimeMillis
                                                awaitPointerEventScope {
                                                    var lastTapTime = 0L
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        val down = event.changes.firstOrNull() ?: continue
                                                        if (down.pressed && !down.previousPressed) {
                                                            val now = System.currentTimeMillis()
                                                            val diff = now - lastTapTime
                                                            if (diff in doubleTapMinTime..doubleTapTimeout) {
                                                                // Double tap — consume and toggle zen
                                                                down.consume()
                                                                editorVm.toggleZen()
                                                                lastTapTime = 0L
                                                            } else {
                                                                // Single tap — don't consume, let it reach the editor
                                                                lastTapTime = now
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                    // ── BasicTextField (experiment) ───────────────────────────────
                                    // Replaces AndroidView(ScribeEditText). Content is driven by
                                    // textFieldState; loading and ViewModel wiring is in the
                                    // LaunchedEffects above.
                                    val editorFontSize = (activeTheme?.fontSize ?: 18).sp
                                    val editorTypeface = activeTheme?.fontFamily?.let { family ->
                                        ThemeManager.resolveTypeface(context, family)
                                    }
                                    val editorFontFamily = editorTypeface?.let { tf ->
                                        FontFamily(androidx.compose.ui.text.font.Typeface(tf))
                                    } ?: FontFamily.Default

                                    val editorTextStyle = TextStyle(
                                        color = currentThemeTextColor,
                                        fontSize = editorFontSize,
                                        fontFamily = editorFontFamily,
                                        lineHeight = editorFontSize * 1.6f
                                    )

                                    BasicTextField(
                                        state = textFieldState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (hasBgImage) Color.Transparent
                                                else currentThemeBg
                                            )
                                            .padding(horizontal = 20.dp, vertical = 20.dp),
                                        textStyle = editorTextStyle,
                                        inputTransformation = ScribeInputTransformation,
                                        cursorBrush = SolidColor(currentThemePrimary),
                                        scrollState = rememberScrollState()
                                    )
                                    } // end gesture-detection Box

                                // Floating Word Count Pill + Zen FAB — always visible,
                                // so use barBlurBitmap (not dialogOneShotBitmap).
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
                                            enter = fadeIn() + slideInVertically { -20 },
                                            exit = fadeOut() + slideOutVertically { -20 }
                                        ) {
                                            Text(
                                                text = deltaText ?: "",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPositiveDelta) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = frostedContainerColor(
                                                fallback = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .frostedFab(LocalHazeState.current)
                                                .pointerInput(Unit) {
                                                    detectDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        pillOffsetX += dragAmount.x
                                                        pillOffsetY += dragAmount.y
                                                    }
                                                }
                                                .clickable {
                                                    pillMode = (pillMode + 1) % 3
                                                }
                                        ) {
                                            AnimatedContent(
                                                targetState = pillMode,
                                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                                            ) { mode ->
                                                val displayText = when (mode) {
                                                    1 -> "$wordCount words · $charCount chars"
                                                    2 -> "$wordCount words · ${maxOf(1, wordCount / 200)}m"
                                                    else -> "$wordCount words"
                                                }
                                                Text(
                                                    text = displayText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (zenMode) {
                                    ScribeSingleFab(
                                        icon = Icons.Default.FullscreenExit,
                                        contentDescription = "Exit Zen",
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp),
                                        onClick = { editorVm.setZen(false) }
                                    )
                                }
                                } // end CompositionLocalProvider(barBlurBitmap for FABs)
                            } // end editor Box
                        }
                    }
                }
            }
        }

        // Floating Windows Overlay
        val mappedNotes = remember(currentBookNotes, worldEntries) {
            val list = currentBookNotes.toMutableList()
            worldEntries.forEach { w ->
                if (list.none { it.id == w.id }) {
                    list.add(Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}"))
                }
            }
            list
        }

        FloatingWindowOverlay(
            floatingWindows = floatingWindows,
            notes = mappedNotes,
            onCloseWindow = { id -> editorVm.closeFloatingWindow(id) },
            onToggleCollapse = { id -> editorVm.toggleCollapseFloatingWindow(id) },
            onMoveWindow = { id, x, y -> editorVm.moveFloatingWindow(id, x, y) }
        )

        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
        if (showRenameDialog && activeNote != null) {
            val noteToRename = activeNote
            var renameText by remember { mutableStateOf(noteToRename?.name ?: "") }
            FrostedDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Note") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val t = renameText.trim()
                            if (t.isNotEmpty() && noteToRename != null) {
                                bookVm.renameNote(noteToRename.id, t)
                            }
                            showRenameDialog = false
                        }
                    ) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showCreateNoteDialog) {
            var noteTitle by remember { mutableStateOf("") }
            FrostedDialog(
                onDismissRequest = { showCreateNoteDialog = false },
                title = { Text("New Note") },
                text = {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val t = noteTitle.trim()
                            if (t.isNotEmpty()) {
                                bookVm.createNote(t) { id ->
                                    showCreateNoteDialog = false
                                    editorVm.loadNote(id)
                                }
                            }
                        }
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateNoteDialog = false }) { Text("Cancel") }
                }
            )
        }

        filePickerTargetSlot?.let { targetSlot ->
            FileExplorerOverlayDialog(
                allNotes = if (leftDrawerMode == "Current") currentBookNotes else allNotes,
                allFolders = if (leftDrawerMode == "Current") currentBookFolders else allFolders,
                onSelectNote = { note ->
                    if (targetSlot == "top") {
                        editorVm.addPinnedTop(note.id)
                    } else {
                        editorVm.addPinnedBottom(note.id)
                    }
                    filePickerTargetSlot = null
                },
                onDismiss = { filePickerTargetSlot = null }
            )
        }
        } // end CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap)
    }
        } // end ModalNavigationDrawer
    } // end outer Box
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerOverlayDialog(
    allNotes: List<Note>,
    allFolders: List<Folder>,
    onSelectNote: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }
    val folderGrouped = remember(allNotes, allFolders) {
        val map = mutableMapOf<String, MutableList<Note>>()
        allNotes.forEach { n ->
            val f = n.folderPath.ifBlank { "/" }
            map.getOrPut(f) { mutableListOf() }.add(n)
        }
        map
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a note to pin", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                folderGrouped.forEach { (folderPath, notesInFolder) ->
                    val isExpanded = expandedPaths[folderPath] ?: true
                    item(key = "f_$folderPath") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expandedPaths[folderPath] = !isExpanded }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                folderPath.substringAfterLast('/'),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isExpanded) {
                        items(notesInFolder, key = { "n_${it.id}" }) { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, top = 2.dp, bottom = 2.dp)
                                    .clickable { onSelectNote(note) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(note.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    val preview = remember(note.content) {
                                        note.content.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" ")
                                    }
                                    if (preview.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            preview,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FormatButton(
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── TextFieldState toolbar helpers ────────────────────────────────────────────
// Replacements for the old ScribeEditText extension functions.
// TextFieldState.edit{} is automatically recorded in the built-in undo stack.

private fun TextFieldState.applyFormat(prefix: String, suffix: String) {
    edit {
        val start = selection.start
        val end   = selection.end
        if (start >= 0 && end >= start) {
            val selected  = toString().substring(start, end)
            val formatted = "$prefix$selected$suffix"
            replace(start, end, formatted)
            selection = androidx.compose.ui.text.TextRange(
                start + prefix.length,
                start + prefix.length + selected.length
            )
        }
    }
}

private fun TextFieldState.applyLinePrefix(prefix: String) {
    edit {
        val pos = selection.start
        if (pos >= 0) {
            insert(pos, prefix)
        }
    }
}

private fun TextFieldState.insertAtCursor(str: String) {
    edit {
        val pos = selection.start
        if (pos >= 0) {
            insert(pos, str)
        }
    }
}

private fun parseComposeColor(hex: String, fallback: Color = Color.Transparent): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
