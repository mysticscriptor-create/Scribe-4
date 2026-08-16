package com.primaloptima.scribe.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.FrostedPanelContent
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSpeedDialFab
import com.primaloptima.scribe.ui.components.SpeedDialItem
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.LocalSharedTransitionScope
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.CoverUtils
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeContentCard
import com.primaloptima.scribe.ui.components.ScribeProgressBar
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.withContext

// ── Scroll-hide connection ────────────────────────────────────────────────────
// Tracks scroll direction so bottom bar + FAB can hide/show smoothly.

private class HideOnScrollConnection(
    private val onScrollDown: () -> Unit,
    private val onScrollUp:   () -> Unit
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -8f) onScrollDown()   // user scrolled up  → hide bars
        else if (available.y > 8f) onScrollUp()  // user scrolled down → show bars
        return Offset.Zero
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BookScreen(
    vm: BookViewModel,
    dashboardVm: DashboardViewModel,
    onBack: () -> Unit,
    onOpenNote: (noteId: String) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Snap to Closed on first composition — prevents 1-frame drawer flash during
    // NavDisplay slide-in transition (drawer Animatable initialises at offset 0
    // before it clamps to the closed position).
    LaunchedEffect(Unit) { drawerState.snapTo(DrawerValue.Closed) }

    // Phase 1: shared transition locals
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedContentScope  = if (LocalInspectionMode.current) null
                                else LocalNavAnimatedContentScope.current

    // Pre-API-31 frosted glass bitmaps
    val view         = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    var oneShotBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var captured          by remember { mutableStateOf(false) }
    var isFabExpanded     by remember { mutableStateOf(false) }
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dialog states
    var showCreateNoteDialog   by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var noteToRename  by remember { mutableStateOf<Note?>(null) }
    var noteToDelete  by remember { mutableStateOf<Note?>(null) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showTagsDialog    by remember { mutableStateOf(false) }

    val anyDialogOpen = showCreateNoteDialog || showCreateFolderDialog ||
            noteToRename != null || noteToDelete != null || showSummaryDialog || showTagsDialog

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(isFabExpanded) {
            if (isFabExpanded && !captured) {
                captured = true
                val raw = BitmapBlur.captureOnly(view)
                oneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (!isFabExpanded) {
                captured = false
                oneShotBitmap = null
            }
        }
        LaunchedEffect(anyDialogOpen) {
            if (!anyDialogOpen) dialogOneShotBitmap = null
        }
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

    val book        by vm.book.collectAsStateWithLifecycle()
    val notes       by vm.notes.collectAsStateWithLifecycle()
    val folders     by vm.folders.collectAsStateWithLifecycle()
    val viewMode    by vm.viewMode.collectAsStateWithLifecycle()
    val sortMode    by vm.sortMode.collectAsStateWithLifecycle()
    val ongoingBookId by dashboardVm.ongoingProjectBookId.collectAsStateWithLifecycle()

    var selectedTab        by remember { mutableIntStateOf(0) }
    var selectedFolderPath by remember { mutableStateOf("/") }

    // ── Bottom bar / FAB visibility (scroll-driven) ───────────────────────────
    var barsVisible by remember { mutableStateOf(true) }
    val barsOffsetY by animateFloatAsState(
        targetValue   = if (barsVisible) 0f else 200f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label         = "bars-offset"
    )
    val nestedScrollConnection = remember {
        HideOnScrollConnection(
            onScrollDown = { barsVisible = false },
            onScrollUp   = { barsVisible = true }
        )
    }
    // Always show bars when FAB is open
    LaunchedEffect(isFabExpanded) { if (isFabExpanded) barsVisible = true }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val b = book ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val savedUri = CoverUtils.saveCoverImage(context.applicationContext, b.id, uri)
            val db = (context.applicationContext as ScribeApp).database
            db.bookDao().updateCover(b.id, savedUri, System.currentTimeMillis())
            vm.reload()
        }
    }

    val swipeGestureModifier = Modifier.pointerInput(drawerState) {
        var startX = 0f
        var totalX = 0f
        detectHorizontalDragGestures(
            onDragStart = { offset -> startX = offset.x; totalX = 0f },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                if (drawerState.isClosed && !drawerState.isAnimationRunning
                        && startX < size.width * 0.5f && totalX > 36.dp.toPx()) {
                    change.consume()
                    scope.launch { drawerState.open() }
                }
            }
        )
    }

    val hazeState = LocalHazeState.current

    CompositionLocalProvider(LocalOneShotBitmap provides oneShotBitmap) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .frostedPanel(hazeState)
            ) {
                FrostedPanelContent {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text       = book?.title ?: "Book Folders",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label    = { Text("Main") },
                    selected = selectedFolderPath == "/",
                    onClick  = { selectedFolderPath = "/"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                folders.filter { it.path != "/" }.forEach { folder ->
                    NavigationDrawerItem(
                        icon     = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        label    = { Text(folder.path) },
                        selected = selectedFolderPath == folder.path,
                        onClick  = { selectedFolderPath = folder.path; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick  = { scope.launch { captureForDialog { showCreateFolderDialog = true } } },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Folder")
                }
                } // end FrostedPanelContent
            }
            } // end CompositionLocalProvider(LocalBarBlurBitmap)
        }
    ) {
        Scaffold(
            containerColor      = Color.Transparent,
            modifier            = Modifier.then(swipeGestureModifier),
            contentWindowInsets = WindowInsets.systemBars,
            topBar = {
                // DropdownMenu hoisted outside ScribeTopBar so it can anchor correctly.
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    ScribeTopBar(
                        title             = book?.title ?: "Book",
                        navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                        onNavigationClick = onBack,
                        titleContent      = { titleModifier ->
                            val (titleColor, adaptiveModifier) = rememberAdaptiveTextColor(
                                fallback = MaterialTheme.colorScheme.onSurface
                            )
                            val sharedMod = if (sharedTransitionScope != null && animatedContentScope != null && book != null) {
                                with(sharedTransitionScope) {
                                    adaptiveModifier.then(titleModifier).sharedBounds(
                                        sharedContentState      = rememberSharedContentState(key = "book_title_${book!!.id}"),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                }
                            } else adaptiveModifier.then(titleModifier)
                            Text(
                                text       = book?.title ?: "Book",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 17.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                color      = titleColor,
                                modifier   = sharedMod
                            )
                        },
                        actions = listOf(
                            ScribeBarAction(Icons.Default.Folder,      "Folders")     { scope.launch { drawerState.open() } },
                            ScribeBarAction(
                                if (viewMode == BookViewModel.ViewMode.LIST) Icons.Default.ViewStream else Icons.Default.AccountTree,
                                "Toggle Mode"
                            ) { vm.toggleViewMode() },
                            ScribeBarAction(Icons.Default.MoreVert, "Options") { showSortMenu = true },
                        )
                    )
                    DropdownMenu(
                        expanded         = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor   = LocalSolidSurface.current
                    ) {
                        DropdownMenuItem(text = { Text("Change Book Cover") }, onClick = { showSortMenu = false; coverPickerLauncher.launch("image/*") })
                        DropdownMenuItem(text = { Text("Edit Genre Tags") },   onClick = { showSortMenu = false; scope.launch { captureForDialog { showTagsDialog = true } } })
                        HorizontalDivider()
                        val thisBookId = book?.id
                        val isOngoing  = thisBookId != null && ongoingBookId == thisBookId
                        if (isOngoing) {
                            DropdownMenuItem(
                                text        = { Text("Remove from Ongoing Project") },
                                leadingIcon = { Icon(Icons.Default.BookmarkRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick     = { showSortMenu = false; dashboardVm.clearOngoingProject() }
                            )
                        } else {
                            DropdownMenuItem(
                                text        = { Text("Set as Ongoing Project") },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick     = { showSortMenu = false; if (thisBookId != null) dashboardVm.setOngoingProject(thisBookId) }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Sort by Date Updated") }, onClick = { vm.setSortMode(BookViewModel.SortMode.DATE_UPDATED); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Sort by Date Created") }, onClick = { vm.setSortMode(BookViewModel.SortMode.DATE_CREATED); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Sort by Title (A-Z)") },  onClick = { vm.setSortMode(BookViewModel.SortMode.TITLE_AZ);     showSortMenu = false })
                    }
                }
            },
            bottomBar = {
                // Slide-down hide on scroll, slide-up show on scroll-back
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier       = Modifier
                        .graphicsLayer { translationY = barsOffsetY }
                        .frostedBar(hazeState)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        icon     = { Icon(Icons.Default.EditNote, contentDescription = "Write") },
                        label    = { Text("Write") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        icon     = { Icon(Icons.Default.BarChart, contentDescription = "Statistics") },
                        label    = { Text("Statistics") }
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    Box(modifier = Modifier.graphicsLayer { translationY = barsOffsetY }) {
                        ScribeSpeedDialFab(
                            items = listOf(
                                SpeedDialItem(
                                    icon    = Icons.Default.Description,
                                    label   = "New Text File",
                                    onClick = { scope.launch { captureForDialog { showCreateNoteDialog = true } } }
                                ),
                                SpeedDialItem(
                                    icon    = Icons.Default.CreateNewFolder,
                                    label   = "New Folder",
                                    onClick = { scope.launch { captureForDialog { showCreateFolderDialog = true } } }
                                )
                            ),
                            expanded          = isFabExpanded,
                            onExpandedChange  = { isFabExpanded = it }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(nestedScrollConnection)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { if (isFabExpanded) isFabExpanded = false }
            ) {
                if (selectedTab == 0) {
                    val allFolderPaths = remember(folders) {
                        (listOf("/") + folders.map { it.path }).distinct()
                    }

                    if (viewMode == BookViewModel.ViewMode.LIST) {
                        val pagerState    = rememberPagerState(pageCount = { allFolderPaths.size })
                        val listState     = rememberLazyListState()

                        // ── Collapse progress (0f=expanded, 1f=fully collapsed) ──────────
                        // Driven by scroll offset of the header item.
                        val headerHeightPx = remember { mutableFloatStateOf(0f) }
                        val collapseProgress by remember {
                            derivedStateOf {
                                if (headerHeightPx.floatValue == 0f) 0f
                                else {
                                    val offset = listState.firstVisibleItemScrollOffset.toFloat()
                                    val item   = listState.firstVisibleItemIndex
                                    // Only collapse while header (item 0) is visible
                                    if (item > 0) 1f
                                    else (offset / headerHeightPx.floatValue).coerceIn(0f, 1f)
                                }
                            }
                        }
                        // Fade starts at 60%, finishes at 95%
                        val headerAlpha by remember {
                            derivedStateOf { ((1f - collapseProgress) / 0.4f).coerceIn(0f, 1f) }
                        }

                        LaunchedEffect(pagerState.currentPage) {
                            selectedFolderPath = allFolderPaths[pagerState.currentPage]
                        }

                        // ── New structure: Column { CollapsibleHeader + StickyTabRow + LazyColumn }
                        Column(modifier = Modifier.fillMaxSize()) {

                            // ── Collapsible header — clips height as user scrolls ──────────
                            // Height goes from full → 0 as collapseProgress 0→1.
                            // Alpha fades from 1→0 in the last 40% of collapse.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = headerAlpha
                                        // clip content that would overflow during collapse
                                        clip = true
                                    }
                                    .onGloballyPositioned { coords ->
                                        // Capture natural height once, before any collapse
                                        if (headerHeightPx.floatValue == 0f)
                                            headerHeightPx.floatValue = coords.size.height.toFloat()
                                    }
                                    // Animate the height from full → 0
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val height = (placeable.height * (1f - collapseProgress)).toInt().coerceAtLeast(0)
                                        layout(placeable.width, height) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                            ) {
                                book?.let { b ->
                                    BookInfoHeader(
                                        book           = b,
                                        notes          = notes,
                                        folders        = folders,
                                        onSummaryClick = {
                                            scope.launch { captureForDialog { showSummaryDialog = true } }
                                        }
                                    )
                                }
                            }

                            // ── Sticky tab row — always pinned below ScribeTopBar ──────────
                            Surface(
                                color     = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier  = Modifier.fillMaxWidth()
                            ) {
                                ScrollableTabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    edgePadding      = 12.dp,
                                    containerColor   = Color.Transparent,
                                    indicator        = { tabPositions ->
                                        // Pill indicator behind selected tab
                                        val accentColor = LocalAccentColor.current
                                        if (pagerState.currentPage < tabPositions.size) {
                                            val pos = tabPositions[pagerState.currentPage]
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .wrapContentSize(Alignment.BottomStart)
                                                    .offset(x = pos.left - 4.dp)
                                                    .width(pos.width + 8.dp)
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                    .background(accentColor)
                                            )
                                        }
                                    },
                                    divider = {}
                                ) {
                                    allFolderPaths.forEachIndexed { index, path ->
                                        val label    = if (path == "/") "Main" else path.removePrefix("/")
                                        val selected = pagerState.currentPage == index
                                        Tab(
                                            selected = selected,
                                            onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                                            text     = {
                                                Text(
                                                    label,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize   = 13.sp
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // ── Note list — this is the only scrollable part ───────────────
                            HorizontalPager(
                                state    = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val currentPath = allFolderPaths[page]
                                val pageNotes   = notes.filter { it.folderPath == currentPath }

                                if (pageNotes.isEmpty()) {
                                    Box(
                                        modifier         = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Outlined.Description,
                                                contentDescription = null,
                                                modifier           = Modifier.size(56.dp),
                                                tint               = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                "No notes in ${if (currentPath == "/") "Main" else currentPath}",
                                                fontSize = 15.sp,
                                                color    = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state          = listState,
                                        contentPadding = PaddingValues(
                                            start  = 12.dp,
                                            end    = 12.dp,
                                            top    = 8.dp,
                                            bottom = 88.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier            = Modifier.fillMaxSize()
                                    ) {
                                        items(pageNotes, key = { it.id }) { note ->
                                            NoteListRow(
                                                note        = note,
                                                onClick     = { onOpenNote(note.id) },
                                                onOpenFloat = { onOpenNote(note.id) },
                                                onRename    = { scope.launch { captureForDialog { noteToRename = note } } },
                                                onDuplicate = { vm.duplicateNote(note.id) },
                                                onDelete    = { scope.launch { captureForDialog { noteToDelete = note } } }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    } else {
                        // TREE MODE — collapsing header + tree list
                        val treeListState = rememberLazyListState()
                        val rootNotes   = remember(notes)   { notes.filter { it.folderPath == "/" } }
                        val folderPaths = remember(folders) { folders.map { it.path }.filter { it != "/" }.sorted() }

                        val treeHeaderHeightPx = remember { mutableFloatStateOf(0f) }
                        val treeCollapseProgress by remember {
                            derivedStateOf {
                                if (treeHeaderHeightPx.floatValue == 0f) 0f
                                else {
                                    val offset = treeListState.firstVisibleItemScrollOffset.toFloat()
                                    val item   = treeListState.firstVisibleItemIndex
                                    if (item > 0) 1f
                                    else (offset / treeHeaderHeightPx.floatValue).coerceIn(0f, 1f)
                                }
                            }
                        }
                        val treeHeaderAlpha by remember {
                            derivedStateOf { ((1f - treeCollapseProgress) / 0.4f).coerceIn(0f, 1f) }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = treeHeaderAlpha; clip = true }
                                    .onGloballyPositioned { coords ->
                                        if (treeHeaderHeightPx.floatValue == 0f)
                                            treeHeaderHeightPx.floatValue = coords.size.height.toFloat()
                                    }
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val height = (placeable.height * (1f - treeCollapseProgress)).toInt().coerceAtLeast(0)
                                        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                                    }
                            ) {
                                book?.let { b ->
                                    BookInfoHeader(
                                        book           = b,
                                        notes          = notes,
                                        folders        = folders,
                                        onSummaryClick = {
                                            scope.launch { captureForDialog { showSummaryDialog = true } }
                                        }
                                    )
                                }
                            }

                            LazyColumn(
                                state          = treeListState,
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier       = Modifier.fillMaxSize()
                            ) {
                                if (rootNotes.isNotEmpty()) {
                                    items(rootNotes, key = { "root_${it.id}" }) { note ->
                                        NoteListRow(
                                            note        = note,
                                            modifier    = Modifier.padding(horizontal = 12.dp),
                                            onClick     = { onOpenNote(note.id) },
                                            onOpenFloat = { onOpenNote(note.id) },
                                            onRename    = { scope.launch { captureForDialog { noteToRename = note } } },
                                            onDuplicate = { vm.duplicateNote(note.id) },
                                            onDelete    = { scope.launch { captureForDialog { noteToDelete = note } } }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                                folderPaths.forEach { fPath ->
                                    item(key = "folder_header_$fPath") {
                                        TreeFolderHeader(fPath = fPath, notes = notes) { note ->
                                            onOpenNote(note.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    BookStatisticsTab(notes = notes, bookTitle = book?.title ?: "Book")
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {

        if (showCreateNoteDialog) {
            var noteTitle by remember { mutableStateOf("") }
            FrostedDialog(
                onDismissRequest = { showCreateNoteDialog = false },
                title            = { Text("New Note") },
                text             = {
                    OutlinedTextField(
                        value         = noteTitle,
                        onValueChange = { noteTitle = it },
                        label         = { Text("Note Title") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = noteTitle.trim()
                        if (t.isNotEmpty()) {
                            vm.createNote(t, selectedFolderPath) { id ->
                                showCreateNoteDialog = false
                                onOpenNote(id)
                            }
                        }
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreateNoteDialog = false }) { Text("Cancel") } }
            )
        }

        if (showCreateFolderDialog) {
            var folderName by remember { mutableStateOf("") }
            FrostedDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title            = { Text("New Folder") },
                text             = {
                    OutlinedTextField(
                        value         = folderName,
                        onValueChange = { folderName = it },
                        label         = { Text("Folder Name (e.g. Chapter 1)") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val f = folderName.trim()
                        if (f.isNotEmpty()) {
                            val path = if (selectedFolderPath == "/") "/$f" else "$selectedFolderPath/$f"
                            vm.createFolder(path)
                            showCreateFolderDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
            )
        }

        noteToRename?.let { note ->
            var renameText by remember { mutableStateOf(note.name) }
            FrostedDialog(
                onDismissRequest = { noteToRename = null },
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
                        if (t.isNotEmpty()) vm.renameNote(note.id, t)
                        noteToRename = null
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { noteToRename = null }) { Text("Cancel") } }
            )
        }

        noteToDelete?.let { note ->
            FrostedDialog(
                onDismissRequest = { noteToDelete = null },
                title            = { Text("Delete Note?") },
                text             = { Text("Are you sure you want to delete \"${note.name}\"?") },
                confirmButton    = {
                    TextButton(onClick = { vm.deleteNote(note.id); noteToDelete = null }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text("Cancel") } }
            )
        }

        // Summary edit dialog
        if (showSummaryDialog) {
            val currentBook = book
            var editText by remember(currentBook?.summary) {
                mutableStateOf(currentBook?.summary ?: "")
            }
            FrostedDialog(
                onDismissRequest = { showSummaryDialog = false },
                title            = { Text("Book Summary") },
                text             = {
                    OutlinedTextField(
                        value         = editText,
                        onValueChange = { editText = it },
                        label         = { Text("Write a short summary of your book…") },
                        minLines      = 4,
                        maxLines      = 10,
                        modifier      = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.saveSummary(editText.trim())
                        showSummaryDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSummaryDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Tags edit dialog
        if (showTagsDialog) {
            val currentBook = book
            // Show existing tags as comma-separated text for easy editing
            var editText by remember(currentBook?.tags) {
                mutableStateOf(currentBook?.tags ?: "")
            }
            FrostedDialog(
                onDismissRequest = { showTagsDialog = false },
                title            = { Text("Genre Tags") },
                text             = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = editText,
                            onValueChange = { editText = it },
                            label         = { Text("Tags (comma-separated)") },
                            placeholder   = { Text("e.g. Dark Fantasy, Adventure, Romance") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Text(
                            text     = "Separate each tag with a comma.",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.saveTags(editText.trim())
                        showTagsDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showTagsDialog = false }) { Text("Cancel") }
                }
            )
        }

    } // end CompositionLocalProvider(dialogOneShotBitmap)
    } // end CompositionLocalProvider(oneShotBitmap)
}

// ── Book Info Header ──────────────────────────────────────────────────────────

@Composable
private fun BookInfoHeader(
    book:           com.primaloptima.scribe.data.Book,
    notes:          List<Note>,
    folders:        List<Folder>,
    onSummaryClick: () -> Unit
) {
    val context     = LocalContext.current
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val outline     = MaterialTheme.colorScheme.outline
    val surface     = MaterialTheme.colorScheme.surface

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedContentScope  = if (LocalInspectionMode.current) null
                                else LocalNavAnimatedContentScope.current

    val totalWords  = remember(notes) { notes.sumOf { it.wordCount } }
    val fileCount   = notes.size
    val folderCount = folders.size

    val tagList = remember(book.tags) {
        book.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ── Ambient cover color extraction ────────────────────────────────────
    // Load cover bitmap on IO, extract a dominant edge color, blend to surface.
    var ambientColor by remember(book.coverUri) { mutableStateOf<Color?>(null) }
    LaunchedEffect(book.coverUri) {
        if (book.coverUri.isNullOrBlank()) { ambientColor = null; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val loader  = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(book.coverUri)
                    .allowHardware(false)
                    .size(64, 64) // tiny — we only need color info
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bmp    = (result.image as? coil3.BitmapImage)?.bitmap ?: return@withContext
                    // Sample a grid of pixels and average them for a dominant tone
                    var r = 0L; var g = 0L; var b = 0L; var count = 0
                    val step = maxOf(1, bmp.width / 8)
                    for (x in 0 until bmp.width step step) {
                        for (y in 0 until bmp.height step step) {
                            val px = bmp.getPixel(x, y)
                            r += android.graphics.Color.red(px)
                            g += android.graphics.Color.green(px)
                            b += android.graphics.Color.blue(px)
                            count++
                        }
                    }
                    if (count > 0) {
                        ambientColor = Color(
                            red   = (r / count / 255f),
                            green = (g / count / 255f),
                            blue  = (b / count / 255f),
                            alpha = 1f
                        )
                    }
                }
            } catch (_: Exception) { ambientColor = null }
        }
    }

    // Build ambient gradient: dominant color (muted) → surface
    val ambientBrush = remember(ambientColor, surface) {
        val top = ambientColor?.copy(alpha = 0.28f) ?: Color.Transparent
        Brush.verticalGradient(colors = listOf(top, surface))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Fill entire header area with ambient gradient
                drawRect(brush = ambientBrush)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // ── Cover + title/tags/stats ──────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment     = Alignment.Top
            ) {
                // Cover — shared element transition preserved
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    if (!book.coverUri.isNullOrBlank()) {
                        val coverModifier = if (sharedTransitionScope != null && animatedContentScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.fillMaxSize().sharedElement(
                                    sharedContentState      = rememberSharedContentState(key = "book_cover_${book.id}"),
                                    animatedVisibilityScope = animatedContentScope
                                )
                            }
                        } else Modifier.fillMaxSize()
                        AsyncImage(
                            model              = ImageRequest.Builder(context).data(book.coverUri).crossfade(true).build(),
                            contentDescription = "Book cover",
                            contentScale       = ContentScale.Crop,
                            modifier           = coverModifier
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector        = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                modifier           = Modifier.size(36.dp),
                                tint               = accentColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Right column
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = book.title,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = onSurface,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )

                    // Tags
                    if (tagList.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            items(tagList) { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(accentColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(tag, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    } else {
                        Text(
                            text      = "Tap ··· to add genre tags",
                            fontSize  = 12.sp,
                            color     = outline.copy(alpha = 0.6f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        StatChip(Icons.Outlined.TextFields, formatWordCount(totalWords), accentColor)
                        StatChip(Icons.Outlined.Description, "$fileCount", outline)
                        StatChip(Icons.Outlined.Folder, "$folderCount", outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Tonal surface separator ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(onSurface.copy(alpha = 0.06f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Summary — tonal surface, no card border ───────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(onSurface.copy(alpha = 0.05f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { onSummaryClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Summary", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(13.dp), tint = outline.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    if (book.summary.isBlank()) {
                        Text(
                            text      = "Tap to add a summary for this book…",
                            fontSize  = 13.sp,
                            color     = outline.copy(alpha = 0.55f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines  = 2
                        )
                    } else {
                        Text(
                            text     = book.summary,
                            fontSize = 13.sp,
                            color    = onSurface.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
        Text(text = label, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

private fun formatWordCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M words"
        count >= 1_000     -> "${"%.1f".format(count / 1_000.0)}K words"
        else               -> "$count words"
    }
}

// ── Tree folder header (for tree mode inside LazyColumn) ─────────────────────

@Composable
private fun TreeFolderHeader(
    fPath:       String,
    notes:       List<Note>,
    onNoteClick: (Note) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val accentColor = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(fPath, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }

        if (isExpanded) {
            val fNotes = notes.filter { it.folderPath == fPath }
            fNotes.forEach { note ->
                // Inline note rows (no nested lazy — outer LazyColumn handles scroll)
                NoteListRowStateless(note = note, onNoteClick = onNoteClick)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun NoteListRowStateless(
    note:        Note,
    onNoteClick: (Note) -> Unit
) {
    // Same visual as NoteListRow but without the context-menu actions
    NoteListRow(
        note        = note,
        modifier    = Modifier.padding(start = 24.dp, end = 12.dp),
        onClick     = { onNoteClick(note) },
        onOpenFloat = { onNoteClick(note) },
        onRename    = {},
        onDuplicate = {},
        onDelete    = {}
    )
}

@Composable
private fun BookStatisticsTab(notes: List<Note>, bookTitle: String) {
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val outline     = MaterialTheme.colorScheme.outline

    val totalWords = remember(notes) { notes.sumOf { it.wordCount } }
    val scoredNotes = remember(notes) {
        notes.map { n -> n to n.wordCount }.sortedByDescending { it.second }
    }
    val maxWords = (scoredNotes.firstOrNull()?.second ?: 1).coerceAtLeast(1).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScribeContentCard(title = "Statistics for \"$bookTitle\"") {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ScribeCard(modifier = Modifier.weight(1f), cornerRadius = ScribeCardTokens.RadiusMedium, accentBorder = true, shine = true) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "${notes.size}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(text = "Total Files", fontSize = 12.sp, color = outline)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                ScribeCard(modifier = Modifier.weight(1f), cornerRadius = ScribeCardTokens.RadiusMedium, accentBorder = true, shine = true) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "$totalWords", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accentColor)
                        Text(text = "Total Words", fontSize = 12.sp, color = outline)
                    }
                }
            }
        }

        ScribeContentCard(title = "Files Word Count Ranking") {
            if (scoredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No files in this book", color = outline)
                }
            } else {
                scoredNotes.forEachIndexed { index, (note, count) ->
                    val ratio = (count / maxWords).coerceIn(0.05f, 1.0f)
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.25f), accentColor.copy(alpha = 0.25f), Color.Transparent))))
                        }
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(accentColor.copy(alpha = if (index == 0) 0.22f else 0.10f)), contentAlignment = Alignment.Center) {
                                    Text(text = "${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                                Text(text = note.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            Text(text = "$count words", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Medium)
                        }
                        Text(text = "Folder: ${note.folderPath}", fontSize = 11.sp, color = outline)
                        ScribeProgressBar(progress = ratio, modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteListRow(
    note:        Note,
    modifier:    Modifier = Modifier,
    onClick:     () -> Unit,
    onOpenFloat: () -> Unit,
    onRename:    () -> Unit,
    onDuplicate: () -> Unit,
    onDelete:    () -> Unit
) {
    var showMenu    by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val outline     = MaterialTheme.colorScheme.outline

    val wordLabel   = remember(note.wordCount) { formatWordCount(note.wordCount) }
    val previewText = remember(note.content) {
        note.content.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" ").ifBlank { null }
    }
    val createdStr  = remember(note.createdAt) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(note.createdAt))
    }
    val modifiedStr = remember(note.updatedAt) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
    }

    ScribeCard(
        modifier     = modifier.fillMaxWidth(),
        cornerRadius = ScribeCardTokens.RadiusMedium,
        onClick      = onClick,
        accentBorder = true,
        shine        = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // ── Title row + word count pill ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = note.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f).padding(end = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Word count pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentColor.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(wordLabel, fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Medium)
                    }
                    // 3-dot menu
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp), tint = onSurface.copy(alpha = 0.5f))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = LocalSolidSurface.current) {
                            DropdownMenuItem(text = { Text("Open") },                    onClick = { showMenu = false; onClick() })
                            DropdownMenuItem(text = { Text("Open in Floating Window") }, onClick = { showMenu = false; onOpenFloat() })
                            DropdownMenuItem(text = { Text("Rename") },                  onClick = { showMenu = false; onRename() })
                            DropdownMenuItem(text = { Text("Duplicate") },               onClick = { showMenu = false; onDuplicate() })
                            DropdownMenuItem(text = { Text("Delete") },                  onClick = { showMenu = false; onDelete() })
                        }
                    }
                }
            }

            // ── Preview ───────────────────────────────────────────────────
            if (previewText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = previewText,
                    fontSize = 12.sp,
                    color    = onSurface.copy(alpha = 0.52f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            // ── Compact footer: created · modified ────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(11.dp), tint = outline.copy(alpha = 0.6f))
                Text(createdStr, fontSize = 11.sp, color = outline.copy(alpha = 0.6f))
                Text("·", fontSize = 11.sp, color = outline.copy(alpha = 0.4f))
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(11.dp), tint = outline.copy(alpha = 0.6f))
                Text(modifiedStr, fontSize = 11.sp, color = outline.copy(alpha = 0.6f))
            }
        }
    }
}
