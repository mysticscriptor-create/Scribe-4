package com.primaloptima.scribe.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import android.graphics.Bitmap
import android.os.Build
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.localHasBgImage
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedPanelContent

import androidx.compose.material3.LocalContentColor
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.GrainTexture
import androidx.compose.ui.platform.LocalView
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.primaloptima.scribe.LocalSharedTransitionScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.primaloptima.scribe.ui.components.ScribeExtendedFab
import com.primaloptima.scribe.ui.components.ScribeSpeedDialFab
import com.primaloptima.scribe.ui.components.SpeedDialItem
import com.primaloptima.scribe.ui.components.ScribeFabTokens
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.components.ScribeNavBar
import com.primaloptima.scribe.ui.components.ScribeNavItem
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.*
import com.primaloptima.scribe.R
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.CoverUtils
import com.primaloptima.scribe.viewmodel.BooksViewModel
import com.primaloptima.scribe.viewmodel.DashboardViewModel
import com.primaloptima.scribe.viewmodel.HomeShellViewModel
import com.primaloptima.scribe.viewmodel.NotesViewModel
import com.primaloptima.scribe.viewmodel.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    shellVm: HomeShellViewModel,
    dashboardVm: DashboardViewModel,
    booksVm: BooksViewModel,
    notesVm: NotesViewModel,
    statsVm: StatsViewModel,
    onOpenBook: (Book) -> Unit,
    onOpenNote: (noteId: String, bookId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSheets: () -> Unit,
    onOpenSheetsCreate: () -> Unit,
    onOpenThemes: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Snap to Closed on first composition so the drawer's internal Animatable starts
    // at its correct off-screen position. Without this, NavDisplay's slide-in transition
    // causes a single-frame flash as the Animatable animates from 0 → closed offset.
    LaunchedEffect(Unit) { drawerState.snapTo(DrawerValue.Closed) }
    var rightPanelVisible by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)

    // Make the system navigation bar follow the app theme.
    // We set it transparent so the frosted bottom bar shows through, with light
    // or dark icons matching the current color scheme.
    val isDarkTheme = MaterialTheme.colorScheme.surface.let { color ->
        val r = color.red * 0.299f
        val g = color.green * 0.587f
        val b = color.blue * 0.114f
        (r + g + b) < 0.5f
    }
    SideEffect {
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, view).isAppearanceLightNavigationBars = !isDarkTheme
    }

    // ── Left drawer blur (pre-API-31) ────────────────────────────────────────
    // We use LocalBarBlurBitmap (derived from the background image by ScribeTheme)
    // rather than a live screen capture. The live-capture path raced the 300 ms
    // open animation on low-end devices and lost, producing a visible flash.
    // LocalBarBlurBitmap is always ready before any UI is shown, so the drawer
    // glass is correct from frame one — no capture, no async blur, no flash.

    // ── Right panel blur (pre-API-31) ────────────────────────────────────────
    // Same rationale as the left drawer: use LocalBarBlurBitmap instead of a
    // live capture that races the slide-in animation.

    val hazeState = LocalHazeState.current

    // 0: Dashboard, 1: Books, 2: Notes, 3: Statistics
    val homeStartPage by booksVm.homeStartPage.collectAsStateWithLifecycle()
    val initialPage = remember(homeStartPage) { if (homeStartPage == "dashboard") 0 else 1 }
    val selectedNavTab by shellVm.selectedTab.collectAsStateWithLifecycle()
    var isGridMode by remember { mutableStateOf(true) }
    val gridColumns by booksVm.gridColumns.collectAsStateWithLifecycle()

    // Pre-bake grain texture on first composition so the first drawer open uses
    // the cached bitmap rather than the inline LCG fallback. Warm-up runs on IO
    // inside GrainTexture; this call returns immediately.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && localHasBgImage()) {
        val screenW = view.rootView.width
        val screenH = view.rootView.height
        LaunchedEffect(screenW, screenH) {
            if (screenW > 0 && screenH > 0) {
                GrainTexture.warmUp(screenW, screenH)
            }
        }
    }

    LaunchedEffect(Unit) {
        shellVm.setInitialTab(initialPage)
    }
    // Collapse speed-dial whenever the user switches tabs
    LaunchedEffect(selectedNavTab) {
        fabExpanded = false
    }
    // Collapse speed-dial when the navigation drawer opens
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) fabExpanded = false
    }

    // Search state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allBooks by booksVm.books.collectAsStateWithLifecycle()
    // Pre-sorted list from ViewModel StateFlow — sort runs in the coroutine layer,
    // never inside composition. See BooksViewModel.sortedBooks for details.
    val sortedBooks by booksVm.sortedBooks.collectAsStateWithLifecycle()
    val allNotes by notesVm.allNotes.collectAsStateWithLifecycle()
    val allFolders by notesVm.allFolders.collectAsStateWithLifecycle()

    // Book stats — all driven by DB-backed VM StateFlows; no in-memory loops.
    val bookWordCounts by booksVm.bookWordCounts.collectAsStateWithLifecycle()
    // Phase 2-A: DB aggregate from VM — no Kotlin loop over allNotes on every recomposition
    val bookFileCounts by booksVm.bookNoteCounts.collectAsStateWithLifecycle()
    // DB aggregate — replaces in-memory allFolders.count { } loop
    val bookFolderCounts by booksVm.bookFolderCounts.collectAsStateWithLifecycle()

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToChangeCover by remember { mutableStateOf<Book?>(null) }

    // One-shot capture for dialogs on pre-API-31 devices.
    //
    // KEY FIX: FrostedDialog is an inline Box composable (not a system Dialog/Popup
    // window), so it renders in the SAME recomposition frame that sets showXxx = true.
    // LaunchedEffect fires AFTER layout+draw, meaning captureOnly was capturing the
    // screen *after* the white dialog was already painted — blurring a white rectangle.
    //
    // Solution: capture BEFORE setting the show-flag. captureForDialog() runs the
    // capture on the current coroutine (Main dispatcher via LaunchedEffect/scope.launch
    // which both default to Main), stores the bitmap, then sets the flag so the dialog
    // composable first renders with a valid blur behind it.
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Clears the bitmap when all dialogs close so the next open gets a fresh capture.
    val anyDialogOpen = showCreateDialog || bookToRename != null ||
            bookToDelete != null || bookToChangeCover != null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) {
            if (!anyDialogOpen) dialogOneShotBitmap = null
        }
    }

    // Helper: capture → blur → store, then execute the lambda that opens the dialog.
    // All of this happens before the dialog composable ever enters the tree.
    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // captureOnly uses view.rootView.draw() which requires the Main thread.
            // scope.launch / LaunchedEffect both run on Main by default, so this is safe.
            val raw = BitmapBlur.captureOnly(view)
            dialogOneShotBitmap = withContext(Dispatchers.IO) {
                raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
            }
        }
        openDialog()   // NOW set the flag — dialog renders with bitmap already in place
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val book = bookToChangeCover ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val savedUri = CoverUtils.saveCoverImage(context.applicationContext, book.id, uri)
            booksVm.updateCover(book.id, savedUri)
        }
        bookToChangeCover = null
    }

    val swipeGestureModifier = Modifier.pointerInput(drawerState, rightPanelVisible) {
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
                // Left-edge swipe → open navigation drawer.
                // Capture BEFORE drawerState.open() so the bitmap contains clean
                // screen content — no drawer pixels, no recomposition yet.
                // rawDrawerBitmap assignment triggers the IO blur LaunchedEffect
                // which runs in parallel with the opening animation.
                if (drawerState.isClosed && !drawerState.isAnimationRunning
                        && startX < size.width * 0.3f && totalX > threshold) {
                    change.consume()
                    scope.launch { drawerState.open() }
                }
                // Right-edge swipe → open stats panel.
                if (!rightPanelVisible && startX > size.width * 0.72f && totalX < -threshold) {
                    change.consume()
                    rightPanelVisible = true
                }
            }
        )
    }

    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
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
                val accentColor = LocalAccentColor.current
                val (adaptiveTextColor, adaptiveTextModifier) = rememberAdaptiveTextColor(
                    fallback = MaterialTheme.colorScheme.onSurface
                )
                val currentStreak by dashboardVm.currentStreak.collectAsStateWithLifecycle()

                // ── HEADER: icon + wordmark + avatar + streak ──
                Spacer(modifier = Modifier.height(28.dp))
                // Row with fillMaxWidth reliably pins logo to start and avatar+streak
                // to end inside ModalDrawerSheet, regardless of its internal Column alignment.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Wordmark — two Icons share the same viewport so they overlap perfectly,
                    // letting ic_scribe_s (the coloured S+quill) sit on top of ic_scribe_text.
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .wrapContentWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_scribe_text),
                            contentDescription = null,
                            tint = adaptiveTextColor,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2048f / 922f)
                                .then(adaptiveTextModifier) // required: without this, bounds stays Rect.Zero and the fallback live-analysis path always returns white
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_scribe_s),
                            contentDescription = "Scribe",
                            tint = accentColor,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2048f / 922f)
                        )
                    }
                    // Avatar + streak pinned to end
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accentColor.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🔥 $currentStreak ${if (currentStreak == 1) "Day" else "Days"}",
                                fontSize = 11.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── INNER CARD with nav items ──
                val innerCardBg = if (hazeState != null) {
                    // Frosted glass active: semi-transparent overlay
                    Color.White.copy(alpha = 0.07f)
                } else {
                    // No frosted glass: slightly lifted surface
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(innerCardBg)
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {

                        // Section label: NAVIGATION
                        Text(
                            text = "NAVIGATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            color = LocalContentColor.current.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )

                        // World Sheets item
                        DrawerNavItem(
                            icon = Icons.Default.Map,
                            label = "World Sheets",
                            accentColor = accentColor,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSheets()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Section label: TOOLS
                        Text(
                            text = "TOOLS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            color = LocalContentColor.current.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )

                        // Themes item
                        DrawerNavItem(
                            icon = Icons.Default.Palette,
                            label = "Themes",
                            accentColor = accentColor,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenThemes()
                            }
                        )

                        // Settings item
                        DrawerNavItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            accentColor = accentColor,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
                } // end FrostedPanelContent
            } // end CompositionLocalProvider(LocalOneShotBitmap provides oneShotBitmap)
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.then(swipeGestureModifier),
            topBar = {
                // Full-screen search overlay — slides down from top when searching.
                AnimatedVisibility(
                    visible = isSearching,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(220)) + fadeIn(tween(220)),
                    exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(180)) + fadeOut(tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 8.dp, bottom = 8.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search titles, notes, folders...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearching = false
                                }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    }
                }

                // Shared bar — only visible when NOT searching.
                // DropdownMenu for sort/grid is hoisted here since ScribeTopBar
                // delivers the MoreVert click via ScribeBarAction.onClick.
                if (!isSearching) {
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        ScribeTopBar(
                            title             = "Scribe",
                            navigationIcon    = Icons.Default.Menu,
                            onNavigationClick = { scope.launch { drawerState.open() } },
                            actions = buildList {
                                add(ScribeBarAction(Icons.Default.Search, "Search") { isSearching = true })
                                if (selectedNavTab == 1) {
                                    add(ScribeBarAction(
                                        if (isGridMode) Icons.Filled.ViewList else Icons.Default.GridView,
                                        "Toggle view"
                                    ) { isGridMode = !isGridMode })
                                    add(ScribeBarAction(Icons.Default.MoreVert, "More options") { showSortMenu = true })
                                }
                            }
                        )
                        if (selectedNavTab == 1) {
                            DropdownMenu(
                                expanded         = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor   = LocalSolidSurface.current
                            ) {
                                if (isGridMode) {
                                    DropdownMenuItem(
                                        text = { Text(if (gridColumns == 2) "3 Columns" else "2 Columns") },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            val nextCols = if (gridColumns == 2) 3 else 2
                                            booksVm.setGridColumns(nextCols)
                                            showSortMenu = false
                                        }
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(text = { Text("Date Updated") }, onClick = { booksVm.setSortMode(BooksViewModel.SortMode.DATE_UPDATED); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Date Created") }, onClick = { booksVm.setSortMode(BooksViewModel.SortMode.DATE_CREATED); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Title (A-Z)") },  onClick = { booksVm.setSortMode(BooksViewModel.SortMode.TITLE_AZ);     showSortMenu = false })
                            }
                        }
                    }
                }
            },
            bottomBar = {
                ScribeNavBar(
                    items = listOf(
                        ScribeNavItem(Icons.Default.Dashboard,   "Dashboard"),
                        ScribeNavItem(Icons.Default.Book,        "Books"),
                        ScribeNavItem(Icons.Filled.StickyNote2,  "Notes"),
                        ScribeNavItem(Icons.Default.BarChart,    "Stats"),
                    ),
                    selectedIndex = selectedNavTab,
                    onTabSelected = { tab ->
                        shellVm.selectTab(tab)
                        isSearching = false
                        fabExpanded = false
                    }
                )
            },
            floatingActionButton = {
                // FAB is always visible — LocalBarBlurBitmap (derived from the Coil
                // image in ScribeTheme) is already loaded, no screen capture needed.
                CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
                AnimatedContent(
                    targetState = selectedNavTab,
                    transitionSpec = {
                        ScribeFabTokens.TabSwitchEnter togetherWith ScribeFabTokens.TabSwitchExit
                    },
                    label = "fabSwitch"
                ) { tab ->
                    when (tab) {
                        0 -> Box(Modifier) // Dashboard — no FAB; actions live inside the screen
                        1 -> ScribeSpeedDialFab(
                            items = listOf(
                                SpeedDialItem(
                                    icon  = Icons.Default.Add,
                                    label = "New Book",
                                    onClick = {
                                        scope.launch { captureForDialog { showCreateDialog = true } }
                                    }
                                ),
                                SpeedDialItem(
                                    icon  = Icons.Outlined.Book,
                                    label = "New Sheet",
                                    onClick = { onOpenSheetsCreate() }
                                ),
                            ),
                            expanded         = fabExpanded,
                            onExpandedChange = { fabExpanded = it },
                        )
                        2 -> ScribeExtendedFab(
                            icon  = Icons.Default.Edit,
                            label = "Quick Note",
                            onClick = {
                                booksVm.createQuickNote { note ->
                                    onOpenNote(note.id, note.bookId)
                                }
                            }
                        )
                        else -> Box(Modifier)
                    }
                }
                } // end CompositionLocalProvider(LocalBarBlurBitmap for FAB)
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isSearching) {
                    SearchResultsView(
                        query = searchQuery,
                        allBooks = allBooks,
                        allNotes = allNotes,
                        onOpenNote = { note ->
                            onOpenNote(note.id, note.bookId)
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = selectedNavTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                            } else {
                                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                            }
                        },
                        label = "tab-content",
                        modifier = Modifier
                            .fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> DashboardTabContent(
                                vm = dashboardVm,
                                allBooks = allBooks,
                                bookWordCounts = bookWordCounts,
                                onOpenNote = onOpenNote,
                                onOpenBook = onOpenBook,
                                onGoToStats = {
                                    shellVm.selectTab(3)
                                },
                                onGoToBooks = {
                                    shellVm.selectTab(1)
                                },
                                onOpenSheets = onOpenSheets
                            )
                            1 -> BooksTabContent(
                                books = sortedBooks,
                                isGridMode = isGridMode,
                                gridColumns = gridColumns,
                                wordCounts = bookWordCounts,
                                fileCounts = bookFileCounts,
                                folderCounts = bookFolderCounts,
                                allNotes = allNotes,
                                onOpen = onOpenBook,
                                onRename = { book -> scope.launch { captureForDialog { bookToRename = book } } },
                                onChangeCover = {
                                    bookToChangeCover = it
                                    coverPickerLauncher.launch("image/*")
                                },
                                onDelete = { book -> scope.launch { captureForDialog { bookToDelete = book } } }
                            )
                            2 -> NotesTabContent(
                                allNotes = allNotes,
                                onOpenNote = { note ->
                                    onOpenNote(note.id, note.bookId)
                                }
                            )
                            3 -> MainStatisticsTabContent(
                                dashboardVm = dashboardVm,
                                statsVm = statsVm,
                                allBooks = allBooks,
                                allNotes = allNotes,
                                allFolders = allFolders,
                                bookWordCounts = bookWordCounts
                            )
                        }
                    }
                }

                // ── FAB speed-dial scrim — fades in behind the card, above pager ──
                AnimatedVisibility(
                    visible = fabExpanded && selectedNavTab == 1,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                            .clickable { fabExpanded = false }
                    )
                }

                // ── Right stats panel scrim ──
                AnimatedVisibility(
                    visible = rightPanelVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                            .clickable { rightPanelVisible = false }
                    )
                }

                // ── Right stats panel ──
                // DB aggregate — replaces allNotes.sumOf { it.wordCount } in-memory loop
                val totalWords by booksVm.vaultWordCount.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    visible = rightPanelVisible,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200))
                ) {
                CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
                FrostedPanelContent {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.72f)
                        .frostedPanel(hazeState)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { rightPanelVisible = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    HorizontalDivider()
                    StatPanelRow(Icons.Default.Book, "Books", "${allBooks.size}")
                    StatPanelRow(Icons.Filled.StickyNote2, "Notes", "${allNotes.size}")
                    StatPanelRow(Icons.Default.TextFields, "Total words", "$totalWords")
                    StatPanelRow(Icons.Default.FolderOpen, "Folders", "${allFolders.size}")
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    Text(
                        "Quick Actions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    TextButton(
                        onClick = {
                            rightPanelVisible = false
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings")
                    }
                    TextButton(
                        onClick = {
                            rightPanelVisible = false
                            onOpenThemes()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Themes")
                    }
                }
                } // end FrostedPanelContent for right panel
                } // end CompositionLocalProvider(rightOneShotBitmap for right panel)
                } // end AnimatedVisibility for right panel
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        FrostedDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Book") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Book Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = newTitle.trim()
                        if (title.isNotEmpty()) {
                            booksVm.createBook(title) { showCreateDialog = false }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    bookToRename?.let { book ->
        var renameText by remember { mutableStateOf(book.title) }
        FrostedDialog(
            onDismissRequest = { bookToRename = null },
            title = { Text("Rename Book") },
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
                        if (t.isNotEmpty()) {
                            booksVm.renameBook(book.id, t)
                        }
                        bookToRename = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { bookToRename = null }) { Text("Cancel") }
            }
        )
    }

    bookToDelete?.let { book ->
        FrostedDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete Book?") },
            text = { Text("Are you sure you want to delete \"${book.title}\"? All notes in it will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        booksVm.deleteBook(book.id)
                        bookToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("Cancel") }
            }
        )
    }
    } // end CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap)
}

@Composable
private fun DrawerNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = accentColor
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun BooksTabContent(
    books: List<Book>,
    isGridMode: Boolean,
    gridColumns: Int,
    wordCounts: Map<String, Int>,
    fileCounts: Map<String, Int>,
    folderCounts: Map<String, Int>,
    allNotes: List<Note>,
    onOpen: (Book) -> Unit,
    onRename: (Book) -> Unit,
    onChangeCover: (Book) -> Unit,
    onDelete: (Book) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No books yet", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                    Text("Tap + to create your first book", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else if (isGridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.id }, contentType = { "book_card" }) { book ->
                    BookGridCard(
                        book = book,
                        words = wordCounts[book.id] ?: 0,
                        files = fileCounts[book.id] ?: 0,
                        onOpen = { onOpen(book) },
                        onRename = { onRename(book) },
                        onChangeCover = { onChangeCover(book) },
                        onDelete = { onDelete(book) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.id }, contentType = { "book_row" }) { book ->
                    val firstNote = allNotes.firstOrNull { it.bookId == book.id && it.content.isNotBlank() }
                    val introSnippet = firstNote?.content?.take(100)?.replace("\n", " ") ?: "No book intro"
                    BookListRow(
                        book = book,
                        words = wordCounts[book.id] ?: 0,
                        files = fileCounts[book.id] ?: 0,
                        folders = folderCounts[book.id] ?: 0,
                        introSnippet = introSnippet,
                        onOpen = { onOpen(book) },
                        onRename = { onRename(book) },
                        onChangeCover = { onChangeCover(book) },
                        onDelete = { onDelete(book) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BookGridCard(
    book: Book,
    words: Int,
    files: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    // Phase 1: shared element transition setup.
    // sharedTransitionScope is null in @Preview (LocalInspectionMode) — guard accordingly.
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedContentScope = if (LocalInspectionMode.current) null
                               else LocalNavAnimatedContentScope.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (book.coverUri != null) {
                val context = LocalContext.current
                // Phase 1: sharedElement morphs the cover between HomeScreen grid and BookScreen header.
                val coverModifier = if (sharedTransitionScope != null && animatedContentScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.fillMaxSize().sharedElement(
                            sharedContentState = rememberSharedContentState(key = "book_cover_${book.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    }
                } else Modifier.fillMaxSize()
                AsyncImage(
                    // On API < 31, one-shot blur uses View.draw(softwareCanvas).
                    // Hardware bitmaps (Coil's default) crash that call silently,
                    // causing captureOnly to return null and frosted glass to fall back.
                    // Use allowHardware(false) on pre-API-31 so the cover stays as a
                    // software bitmap that the canvas capture can read correctly.
                    model = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        ImageRequest.Builder(context)
                            .data(book.coverUri)
                            .allowHardware(false)
                            .build()
                    } else {
                        book.coverUri
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = if (book.coverUri != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = LocalSolidSurface.current
                ) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { showMenu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Change Cover") }, onClick = { showMenu = false; onChangeCover() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Phase 1: sharedBounds (not sharedElement) because the text style changes
        // between the small grid label here and the large header in BookScreen.
        val titleModifier = if (sharedTransitionScope != null && animatedContentScope != null) {
            with(sharedTransitionScope) {
                Modifier.fillMaxWidth().padding(horizontal = 2.dp).sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "book_title_${book.id}"),
                    animatedVisibilityScope = animatedContentScope
                )
            }
        } else Modifier.fillMaxWidth().padding(horizontal = 2.dp)

        Text(
            text = book.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = titleModifier
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$words words • $files files",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BookListRow(
    book: Book,
    words: Int,
    files: Int,
    folders: Int,
    introSnippet: String,
    onOpen: () -> Unit,
    onRename: (Book) -> Unit,
    onChangeCover: (Book) -> Unit,
    onDelete: (Book) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (book.coverUri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                ImageRequest.Builder(context)
                                    .data(book.coverUri)
                                    .allowHardware(false)
                                    .build()
                            } else {
                                book.coverUri
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tt $words", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$files", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$folders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                        DropdownMenuItem(text = { Text("Open") }, onClick = { showMenu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename(book) })
                        DropdownMenuItem(text = { Text("Change Cover") }, onClick = { showMenu = false; onChangeCover(book) })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete(book) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTabContent(
    allNotes: List<Note>,
    onOpenNote: (Note) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (allNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.StickyNote2,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No notes yet", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                    Text("Tap + Quick Note to create one instantly", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allNotes, key = { "notes_tab_${it.id}" }, contentType = { "note_row" }) { note ->
                    val wordCount = note.wordCount
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(note) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(note.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("$wordCount words", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content.ifBlank { "Empty quick note..." },
                                fontSize = 13.sp,
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

@Composable
private fun SearchResultsView(
    query: String,
    allBooks: List<Book>,
    allNotes: List<Note>,
    onOpenNote: (Note) -> Unit
) {
    if (query.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Type above to search across all notes", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        val matches = remember(query, allNotes) {
            allNotes.filter { n ->
                n.name.contains(query, ignoreCase = true) || n.content.contains(query, ignoreCase = true) || n.folderPath.contains(query, ignoreCase = true)
            }
        }

        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No notes found matching \"$query\"", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(matches, key = { "sr_${it.id}" }) { note ->
                    val bookTitle = allBooks.firstOrNull { it.id == note.bookId }?.title ?: "Vault"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(note) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = highlightMatch(note.name, query),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "$bookTitle / ${note.folderPath}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = highlightMatch(note.content.take(150).replace("\n", " "), query),
                                fontSize = 13.sp,
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

@Composable
private fun highlightMatch(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return buildAnnotatedString { append(text) }
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index == -1) {
                append(text.substring(start))
                break
            }
            if (index > start) {
                append(text.substring(start, index))
            }
            withStyle(style = SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + query.length))
            }
            start = index + query.length
        }
    }
}

@Composable
private fun StatPanelRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 15.sp)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
