package com.primaloptima.scribe.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeEditorTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.EditorLeftDrawer
import com.primaloptima.scribe.ui.components.EditorRightPanel
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

// ── Scribe Pure Compose Editor Engine imports ────────────────────────────────
import com.primaloptima.scribe.engine.FormatType
import com.primaloptima.scribe.engine.ScribeEditorEngine
import com.primaloptima.scribe.engine.toSerializedDocument
import com.primaloptima.scribe.ui.editor.ScribeEditor
import com.primaloptima.scribe.ui.theme.FontHelper
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


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
    // NOTE: The 5-arg AnchoredDraggableState constructor is deprecated in Compose 1.8,
    // but the suggested replacement (anchoredDraggableFlingBehavior) is marked internal
    // and cannot be called from app code. Keeping the original constructor until Google
    // provides a public migration path. Suppress the deprecation warning instead.
    val localDensity = LocalDensity.current
    @Suppress("DEPRECATION")
    val panelState = remember {
        AnchoredDraggableState(
            initialValue        = PanelState.Center,
            positionalThreshold = { distance: Float -> distance * 0.4f },
            velocityThreshold   = { with(localDensity) { 125.dp.toPx() } },
            snapAnimationSpec   = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec  = splineBasedDecay(localDensity)
        )
    }
    val isLeftDrawerOpen = panelState.targetValue == PanelState.LeftOpen
    val isRightPanelOpen = panelState.targetValue == PanelState.RightOpen

    // ── Gesture conflict fix: NestedScrollConnection ──────────────────────────
    // anchoredDraggable alone consumes every horizontal pixel, including slightly-angled
    // vertical scrolls in the Sora editor. The connection sits in onPreScroll and only
    // forwards a delta to panelState when the horizontal component clearly dominates (2:1
    // ratio). Pure or near-vertical scrolls return Offset.Zero so the editor handles them.
    // onPostFling snaps the panel to whichever anchor it is already targeting — we do NOT
    // call settle(velocity) because that signature no longer accepts a velocity parameter
    // in the Foundation version shipped with BOM 2026.08.00.
    val panelScrollConnection = remember(panelState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val absX = abs(available.x)
                val absY = abs(available.y)
                // Only intercept when horizontal dominates AND the keyboard is not up
                // AND the panel is not already locked to an edge that blocks this direction.
                if (absX < absY * 2f) return Offset.Zero
                val consumed = panelState.dispatchRawDelta(available.x)
                return Offset(consumed, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Snap to the nearest anchor the state already decided on during the drag.
                // animateTo(targetValue) is safe here; if the state is already settled it
                // is a no-op.
                scope.launch { panelState.animateTo(panelState.targetValue) }
                return Velocity.Zero
            }
        }
    }

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
    val vmWordCount    by editorVm.wordCount.collectAsStateWithLifecycle()
    val vmCharCount    by editorVm.charCount.collectAsStateWithLifecycle()
    val vmOutline      by editorVm.outline.collectAsStateWithLifecycle()
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
    var leftDrawerMode  by remember { mutableStateOf("Current") }

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

    // ── Scribe Compose Editor Engine state ────────────────────────────────────
    val engine = remember { ScribeEditorEngine(initialContent = activeNote?.content ?: "") }
    val wordCount     by engine.wordCount.collectAsStateWithLifecycle()
    val charCount     by engine.charCount.collectAsStateWithLifecycle()
    val outline       by engine.outline.collectAsStateWithLifecycle()
    val proseAnalysis by engine.proseAnalysis.collectAsStateWithLifecycle()

    var pillMode     by remember { mutableIntStateOf(0) }
    var pillOffsetX  by remember { mutableFloatStateOf(0f) }
    var pillOffsetY  by remember { mutableFloatStateOf(0f) }

    // FIX 2: prevWordCount now uses rememberSaveable so it survives rotation.
    // Previously a plain remember meant the delta indicator reset to 0 after config change.
    var prevWordCount   by rememberSaveable { mutableIntStateOf(wordCount) }
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

    // Load active note into Scribe engine
    LaunchedEffect(activeNote?.id) {
        activeNote?.let { note ->
            engine.loadDocument(note.toSerializedDocument())
        }
    }

    // Debounced sync of engine text back to EditorViewModel
    LaunchedEffect(engine) {
        snapshotFlow { engine.documentRevision.value }
            .map { engine.exportPlainText() }
            .distinctUntilChanged()
            .debounce(400)
            .collect { text ->
                editorVm.onContentChanged(text)
            }
    }

    DisposableEffect(activeNote?.id) {
        onDispose {
            activeNote?.let {
                editorVm.saveVersionSnapshotOnLeave(engine.exportPlainText())
            }
        }
    }

    // FIX 4: The launcher result was being discarded (variable not stored, never launched).
    // Now stored so it can be called. If you have a UI entry point for connecting an external
    // folder, call externalFolderLauncher.launch(null) from that button/menu item.
    val externalFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
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
                model              = bgUri,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
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
                Box(modifier = Modifier.fillMaxHeight().width(300.dp)) {
                    EditorLeftDrawer(
                        leftDrawerMode   = leftDrawerMode,
                        onModeChange     = { leftDrawerMode = it },
                        currentBookNotes = currentBookNotes,
                        allNotes         = allNotes,
                        activeNoteId     = activeNote?.id,
                        onNoteClick      = { id ->
                            editorVm.loadNote(id)
                            scope.launch { panelState.animateTo(PanelState.Center) }
                        },
                        onAddNote        = { scope.launch { captureForDialog { showCreateNoteDialog = true } } },
                        hazeState        = hazeState,
                        barBlurBitmap    = barBlurBitmap,
                    )
                }

                // Child 1: Main editor (full screen, pushed right when drawer opens)
                Scaffold(
                    containerColor      = Color.Transparent,
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
                    topBar = {
                        EditorTopBarWithMenu(
                            activeNote        = activeNote,
                            zenMode           = zenMode,
                            goalProgress      = goalProgress,
                            isLeftDrawerOpen  = isLeftDrawerOpen,
                            onNavClick        = { scope.launch { panelState.animateTo(if (isLeftDrawerOpen) PanelState.Center else PanelState.LeftOpen) } },
                            onTitleClick      = { if (activeNote != null) scope.launch { captureForDialog { showRenameDialog = true } } },
                            onOpenRightPanel  = { scope.launch { panelState.animateTo(PanelState.RightOpen) } },
                            onToggleFind      = { showFindBar = !showFindBar },
                            onSaveCheckpoint  = {
                                engine.saveSnapshot("Manual Checkpoint")
                                editorVm.saveManualSnapshot(engine.exportPlainText())
                                Toast.makeText(context, "Checkpoint saved", Toast.LENGTH_SHORT).show()
                            },
                            onEnterZen        = { editorVm.setZen(true) },
                            onOpenFloating    = { activeNote?.let { editorVm.openFloatingWindow(it.id) } },
                            onExport          = { fmt -> activeNote?.let { ExportHelper.shareNote(context, it, fmt) } },
                            onVersionHistory  = { editorVm.flushContent(engine.exportPlainText()); onOpenHistory() },
                            onShortcuts       = onOpenShortcuts,
                            onGuide           = onOpenGuide,
                            onSettings        = onOpenSettings,
                        )
                    },
                    bottomBar = {
                        // FIX 5: Removed the shadowed isKeyboardVisible redeclaration that was
                        // inside bottomBar. Now uses the one declared at screen scope.
                        CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
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
                                                "wrap" -> engine.applyFormatWrap(shortcut.payload, shortcut.closing ?: shortcut.payload)
                                                "pair" -> engine.applyFormatWrap(shortcut.payload, shortcut.closing ?: "")
                                                else   -> engine.insertAtCursor(shortcut.payload)
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
                            FindReplaceBar(
                                visible       = showFindBar,
                                findQuery     = findQuery,
                                replaceQuery  = replaceQuery,
                                onFindChange  = {
                                    findQuery = it
                                    engine.searchEngine.search(it)
                                },
                                onReplaceChange = { replaceQuery = it },
                                onPrevious    = {
                                    engine.searchEngine.goToPrevious()?.let { match ->
                                        engine.requestOffsetFocus(match.docOffset)
                                    }
                                },
                                onNext        = {
                                    engine.searchEngine.goToNext()?.let { match ->
                                        engine.requestOffsetFocus(match.docOffset)
                                    }
                                },
                                onReplaceAll  = {
                                    if (findQuery.isNotEmpty()) {
                                        engine.searchEngine.replaceAll(replaceQuery)
                                        editorVm.onContentChanged(engine.exportPlainText())
                                    }
                                },
                                onClose       = {
                                    showFindBar = false
                                    engine.searchEngine.clear()
                                }
                            )

                            // ── Pure Compose Scribe Editor ────────────────────
                            val editorFontFamily = remember(activeTheme?.fontFamily) {
                                FontHelper.getFontFamily(activeTheme?.fontFamily ?: "default")
                            }
                            val editorFontSizeSp = (activeTheme?.fontSize ?: 18).sp
                            val editorTextColor = parseComposeColor(
                                activeTheme?.colors?.text ?: "#000000",
                                MaterialTheme.colorScheme.onBackground
                            )

                            Box(Modifier.fillMaxSize()) {
                                ScribeEditor(
                                    engine = engine,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontFamily = editorFontFamily,
                                        fontSize = editorFontSizeSp,
                                        color = editorTextColor
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Word-count pill
                                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                    WordCountPill(
                                        modifier        = Modifier.align(Alignment.TopEnd),
                                        pillOffsetX     = pillOffsetX,
                                        pillOffsetY     = pillOffsetY,
                                        onOffsetChange  = { dx, dy -> pillOffsetX += dx; pillOffsetY += dy },
                                        pillMode        = pillMode,
                                        onModeClick     = { pillMode = (pillMode + 1) % 3 },
                                        wordCount       = wordCount,
                                        charCount       = charCount,
                                        deltaText       = deltaText,
                                        isPositiveDelta = isPositiveDelta,
                                        hazeState       = LocalHazeState.current,
                                    )

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
                EditorRightPanel(
                    rightPanelTab         = rightPanelTab,
                    onTabChange           = { rightPanelTab = it },
                    pinnedTopNotes        = pinnedTopNotes,
                    pinnedTopIndex        = pinnedTopIndex,
                    pinnedBottomNotes     = pinnedBottomNotes,
                    pinnedBottomIndex     = pinnedBottomIndex,
                    allNotes              = allNotes,
                    worldEntries          = worldEntries,
                    outline               = outline,
                    analysis              = proseAnalysis,
                    activeTheme           = activeTheme,
                    onOutlineClick        = { entry ->
                        engine.requestLineFocus(entry.lineIndex)
                    },
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
                // nestedScroll must be applied before anchoredDraggable so the connection
                // sees raw deltas first and can gate what anchoredDraggable receives.
                // When the keyboard is visible we skip both — the user is typing and panel
                // swipes should be completely disabled to prevent accidental navigation.
                .then(
                    if (!isKeyboardVisible && panelState.currentValue != PanelState.Center)
                        Modifier
                            .nestedScroll(panelScrollConnection)
                            .anchoredDraggable(panelState, Orientation.Horizontal)
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

// ── Extracted: Top bar + overflow menu ───────────────────────────────────────
// FIX 9 (decomposition): Moved the topBar content into its own composable so
// the Scaffold's topBar slot isn't holding 60+ lines of inline logic. This
// also means showMenu recomposition is scoped here instead of touching the parent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBarWithMenu(
    activeNote       : Note?,
    zenMode          : Boolean,
    goalProgress     : Float,
    isLeftDrawerOpen : Boolean,
    onNavClick       : () -> Unit,
    onTitleClick     : () -> Unit,
    onOpenRightPanel : () -> Unit,
    onToggleFind     : () -> Unit,
    onSaveCheckpoint : () -> Unit,
    onEnterZen       : () -> Unit,
    onOpenFloating   : () -> Unit,
    onExport         : (String) -> Unit,
    onVersionHistory : () -> Unit,
    onShortcuts      : () -> Unit,
    onGuide          : () -> Unit,
    onSettings       : () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        ScribeEditorTopBar(
            title          = activeNote?.name,
            onNavClick     = onNavClick,
            onTitleClick   = onTitleClick,
            navigationIcon = Icons.Default.Menu,
            visible        = !zenMode,
            actions        = listOf(
                ScribeBarAction(Icons.Default.Dock,        "Outline & Pinned Notes") { onOpenRightPanel() },
                ScribeBarAction(Icons.Default.Search,      "Find")                   { onToggleFind() },
                ScribeBarAction(Icons.Default.BookmarkAdd, "Save Checkpoint")        { onSaveCheckpoint() },
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
            DropdownMenuItem(text = { Text("Enter Zen Mode") },                  onClick = { showMenu = false; onEnterZen() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Open as Floating Reference Window") }, onClick = { showMenu = false; onOpenFloating() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Export as TXT") },      onClick = { showMenu = false; onExport("txt") })
            DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { showMenu = false; onExport("md") })
            DropdownMenuItem(text = { Text("Export as HTML") },     onClick = { showMenu = false; onExport("html") })
            DropdownMenuItem(text = { Text("Export as PDF") },      onClick = { showMenu = false; onExport("pdf") })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Version History") }, onClick = { showMenu = false; onVersionHistory() })
            DropdownMenuItem(text = { Text("Shortcuts") },       onClick = { showMenu = false; onShortcuts() })
            DropdownMenuItem(text = { Text("User Guide") },      onClick = { showMenu = false; onGuide() })
            DropdownMenuItem(text = { Text("Settings") },        onClick = { showMenu = false; onSettings() })
        }
    }
}

// ── Extracted: Find/Replace bar ───────────────────────────────────────────────
// FIX 9 (decomposition): Pulled out so the Column inside the Scaffold content
// doesn't inline 40+ lines of find/replace UI.
@Composable
private fun FindReplaceBar(
    visible         : Boolean,
    findQuery       : String,
    replaceQuery    : String,
    onFindChange    : (String) -> Unit,
    onReplaceChange : (String) -> Unit,
    onPrevious      : () -> Unit,
    onNext          : () -> Unit,
    onReplaceAll    : () -> Unit,
    onClose         : () -> Unit,
) {
    if (!visible) return
    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = findQuery,
                onValueChange = onFindChange,
                placeholder   = { Text("Find") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value         = replaceQuery,
                onValueChange = onReplaceChange,
                placeholder   = { Text("Replace") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
            IconButton(onClick = onReplaceAll) {
                Icon(Icons.Default.FindReplace, contentDescription = "Replace All")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}

// ── Extracted: Word-count pill ────────────────────────────────────────────────
// FIX 9 (decomposition): Separated the draggable pill from the editor Box so
// drag state and animated content are scoped here and don't invalidate the parent.
@Composable
private fun WordCountPill(
    modifier        : Modifier,
    pillOffsetX     : Float,
    pillOffsetY     : Float,
    onOffsetChange  : (Float, Float) -> Unit,
    pillMode        : Int,
    onModeClick     : () -> Unit,
    wordCount       : Int,
    charCount       : Int,
    deltaText       : String?,
    isPositiveDelta : Boolean,
    hazeState       : dev.chrisbanes.haze.HazeState?,
) {
    Box(
        modifier = modifier
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
                shape           = CircleShape,
                color           = frostedContainerColor(MaterialTheme.colorScheme.primaryContainer),
                tonalElevation  = 0.dp,
                shadowElevation = 0.dp,
                modifier        = Modifier
                    .clip(CircleShape)
                    .frostedFab(hazeState)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onOffsetChange(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clickable { onModeClick() }
            ) {
                AnimatedContent(
                    targetState    = pillMode,
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
                modifier            = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                folderPath.substringAfterLast('/'),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp,
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                    if (isExpanded) {
                        items(notesInFolder, key = { "n_${it.id}" }) { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectNote(note) }
                                    .padding(start = 24.dp),
                                shape  = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    note.name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
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

private fun parseComposeColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { fallback }
