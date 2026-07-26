package com.primaloptima.scribe.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.*
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.CoverUtils
import com.primaloptima.scribe.util.ThemeDataStoreRepo
import com.primaloptima.scribe.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenBook: (Book) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSheets: () -> Unit,
    onOpenThemes: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var rightPanelVisible by remember { mutableStateOf(false) }
    val repo = remember { ThemeDataStoreRepo(context) }

    // 0: Books, 1: Notes, 2: Statistics
    var selectedNavTab by remember { mutableIntStateOf(0) }
    var isGridMode by remember { mutableStateOf(true) }
    var gridColumns by remember { mutableIntStateOf(2) }

    LaunchedEffect(Unit) {
        repo.gridColumnsFlow.collectLatest { gridColumns = it }
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    LaunchedEffect(pagerState.currentPage) {
        selectedNavTab = pagerState.currentPage
    }

    // Search state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allBooks by vm.books.observeAsState(emptyList())
    val allNotes by vm.allNotes.observeAsState(emptyList())
    val allFolders by vm.allFolders.observeAsState(emptyList())

    // Book stats computations
    val bookWordCounts = remember(allNotes, allBooks) {
        allBooks.associate { book ->
            book.id to allNotes.filter { it.bookId == book.id }.sumOf { n ->
                n.content.split("\\s+".toRegex()).count { it.isNotBlank() }
            }
        }
    }
    val bookFileCounts = remember(allNotes, allBooks) {
        allBooks.associate { book ->
            book.id to allNotes.count { it.bookId == book.id }
        }
    }
    val bookFolderCounts = remember(allFolders, allBooks) {
        allBooks.associate { book ->
            book.id to allFolders.count { it.bookId == book.id && it.path != "/" }
        }
    }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToChangeCover by remember { mutableStateOf<Book?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val book = bookToChangeCover ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val savedUri = CoverUtils.saveCoverImage(context.applicationContext, book.id, uri)
            vm.updateCover(book.id, savedUri)
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
                // Left-edge swipe → open navigation drawer
                if (drawerState.isClosed && startX < size.width * 0.3f && totalX > threshold) {
                    change.consume()
                    scope.launch { drawerState.open() }
                }
                // Right-edge swipe → open stats panel
                if (!rightPanelVisible && startX > size.width * 0.72f && totalX < -threshold) {
                    change.consume()
                    rightPanelVisible = true
                }
            }
        )
    }

    val hazeState = LocalHazeState.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .frostedPanel(hazeState)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scribe",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Book, contentDescription = null) },
                    label = { Text("World Sheets") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSheets()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    label = { Text("Themes") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenThemes()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.then(swipeGestureModifier),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.frostedBar(hazeState),
                    title = {
                        if (isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search full text, titles...") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (searchQuery.isNotEmpty()) searchQuery = ""
                                        else isSearching = false
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            )
                        } else {
                            val (titleColor, titleModifier) = rememberAdaptiveTextColor(
                                fallback = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Scribe",
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                modifier = titleModifier
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            val (iconColor, iconModifier) = rememberAdaptiveTextColor(
                                fallback = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = iconColor,
                                modifier = iconModifier
                            )
                        }
                    },
                    actions = {
                        if (!isSearching) {
                            IconButton(onClick = { rightPanelVisible = !rightPanelVisible }) {
                                Icon(Icons.Default.Info, contentDescription = "Overview")
                            }
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        if (selectedNavTab == 0 && !isSearching) {
                            if (isGridMode) {
                                IconButton(onClick = {
                                    val nextCols = if (gridColumns == 2) 3 else 2
                                    gridColumns = nextCols
                                    scope.launch { repo.setGridColumns(nextCols) }
                                }) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = "${gridColumns}C",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { isGridMode = !isGridMode }) {
                                Icon(
                                    if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List View"
                                )
                            }
                            var showSortMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Sort Options")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = LocalSolidSurface.current
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date Updated") },
                                    onClick = {
                                        vm.setSortMode(HomeViewModel.SortMode.DATE_UPDATED)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Created") },
                                    onClick = {
                                        vm.setSortMode(HomeViewModel.SortMode.DATE_CREATED)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Title (A-Z)") },
                                    onClick = {
                                        vm.setSortMode(HomeViewModel.SortMode.TITLE_AZ)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .height(60.dp)
                        .frostedBar(hazeState)
                ) {
                    val activeTheme = LocalAppTheme.current
                    val accentColor = activeTheme?.let { parseComposeColor(it.colors.accent, MaterialTheme.colorScheme.primary) }
                        ?: MaterialTheme.colorScheme.primary
                    val navColors = NavigationBarItemDefaults.colors(
                        indicatorColor = accentColor,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = selectedNavTab == 0 && !isSearching,
                        onClick = {
                            selectedNavTab = 0
                            isSearching = false
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        icon = { Icon(Icons.Default.Book, contentDescription = "Books") },
                        label = { Text("Books", fontSize = 10.sp) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = selectedNavTab == 1 && !isSearching,
                        onClick = {
                            selectedNavTab = 1
                            isSearching = false
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        icon = { Icon(Icons.Default.StickyNote2, contentDescription = "Notes") },
                        label = { Text("Notes", fontSize = 10.sp) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = selectedNavTab == 2 && !isSearching,
                        onClick = {
                            selectedNavTab = 2
                            isSearching = false
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistics") },
                        label = { Text("Statistics", fontSize = 10.sp) },
                        colors = navColors
                    )
                }
            },
            floatingActionButton = {
                val fabTheme = LocalAppTheme.current
                val accentClr = fabTheme?.let {
                    parseComposeColor(it.colors.accent, MaterialTheme.colorScheme.primary)
                } ?: MaterialTheme.colorScheme.primary

                AnimatedContent(
                    targetState = selectedNavTab,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
                                fadeIn()) togetherWith (scaleOut() + fadeOut())
                    },
                    label = "fabSwitch"
                ) { tab ->
                    when (tab) {
                        0 -> FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = accentClr,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Book")
                        }
                        1 -> ExtendedFloatingActionButton(
                            onClick = {
                                vm.createQuickNote { note ->
                                    context.startActivity(
                                        Intent(context, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                                            .putExtra(MainActivity.EXTRA_BOOK_ID, note.bookId)
                                    )
                                }
                            },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            text = { Text("Quick Note") },
                            containerColor = accentClr,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        )
                        else -> Box(Modifier)
                    }
                }
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
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                                    .putExtra(MainActivity.EXTRA_BOOK_ID, note.bookId)
                            )
                        }
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                    ) { page ->
                        when (page) {
                            0 -> BooksTabContent(
                                books = vm.sortedBooks(allBooks),
                                isGridMode = isGridMode,
                                gridColumns = gridColumns,
                                wordCounts = bookWordCounts,
                                fileCounts = bookFileCounts,
                                folderCounts = bookFolderCounts,
                                allNotes = allNotes,
                                onOpen = onOpenBook,
                                onRename = { bookToRename = it },
                                onChangeCover = {
                                    bookToChangeCover = it
                                    coverPickerLauncher.launch("image/*")
                                },
                                onDelete = { bookToDelete = it }
                            )
                            1 -> NotesTabContent(
                                allNotes = allNotes,
                                onOpenNote = { note ->
                                    context.startActivity(
                                        Intent(context, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                                            .putExtra(MainActivity.EXTRA_BOOK_ID, note.bookId)
                                    )
                                }
                            )
                            2 -> MainStatisticsTabContent(
                                allBooks = allBooks,
                                allNotes = allNotes,
                                allFolders = allFolders
                            )
                        }
                    }
                }

                // ── Right stats panel — swipe from right edge or tap Info button ──
                AnimatedVisibility(
                    visible = rightPanelVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                            .clickable { rightPanelVisible = false }
                    )
                }
                AnimatedVisibility(
                    visible = rightPanelVisible,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(200)
                    )
                ) {
                    val totalWords = remember(allNotes) {
                        allNotes.sumOf { n -> n.content.split("\\s+".toRegex()).count { it.isNotBlank() } }
                    }
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
                        StatPanelRow(Icons.Default.StickyNote2, "Notes", "${allNotes.size}")
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
                            onClick = { rightPanelVisible = false; onOpenSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Settings")
                        }
                        TextButton(
                            onClick = { rightPanelVisible = false; onOpenThemes() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Themes")
                        }
                    }
                }
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
                            vm.createBook(title) { showCreateDialog = false }
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
                            vm.renameBook(book.id, t)
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
                        vm.deleteBook(book.id)
                        bookToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("Cancel") }
            }
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
                items(books, key = { it.id }) { book ->
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
                items(books, key = { it.id }) { book ->
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

    // ── Idle floating bob ────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // ── Press 3-D tilt ───────────────────────────────────────────────────
    var isPressed by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }

    val targetRotX = if (isPressed) (pressOffset.y - 100f) * 0.04f else 0f
    val targetRotY = if (isPressed) -(pressOffset.x - 80f) * 0.04f else 0f
    val targetScale = if (isPressed) 0.95f else 1f
    val targetElev  = if (isPressed) 2f else 12f

    val rotX  by animateFloatAsState(targetRotX,  spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "rotX")
    val rotY  by animateFloatAsState(targetRotY,  spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "rotY")
    val scale by animateFloatAsState(targetScale, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
    val elev  by animateFloatAsState(targetElev,  tween(200), label = "elev")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY    = floatY
                scaleX          = scale
                scaleY          = scale
                rotationX       = rotX
                rotationY       = rotY
                cameraDistance  = 10f * density
                shadowElevation = elev.dp.toPx()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isPressed = true
                        pressOffset = offset
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onOpen() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(elev.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (book.coverUri != null) {
                AsyncImage(
                    model = book.coverUri,
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

        Text(
            text = book.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
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
                        AsyncImage(
                            model = book.coverUri,
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
                            Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                items(allNotes, key = { "notes_tab_${it.id}" }) { note ->
                    val wordCount = note.content.split("\\s+".toRegex()).count { it.isNotBlank() }
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 15.sp)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
