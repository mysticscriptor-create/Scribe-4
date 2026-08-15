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
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitFirstDown
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
import io.github.rosemoe.sora.text.Content
import com.primaloptima.scribe.util.ScribeProseLanguage
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

    // ── Sora Editor state ─────────────────────────────────────────────────────
    // soraEditorRef holds the live CodeEditor view so toolbar buttons and
    // find/replace can call into it without recomposition.
    var soraEditorRef by remember { mutableStateOf<CodeEditor?>(null) }

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

    // ── Load note content into Sora CodeEditor ────────────────────────────────
    // Mirrors the old AndroidView update lambda's loadedNoteId guard:
    // only setText when the note actually changes or the editor is empty.
    // soraEditorRef may be null on first composition (before AndroidView factory
    // runs), so this also re-fires when soraEditorRef becomes non-null.
    LaunchedEffect(activeNote?.id, activeNote?.content, soraEditorRef) {
        val note = activeNote ?: return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        if (loadedNoteId != note.id ||
            (editor.text.length == 0 && note.content.isNotEmpty())) {
            loadedNoteId = note.id
            // setText() is a programmatic replacement — it does NOT fire the
            // ContentChangeEvent subscription, so it won't loop back into
            // onContentChanged. Sora handles this correctly out of the box.
            editor.setText(note.content)
        }
    }

    // Auto-save snapshot when leaving note.
    // soraEditorRef is captured in a local val so onDispose can read it
    // safely without holding a reference to the composable scope.
    val soraEditorForDispose = soraEditorRef
    DisposableEffect(activeNote?.id) {
        onDispose {
            activeNote?.let { _ ->
                val currentText = soraEditorForDispose?.text?.toString() ?: ""
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

    // ── Drawer back-press handlers ────────────────────────────────────────────
    // Must be declared outside (and before) the Box so they sit higher in the
    // composition than any BackHandler inside the drawer content (e.g. FAB menu).
    // Using an `if` block rather than `enabled` keeps the handler fully out of
    // the composition when not needed, avoiding stale-callback priority issues.
    if (leftDrawerState.isOpen) {
        BackHandler { scope.launch { leftDrawerState.close() } }
    }
    if (rightDrawerState.isOpen) {
        BackHandler { scope.launch { rightDrawerState.close() } }
    }

    // ── Gesture Router: double-tap zen + edge drawer open ────────────────────
    // gesturesEnabled=false on both drawers because ModalNavigationDrawer with
    // gesturesEnabled=true installs a full-screen touch interceptor that steals
    // every gesture from Sora (scroll, selection, cursor). Instead we handle
    // only two things here and pass everything else through untouched:
    //   1. Edge swipe (≤20dp from side, horizontal) → open the drawer.
    //   2. Double-tap → zen toggle (consuming the DOWN before Sora's word-select).
    // Close is handled by: scrim tap (built-in) + BackHandler above.
    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(leftDrawerState, rightDrawerState) {
            val edgeZonePx       = 20.dp.toPx()
            val slopPx           = 18.dp.toPx()
            val tapSlopPx        = 16f
            val drawerTriggerPx  = 72.dp.toPx()
            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
            val doubleTapMinTime = viewConfiguration.doubleTapMinTimeMillis

            data class PendingTap(val x: Float, val y: Float, val time: Long)
            var pendingTap: PendingTap? = null

            awaitPointerEventScope {
                while (true) {
                    val down      = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val touchTime = System.currentTimeMillis()
                    val startX    = down.position.x
                    val startY    = down.position.y

                    // Expire stale pending tap
                    pendingTap?.let { if (touchTime - it.time > doubleTapTimeout) pendingTap = null }

                    // ── Double-tap? ────────────────────────────────────────
                    val isSecondTap = pendingTap?.let { first ->
                        val dt  = touchTime - first.time
                        val dxt = startX - first.x; val dyt = startY - first.y
                        dt >= doubleTapMinTime && dt <= doubleTapTimeout &&
                                kotlin.math.sqrt(dxt * dxt + dyt * dyt) < tapSlopPx
                    } ?: false

                    if (isSecondTap) {
                        pendingTap = null
                        down.consume() // block Sora's word-select
                        var becameDrag = false
                        while (true) {
                            val event  = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!becameDrag) { change.consume(); editorVm.toggleZen() }
                                break
                            }
                            val cdx = change.position.x - startX
                            val cdy = change.position.y - startY
                            if (kotlin.math.sqrt(cdx * cdx + cdy * cdy) > tapSlopPx) becameDrag = true
                        }
                        continue
                    }

                    // Record first tap — do NOT drain, everything passes through
                    pendingTap = PendingTap(startX, startY, touchTime)

                    // ── Edge touch? Watch for horizontal swipe to open drawer ─
                    val isLeftEdge  = startX < edgeZonePx
                    val isRightEdge = startX > size.width - edgeZonePx
                    if (!isLeftEdge && !isRightEdge) continue // not edge — release immediately

                    var hasExitedSlop = false
                    var drawerFired   = false
                    while (true) {
                        val event  = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dx   = change.position.x - startX
                        val dy   = change.position.y - startY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                        if (!hasExitedSlop && dist > slopPx) {
                            hasExitedSlop = true
                            val adx = if (dx < 0f) -dx else dx
                            val ady = if (dy < 0f) -dy else dy
                            // Vertical or ambiguous → abandon, let Sora/scroll own it
                            if (ady >= adx * 1.3f) break
                            // Diagonal ambiguous (neither clearly horiz) → abandon
                            if (adx <= ady * 1.3f) break
                        }

                        if (hasExitedSlop) {
                            val towardCenter = (isLeftEdge && dx > 0f) || (isRightEdge && dx < 0f)
                            if (!towardCenter) break // wrong direction — don't open
                            val absDx = if (dx < 0f) -dx else dx
                            change.consume()
                            if (!drawerFired && absDx > drawerTriggerPx) {
                                drawerFired = true
                                scope.launch {
                                    if (isLeftEdge) leftDrawerState.open()
                                    else rightDrawerState.open()
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
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
            gesturesEnabled = false,
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
                    gesturesEnabled = false,
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
                                                    // Note title
                                                    Text(
                                                        currentTopNote.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                                                    )
                                                    // Read-only Sora viewer
                                                    AndroidView(
                                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                                        factory = { ctx ->
                                                            CodeEditor(ctx).apply {
                                                                isEditable           = false
                                                                isLineNumberEnabled  = false
                                                                isHighlightCurrentLine = false
                                                                isWordwrap           = true
                                                                setText(currentTopNote.content.ifBlank { "(Empty note content)" })
                                                                activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                                                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                                            }
                                                        },
                                                        update = { editor ->
                                                            val incoming = currentTopNote.content.ifBlank { "(Empty note content)" }
                                                            if (editor.text.toString() != incoming) editor.setText(incoming)
                                                            activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                                                        }
                                                    )
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
                                                    // Note title
                                                    Text(
                                                        currentBottomNote.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                                                    )
                                                    // Read-only Sora viewer
                                                    AndroidView(
                                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                                        factory = { ctx ->
                                                            CodeEditor(ctx).apply {
                                                                isEditable           = false
                                                                isLineNumberEnabled  = false
                                                                isHighlightCurrentLine = false
                                                                isWordwrap           = true
                                                                setText(currentBottomNote.content.ifBlank { "(Empty note content)" })
                                                                activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                                                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                                            }
                                                        },
                                                        update = { editor ->
                                                            val incoming = currentBottomNote.content.ifBlank { "(Empty note content)" }
                                                            if (editor.text.toString() != incoming) editor.setText(incoming)
                                                            activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                                                        }
                                                    )
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
                                                            soraEditorRef?.let { editor ->
                                                                val fullText = editor.text.toString()
                                                                val charPos  = fullText.indexOf(entry.text)
                                                                if (charPos >= 0) {
                                                                    // Convert flat char offset → line/column for Sora.
                                                                    val line = editor.text.indexer.getCharLine(charPos)
                                                                    val col  = editor.text.indexer.getCharColumn(charPos)
                                                                    editor.cursor.set(line, col)
                                                                    editor.ensurePositionVisible(line, col)
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
                            modifier = Modifier,
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
                                                    val text = soraEditorRef?.text?.toString() ?: ""
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
                                                            editorVm.flushContent(soraEditorRef?.text?.toString() ?: "")
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
                                            // All toolbar buttons come from DefaultShortcuts / user shortcuts.
                                            // Do not add hardcoded FormatButtons here — that duplicates
                                            // entries already present in DefaultShortcuts.all.
                                            shortcuts.forEach { shortcut ->
                                                FormatButton(label = shortcut.label) {
                                                    when (shortcut.kind) {
                                                        "wrap" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: shortcut.payload)
                                                        "pair" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: "")
                                                        else -> soraEditorRef?.insertAtCursor(shortcut.payload)
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
                                                // Previous match
                                                IconButton(onClick = {
                                                    soraEditorRef?.searcher?.gotoPrevious()
                                                }) {
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous match")
                                                }
                                                // Next match
                                                IconButton(onClick = {
                                                    soraEditorRef?.searcher?.gotoNext()
                                                }) {
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next match")
                                                }
                                                // Replace all
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

                                    // ── Wire find query into Sora searcher ───────────────────
                                    // Runs whenever findQuery changes or the bar is toggled.
                                    // When the bar closes we stop the search to clear highlights.
                                    LaunchedEffect(findQuery, showFindBar) {
                                        val editor = soraEditorRef ?: return@LaunchedEffect
                                        if (showFindBar && findQuery.isNotEmpty()) {
                                            editor.searcher.search(
                                                findQuery,
                                                EditorSearcher.SearchOptions(true, false)
                                            )
                                        } else {
                                            editor.searcher.stopSearch()
                                        }
                                    }

                                    val currentThemeBg = MaterialTheme.colorScheme.background
                                    val currentThemeTextColor = MaterialTheme.colorScheme.onBackground
                                    val currentThemePrimary = MaterialTheme.colorScheme.primary
                                    val hasBgImage = !activeTheme?.backgroundImageUri.isNullOrEmpty()

                                    Box(modifier = Modifier.fillMaxSize()) {
                                    // ── Sora CodeEditor ───────────────────────────────────────
                                    // High-performance View-based editor. Content loading and
                                    // ViewModel wiring live in the LaunchedEffects above.
                                    // The factory runs once; the update lambda re-applies
                                    // theme values whenever they change in Compose state.
                                    val editorTextSizeSp = (activeTheme?.fontSize ?: 18).toFloat()
                                    val editorTypeface   = activeTheme?.fontFamily?.let { family ->
                                        ThemeManager.resolveTypeface(context, family)
                                    }
                                    val bgArgb   = if (hasBgImage) android.graphics.Color.TRANSPARENT
                                                   else currentThemeBg.toArgb()
                                    val textArgb = currentThemeTextColor.toArgb()
                                    val primaryArgb = currentThemePrimary.toArgb()

                                    AndroidView(
                                        factory = { ctx ->
                                            CodeEditor(ctx).apply {
                                                // ── Writer-mode setup ──────────────────────────
                                                // Disable all code-editor chrome — this is a prose editor.
                                                isLineNumberEnabled      = false
                                                isHighlightCurrentLine   = false
                                                isWordwrap               = true
                                                // ScribeProseLanguage: prose auto-pairs only, zero syntax overhead.
                                                // A new instance per editor is required by Sora's Language contract.
                                                setEditorLanguage(ScribeProseLanguage())

                                                // ── Content change → ViewModel ─────────────────
                                                // subscribeEvent fires on every user edit.
                                                // Programmatic setText() does NOT fire this.
                                                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                                    if (loadedNoteId != null) {
                                                        editorVm.onContentChanged(text.toString())
                                                    }
                                                }

                                                // ── Smart Enter ────────────────────────────────
                                                // If the cursor sits immediately before a closing
                                                // character, Enter moves past it instead of inserting
                                                // a newline. Completes the auto-pair experience.
                                                subscribeEvent(EditorKeyEvent::class.java) { event, _ ->
                                                    if (event.action != android.view.KeyEvent.ACTION_DOWN) return@subscribeEvent
                                                    if (event.keyCode != android.view.KeyEvent.KEYCODE_ENTER) return@subscribeEvent
                                                    val cur = this.cursor
                                                    if (cur.isSelected) return@subscribeEvent
                                                    val line = this.text.getLine(cur.leftLine)
                                                    val col  = cur.leftColumn
                                                    val closeChars = setOf(
                                                        ')', ']', '}', '`', '"', '\'',
                                                        '\u201D', '\u2019', '\u00BB'
                                                    )
                                                    if (col < line.length && line[col] in closeChars) {
                                                        setSelection(cur.leftLine, col + 1)
                                                        event.intercept()
                                                    }
                                                }
                                            }.also { soraEditorRef = it }
                                        },
                                        update = { editor ->
                                            // ── Apply theme ────────────────────────────────────
                                            editor.setTextSize(editorTextSizeSp)
                                            editorTypeface?.let { editor.typefaceText = it }

                                            // Background — transparent when a bg image is active.
                                            editor.setBackgroundColor(bgArgb)

                                            // Assign a fresh ScribeColorScheme on every theme change.
                                            // This replaces inline mutation and correctly sets all
                                            // tokens including SELECTION_INSERT (cursor bar colour).
                                            activeTheme?.let { theme ->
                                                val scheme = ScribeColorScheme(theme)
                                                // Override background separately: transparent when a
                                                // background image is active, themed colour otherwise.
                                                scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,       bgArgb)
                                                scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgArgb)
                                                scheme.setColor(EditorColorScheme.LINE_NUMBER,            bgArgb)
                                                editor.colorScheme = scheme

                                                // ── Text action popup styling ──────────────────
                                                // Match the ScribeFab border language: solid surface
                                                // fill + hairline vertical-gradient accent stroke
                                                // (accent top → transparent bottom, 0.7dp wide).
                                                // GradientDrawable can't stroke with a gradient, so
                                                // we layer a semi-transparent gradient rect on top of
                                                // the fill — same visual as Brush.verticalGradient.
                                                val density = context.resources.displayMetrics.density
                                                val cornerPx = 24f * density
                                                val accentArgb = android.graphics.Color.parseColor(theme.colors.accent)
                                                val surfaceArgb = android.graphics.Color.parseColor(theme.colors.surface)

                                                // Layer 0 — solid surface fill
                                                val fillDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                    setColor(surfaceArgb)
                                                    cornerRadius = cornerPx
                                                }

                                                // Layer 1 — gradient overlay: accent@28% top → transparent bottom
                                                // alpha 0.28 * 255 = ~71
                                                val strokeOverlay = android.graphics.drawable.GradientDrawable(
                                                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                                                    intArrayOf(
                                                        android.graphics.Color.argb(
                                                            71,
                                                            android.graphics.Color.red(accentArgb),
                                                            android.graphics.Color.green(accentArgb),
                                                            android.graphics.Color.blue(accentArgb)
                                                        ),
                                                        android.graphics.Color.TRANSPARENT
                                                    )
                                                ).apply {
                                                    cornerRadius = cornerPx
                                                }

                                                val popupBackground = android.graphics.drawable.LayerDrawable(
                                                    arrayOf(fillDrawable, strokeOverlay)
                                                )

                                                // EditorTextActionWindow wraps a PopupWindow internally.
                                                // Walk the class hierarchy looking for any field that holds
                                                // a PopupWindow instance — field name varies by Sora version.
                                                try {
                                                    val actionWindow = editor.getComponent(
                                                        io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
                                                    )
                                                    var popup: android.widget.PopupWindow? = null
                                                    var cls: Class<*>? = actionWindow.javaClass
                                                    outer@ while (cls != null && cls != Any::class.java) {
                                                        for (field in cls.declaredFields) {
                                                            if (android.widget.PopupWindow::class.java
                                                                    .isAssignableFrom(field.type)) {
                                                                field.isAccessible = true
                                                                popup = field.get(actionWindow)
                                                                    as? android.widget.PopupWindow
                                                                break@outer
                                                            }
                                                        }
                                                        cls = cls.superclass
                                                    }
                                                    popup?.setBackgroundDrawable(popupBackground)
                                                } catch (_: Exception) {
                                                    // Reflection failed — skip styling, no crash
                                                }
                                            }
                                        },
                                        // Double-tap zen toggle is now handled by the
                                        // unified gesture router on the outer Box.
                                        // No pointerInput needed here — keeps Sora's
                                        // own scroll and selection gestures undisturbed.
                                        modifier = Modifier
                                            .fillMaxSize()
                                    )

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
            activeTheme = activeTheme,
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

// ── CodeEditor toolbar helpers ────────────────────────────────────────────────
// These call into Sora's cursor/text API, which records every edit in Sora's
// built-in UndoManager so undo/redo works correctly.

private fun CodeEditor.applyFormat(prefix: String, suffix: String) {
    val cur = cursor
    if (cur.isSelected) {
        // Wrap selected text. Convert line/col positions to absolute char
        // indices via the Content indexer, then use the 2-arg subSequence.
        val indexer = text.indexer
        val startIdx = indexer.getCharIndex(cur.leftLine,  cur.leftColumn)
        val endIdx   = indexer.getCharIndex(cur.rightLine, cur.rightColumn)
        val selected = text.subSequence(startIdx, endIdx).toString()
        // Replace the selection with the wrapped version in one operation
        // so undo restores the original selection.
        text.replace(
            cur.leftLine,  cur.leftColumn,
            cur.rightLine, cur.rightColumn,
            "$prefix$selected$suffix"
        )
    } else {
        // No selection — insert prefix+suffix and place cursor between them.
        // Use text.insert directly so we control cursor placement precisely
        // without going through commitText (which would trigger Sora's own
        // auto-pair and insert a second closing character).
        val line = cur.leftLine
        val col  = cur.leftColumn
        text.insert(line, col, "$prefix$suffix")
        this.cursor.set(line, col + prefix.length)
    }
}

private fun CodeEditor.applyLinePrefix(prefix: String) {
    // Insert prefix at the start of the current line.
    val line = cursor.leftLine
    text.insert(line, 0, prefix)
    // Keep cursor at the same visual position, shifted right by prefix length.
    val newCol = cursor.leftColumn + prefix.length
    cursor.set(line, newCol)
}

private fun CodeEditor.insertAtCursor(str: String) {
    commitText(str)
}

private fun parseComposeColor(hex: String, fallback: Color = Color.Transparent): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
