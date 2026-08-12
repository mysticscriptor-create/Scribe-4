package com.primaloptima.scribe.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSpeedDialFab
import com.primaloptima.scribe.ui.components.SpeedDialItem
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
import androidx.compose.ui.graphics.graphicsLayer
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeContentCard
import com.primaloptima.scribe.ui.components.ScribeProgressBar
import com.primaloptima.scribe.ui.components.ScribeStripCard
import com.primaloptima.scribe.ui.theme.LocalAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    vm: BookViewModel,
    dashboardVm: DashboardViewModel,
    onBack: () -> Unit,
    onOpenNote: (noteId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // One-shot blurred capture for pre-API-31 frosted glass (FABs + dialogs)
    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    var oneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captured by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    // Dialog states declared early so captureForDialog can reference them
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var noteToRename by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Clear dialog bitmap when all dialogs close.
    val anyDialogOpen = showCreateNoteDialog || showCreateFolderDialog ||
            noteToRename != null || noteToDelete != null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // FAB expanded: capture before the sub-FABs animate in (they are inline
        // composables so they render the same frame isFabExpanded becomes true).
        LaunchedEffect(isFabExpanded) {
            if (isFabExpanded && !captured) {
                captured = true
                val raw = BitmapBlur.captureOnly(view)  // Main thread (LaunchedEffect default)
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

    val book by vm.book.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()

    // ── Ongoing project state ─────────────────────────────────────────────────
    val ongoingBookId by dashboardVm.ongoingProjectBookId.collectAsStateWithLifecycle()

    // Bottom Bar tab state inside BookScreen: 0: Write, 1: Statistics
    var selectedTab by remember { mutableIntStateOf(0) }

    // (dialog state vars declared above near capture block)
    var selectedFolderPath by remember { mutableStateOf("/") }

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
            onDragStart = { offset ->
                startX = offset.x
                totalX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                totalX += dragAmount
                if (drawerState.isClosed && startX < size.width * 0.5f && totalX > 36.dp.toPx()) {
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
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = book?.title ?: "Book Folders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Main") },
                    selected = selectedFolderPath == "/",
                    onClick = {
                        selectedFolderPath = "/"
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                folders.filter { it.path != "/" }.forEach { folder ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        label = { Text(folder.path) },
                        selected = selectedFolderPath == folder.path,
                        onClick = {
                            selectedFolderPath = folder.path
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { scope.launch { captureForDialog { showCreateFolderDialog = true } } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Folder")
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.then(swipeGestureModifier),
            contentWindowInsets = WindowInsets.systemBars,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.frostedBar(hazeState),
                    title = {
                        Column {
                            val (titleColor, titleModifier) = rememberAdaptiveTextColor(
                                fallback = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                book?.title ?: "Book",
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                modifier = titleModifier
                            )
                            if (selectedFolderPath != "/") {
                                Text("Folder: $selectedFolderPath", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Folder, contentDescription = "Folders")
                        }
                        // Toggle between Tab Mode and Tree Mode
                        IconButton(onClick = { vm.toggleViewMode() }) {
                            Icon(
                                if (viewMode == BookViewModel.ViewMode.LIST) Icons.Default.ViewStream else Icons.Default.AccountTree,
                                contentDescription = "Toggle Mode"
                            )
                        }
                        var showSortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = LocalSolidSurface.current
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change Book Cover") },
                                onClick = {
                                    showSortMenu = false
                                    coverPickerLauncher.launch("image/*")
                                }
                            )
                            HorizontalDivider()
                            // ── Ongoing project ───────────────────────────────
                            val thisBookId = book?.id
                            val isOngoing = thisBookId != null && ongoingBookId == thisBookId
                            if (isOngoing) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Ongoing Project") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.BookmarkRemove,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        homeVm.clearOngoingProject()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Set as Ongoing Project") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        if (thisBookId != null) {
                                            // Phase 6-E: homeVm.setOngoingProject also inserts
                                            // the /Chapters folder — no manual DB call needed.
                                            homeVm.setOngoingProject(thisBookId)
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sort by Date Updated") },
                                onClick = {
                                    vm.setSortMode(BookViewModel.SortMode.DATE_UPDATED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Date Created") },
                                onClick = {
                                    vm.setSortMode(BookViewModel.SortMode.DATE_CREATED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Title (A-Z)") },
                                onClick = {
                                    vm.setSortMode(BookViewModel.SortMode.TITLE_AZ)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.frostedBar(hazeState)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.EditNote, contentDescription = "Write") },
                        label = { Text("Write") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistics") },
                        label = { Text("Statistics") }
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    ScribeSpeedDialFab(
                        items = listOf(
                            SpeedDialItem(
                                icon = Icons.Default.Description,
                                label = "New Text File",
                                onClick = {
                                    scope.launch { captureForDialog { showCreateNoteDialog = true } }
                                }
                            ),
                            SpeedDialItem(
                                icon = Icons.Default.CreateNewFolder,
                                label = "New Folder",
                                onClick = {
                                    scope.launch { captureForDialog { showCreateFolderDialog = true } }
                                }
                            )
                        ),
                        expanded = isFabExpanded,
                        onExpandedChange = { isFabExpanded = it }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isFabExpanded) isFabExpanded = false
                    }
            ) {
                if (selectedTab == 0) {
                    val allFolderPaths = remember(folders) {
                        (listOf("/") + folders.map { it.path }).distinct()
                    }

                    if (viewMode == BookViewModel.ViewMode.LIST) {
                        // TAB MODE using HorizontalPager & ScrollableTabRow
                        val pagerState = rememberPagerState(pageCount = { allFolderPaths.size })

                        LaunchedEffect(pagerState.currentPage) {
                            selectedFolderPath = allFolderPaths[pagerState.currentPage]
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                edgePadding = 16.dp
                            ) {
                                allFolderPaths.forEachIndexed { index, path ->
                                    val label = if (path == "/") "Main" else path.removePrefix("/")
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        },
                                        text = { Text(label, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val currentPath = allFolderPaths[page]
                                val pageNotes = notes.filter { it.folderPath == currentPath }

                                if (pageNotes.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Outlined.Description,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            val displayPathName = if (currentPath == "/") "Main" else currentPath
                                            Text(
                                                "No notes in $displayPathName",
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(pageNotes, key = { note -> "${sortMode}_${note.id}" }) { note ->
                                            NoteListRow(
                                                note = note,
                                                onClick = { onOpenNote(note.id) },
                                                onOpenFloat = { onOpenNote(note.id) },
                                                onRename = { scope.launch { captureForDialog { noteToRename = note } } },
                                                onDuplicate = { vm.duplicateNote(note.id) },
                                                onDelete = { scope.launch { captureForDialog { noteToDelete = note } } }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TREE MODE: Expandable/Collapsible tree
                        TreeModeView(
                            notes = notes,
                            folders = folders,
                            onNoteClick = { note -> onOpenNote(note.id) },
                            onOpenFloat = { note -> onOpenNote(note.id) },
                            onRename = { note -> scope.launch { captureForDialog { noteToRename = note } } },
                            onDuplicate = { note -> vm.duplicateNote(note.id) },
                            onDelete = { note -> scope.launch { captureForDialog { noteToDelete = note } } }
                        )
                    }
                } else {
                    BookStatisticsTab(notes = notes, bookTitle = book?.title ?: "Book")
                }
            }
        }
    }

    // Dialogs — wrapped in their own provider so dialogOneShotBitmap (not the FAB
    // bitmap) is used, preventing the FAB collapse from clearing the blur before
    // the dialog renders on Android 10.
    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
    if (showCreateNoteDialog) {
        var noteTitle by remember { mutableStateOf("") }
        FrostedDialog(
            onDismissRequest = { showCreateNoteDialog = false },
            title = { Text("New Note") },
            text = {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Note Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = noteTitle.trim()
                        if (t.isNotEmpty()) {
                            vm.createNote(t, selectedFolderPath) { id ->
                                showCreateNoteDialog = false
                                onOpenNote(id)
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

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        FrostedDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name (e.g. Chapter 1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val f = folderName.trim()
                        if (f.isNotEmpty()) {
                            val path = if (selectedFolderPath == "/") "/$f" else "$selectedFolderPath/$f"
                            vm.createFolder(path)
                            showCreateFolderDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    noteToRename?.let { note ->
        var renameText by remember { mutableStateOf(note.name) }
        FrostedDialog(
            onDismissRequest = { noteToRename = null },
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
                        if (t.isNotEmpty()) {
                            vm.renameNote(note.id, t)
                        }
                        noteToRename = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { noteToRename = null }) { Text("Cancel") }
            }
        )
    }

    noteToDelete?.let { note ->
        FrostedDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete \"${note.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteNote(note.id)
                        noteToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            }
        )
    }
    } // end CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap)
    } // end CompositionLocalProvider(LocalOneShotBitmap provides oneShotBitmap)
}

@Composable
private fun TreeModeView(
    notes: List<Note>,
    folders: List<Folder>,
    onNoteClick: (Note) -> Unit,
    onOpenFloat: (Note) -> Unit,
    onRename: (Note) -> Unit,
    onDuplicate: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    val folderPaths = remember(folders) {
        folders.map { it.path }.filter { it != "/" }.sorted()
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Root Notes
        val rootNotes = notes.filter { it.folderPath == "/" }
        if (rootNotes.isNotEmpty()) {
            items(rootNotes, key = { "root_${it.id}" }) { note ->
                NoteListRow(
                    note = note,
                    onClick = { onNoteClick(note) },
                    onOpenFloat = { onOpenFloat(note) },
                    onRename = { onRename(note) },
                    onDuplicate = { onDuplicate(note) },
                    onDelete = { onDelete(note) }
                )
            }
        }

        // Subfolders
        folderPaths.forEach { fPath ->
            val isExpanded = expandedFolders[fPath] ?: true
            item(key = "folder_$fPath") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedFolders[fPath] = !isExpanded }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowDown
                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        fPath,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExpanded) {
                val fNotes = notes.filter { it.folderPath == fPath }
                items(fNotes, key = { "fn_${it.id}" }) { note ->
                    NoteListRow(
                        note = note,
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { onNoteClick(note) },
                        onOpenFloat = { onOpenFloat(note) },
                        onRename = { onRename(note) },
                        onDuplicate = { onDuplicate(note) },
                        onDelete = { onDelete(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookStatisticsTab(notes: List<Note>, bookTitle: String) {
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val outline     = MaterialTheme.colorScheme.outline

    // Phase 6-E: use DB-backed word_count column — no Regex in UI layer
    val totalWords = remember(notes) {
        notes.sumOf { it.wordCount }
    }
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

        // ── Stat summary card ──────────────────────────────────────────────
        ScribeContentCard(title = "Statistics for \"$bookTitle\"") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ScribeCard(
                    modifier     = Modifier.weight(1f),
                    cornerRadius = ScribeCardTokens.RadiusMedium,
                    accentBorder = true,
                    shine        = true
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text       = "${notes.size}",
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color      = onSurface
                        )
                        Text(
                            text     = "Total Files",
                            fontSize = 12.sp,
                            color    = outline
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                ScribeCard(
                    modifier     = Modifier.weight(1f),
                    cornerRadius = ScribeCardTokens.RadiusMedium,
                    accentBorder = true,
                    shine        = true
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text       = "$totalWords",
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color      = accentColor
                        )
                        Text(
                            text     = "Total Words",
                            fontSize = 12.sp,
                            color    = outline
                        )
                    }
                }
            }
        }

        // ── Word count ranking card ────────────────────────────────────────
        ScribeContentCard(title = "Files Word Count Ranking") {
            if (scoredNotes.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No files in this book", color = outline)
                }
            } else {
                scoredNotes.forEachIndexed { index, (note, count) ->
                    val ratio = (count / maxWords).coerceIn(0.05f, 1.0f)

                    if (index > 0) {
                        Box(
                            modifier         = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.8.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                accentColor.copy(alpha = 0.25f),
                                                accentColor.copy(alpha = 0.25f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { rotationZ = 45f }
                                    .background(accentColor.copy(alpha = 0.45f))
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            accentColor.copy(alpha = if (index == 0) 0.22f else 0.10f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = "${index + 1}",
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = accentColor
                                    )
                                }
                                Text(
                                    text       = note.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 14.sp,
                                    color      = onSurface,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text       = "$count words",
                                fontSize   = 12.sp,
                                color      = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text     = "Folder: ${note.folderPath}",
                            fontSize = 11.sp,
                            color    = outline
                        )

                        ScribeProgressBar(
                            progress = ratio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteListRow(
    note: Note,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOpenFloat: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface

    // Phase 6-E: use DB-backed word_count column — no Regex in UI layer
    val wordCount = note.wordCount
    val previewText = remember(note.content) {
        val lines = note.content.lineSequence().filter { it.isNotBlank() }.take(3).toList()
        if (lines.isEmpty()) null else lines.joinToString("\n")
    }
    val createdStr = remember(note.createdAt) {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(note.createdAt))
    }
    val modifiedStr = remember(note.updatedAt) {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
    }

    ScribeStripCard(
        title           = note.name,
        modifier        = modifier.fillMaxWidth(),
        subtitle        = "$wordCount words · ${note.folderPath}",
        preview         = previewText ?: "No text content",
        previewMaxLines = 3,
        footerLines     = listOf(
            "Created: $createdStr",
            "Modified: $modifiedStr"
        ),
        leading = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(ScribeCardTokens.RadiusTiny))
                    .background(accentColor.copy(alpha = 0.10f))
                    .border(
                        width = 0.8.dp,
                        color = accentColor.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(ScribeCardTokens.RadiusTiny)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                    tint               = accentColor.copy(alpha = 0.85f)
                )
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint               = onSurface.copy(alpha = 0.60f)
                    )
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor   = LocalSolidSurface.current
                ) {
                    DropdownMenuItem(
                        text    = { Text("Open") },
                        onClick = { showMenu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Open in Floating Window") },
                        onClick = { showMenu = false; onOpenFloat() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Rename") },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Duplicate") },
                        onClick = { showMenu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text    = { Text("Delete") },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        },
        onClick    = onClick,
        wrapInCard = true
    )
}
