package com.primaloptima.scribe.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.viewmodel.DashboardViewModel
import com.primaloptima.scribe.viewmodel.StatsViewModel
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.FrostedDialog

import java.text.SimpleDateFormat
import java.util.*

enum class ChartRange(val label: String, val days: Int) {
    WEEK("Week", 7),
    TWO_WEEKS("2 Weeks", 14),
    MONTH("Month", 30),
    YEAR("Year", 365)
}

data class DailyWordEntry(
    val label: String,
    val fullDateStr: String,
    val wordCount: Int,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainStatisticsTabContent(
    dashboardVm: DashboardViewModel,
    statsVm: StatsViewModel,
    allBooks: List<Book>,
    allNotes: List<Note>,
    allFolders: List<Folder>,
    bookWordCounts: Map<String, Int>
) {
    var selectedTopTab by remember { mutableIntStateOf(0) } // 0: Statistics, 1: Wordmap
    val accent = LocalAccentColor.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 0.dp
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTopTab,
                containerColor = Color.Transparent,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTopTab),
                        color = accent
                    )
                }
            ) {
                Tab(
                    selected = selectedTopTab == 0,
                    onClick = { selectedTopTab = 0 },
                    text = {
                        val (tabColor, tabModifier) = rememberAdaptiveTextColor(
                            fallback = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Statistics", fontWeight = FontWeight.Bold, color = tabColor, modifier = tabModifier)
                    }
                )
                Tab(
                    selected = selectedTopTab == 1,
                    onClick = { selectedTopTab = 1 },
                    text = {
                        val (tabColor, tabModifier) = rememberAdaptiveTextColor(
                            fallback = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Wordmap", fontWeight = FontWeight.Bold, color = tabColor, modifier = tabModifier)
                    }
                )
            }
        }

        when (selectedTopTab) {
            0 -> DetailedStatisticsTab(
                dashboardVm = dashboardVm,
                statsVm = statsVm,
                allBooks = allBooks
            )
            1 -> DetailedWordmapTab(
                statsVm = statsVm,
                allBooks = allBooks,
                allNotes = allNotes,
                allFolders = allFolders,
                bookWordCounts = bookWordCounts
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedStatisticsTab(
    dashboardVm: DashboardViewModel,
    statsVm: StatsViewModel,
    allBooks: List<Book>
) {
    var selectedRange by remember { mutableStateOf(ChartRange.WEEK) }
    var showGoalDialog by remember { mutableStateOf(false) }

    // Fix 3 & 7: streaks, todayWords are now live StateFlows (reactive to writing_log).
    // The LaunchedEffect(Unit) that manually called refreshStreaks/refreshTodayWords
    // is no longer needed — data arrives automatically. collectAsStateWithLifecycle()
    // unsubscribes when the tab is off-screen (fix 7).
    val todayWords  by dashboardVm.todayWords.collectAsStateWithLifecycle()
    val streakCount by dashboardVm.currentStreak.collectAsStateWithLifecycle()
    val dailyGoal   by dashboardVm.dailyGoal.collectAsStateWithLifecycle()

    // Fix 4: chart data comes from writing_log via ViewModel, not computed on UI thread
    val chartData by statsVm.chartData.collectAsStateWithLifecycle()

    LaunchedEffect(selectedRange) {
        statsVm.loadChartData(selectedRange)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Chart Card
        val hazeState = LocalHazeState.current
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .frostedCard(hazeState, shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = frostedContainerColor(fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Words Output",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val segAccent = LocalAccentColor.current
                    SingleChoiceSegmentedButtonRow {
                        ChartRange.entries.forEachIndexed { index, range ->
                            SegmentedButton(
                                selected = selectedRange == range,
                                onClick = { selectedRange = range },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ChartRange.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = segAccent,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(range.label, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CombinedBarTrendChart(entries = chartData)
            }
        }

        // Three Stat Summary Cards Side-by-Side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: Today's Words
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .frostedCard(hazeState, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = frostedContainerColor(fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$todayWords",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "written today",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 2: Book Count
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .frostedCard(hazeState, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = frostedContainerColor(fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${allBooks.size}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "books total",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 3: Streak
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .frostedCard(hazeState, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = frostedContainerColor(fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$streakCount",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "day streak",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Daily Goal Progress Section
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .frostedCard(hazeState, shape = RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = frostedContainerColor(fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Goal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "$todayWords / $dailyGoal words",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showGoalDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val progress = if (dailyGoal > 0) (todayWords.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "daily-goal-progress"
                )
                val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val radius = size.height / 2f
                    drawRoundRect(
                        color = outlineVariantColor,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    drawRoundRect(
                        color = primaryColor,
                        size = Size(size.width * animatedProgress, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalOneShotBitmap provides com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap.current
    ) {
        if (showGoalDialog) {
            var inputGoal by remember { mutableStateOf(dailyGoal.toString()) }
            FrostedDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("Set Daily Word Goal") },
                text = {
                    OutlinedTextField(
                        value = inputGoal,
                        onValueChange = { inputGoal = it },
                        label = { Text("Target Words per Day") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val parsed = inputGoal.toIntOrNull() ?: 500
                            dashboardVm.setDailyGoal(parsed)
                            showGoalDialog = false
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun CombinedBarTrendChart(entries: List<DailyWordEntry>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(entries) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val maxVal = remember(entries) {
        (entries.maxOfOrNull { it.wordCount } ?: 1).coerceAtLeast(100)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(entries) {
                    detectTapGestures { offset ->
                        val leftPadding = 40.dp.toPx()
                        val rightPadding = 16.dp.toPx()
                        val chartWidth = size.width - leftPadding - rightPadding
                        val stepX = if (entries.size > 1) chartWidth / (entries.size - 1) else chartWidth

                        val tappedIndex = ((offset.x - leftPadding + stepX / 2) / stepX).toInt()
                            .coerceIn(0, entries.size - 1)
                        selectedIndex = if (selectedIndex == tappedIndex) null else tappedIndex
                    }
                }
        ) {
            val leftPadding = 40.dp.toPx()
            val bottomPadding = 30.dp.toPx()
            val topPadding = 20.dp.toPx()
            val rightPadding = 16.dp.toPx()

            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding

            val count = entries.size
            val stepX = if (count > 1) chartWidth / (count - 1) else chartWidth

            val gridSteps = 4
            for (i in 0..gridSteps) {
                val yPos = topPadding + chartHeight - (chartHeight * i / gridSteps)
                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, yPos),
                    end = Offset(size.width - rightPadding, yPos),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val barWidth = (chartWidth / count * 0.6f).coerceIn(12f, 36f)
            val barPoints = mutableListOf<Offset>()

            entries.forEachIndexed { index, entry ->
                val xCenter = if (count == 1) leftPadding + chartWidth / 2 else leftPadding + index * stepX
                val barHeight = (entry.wordCount.toFloat() / maxVal * chartHeight * animProgress.value)
                val topY = topPadding + chartHeight - barHeight

                barPoints.add(Offset(xCenter, topY))

                val brush = Brush.verticalGradient(
                    colors = listOf(primaryColor, primaryColor.copy(alpha = 0.4f)),
                    startY = topY,
                    endY = topPadding + chartHeight
                )

                val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(xCenter - barWidth / 2, topY),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = cornerRadius
                )

                if (selectedIndex == index) {
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(xCenter - barWidth / 2 - 2f, topY - 2f),
                        size = Size(barWidth + 4f, barHeight + 4f),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            if (barPoints.size > 1) {
                val linePath = Path()
                val glowPath = Path()

                linePath.moveTo(barPoints[0].x, barPoints[0].y)
                glowPath.moveTo(barPoints[0].x, topPadding + chartHeight)
                glowPath.lineTo(barPoints[0].x, barPoints[0].y)

                for (i in 0 until barPoints.size - 1) {
                    val p1 = barPoints[i]
                    val p2 = barPoints[i + 1]
                    val controlX1 = p1.x + (p2.x - p1.x) / 2
                    val controlY1 = p1.y
                    val controlX2 = p1.x + (p2.x - p1.x) / 2
                    val controlY2 = p2.y

                    linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                    glowPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                }

                glowPath.lineTo(barPoints.last().x, topPadding + chartHeight)
                glowPath.close()

                drawPath(
                    path = glowPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.25f * animProgress.value),
                            Color.Transparent
                        ),
                        startY = topPadding,
                        endY = topPadding + chartHeight
                    )
                )

                drawPath(
                    path = linePath,
                    color = tertiaryColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        selectedIndex?.let { idx ->
            if (idx in entries.indices) {
                val entry = entries[idx]
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = primaryColor,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = "${entry.fullDateStr}: ${entry.wordCount} words",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedWordmapTab(
    statsVm: StatsViewModel,
    allBooks: List<Book>,
    allNotes: List<Note>,
    allFolders: List<Folder>,
    bookWordCounts: Map<String, Int>
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    var isDescendingSort by remember { mutableStateOf(true) }

    // Fix 6 & 7: folderWordTotals is now a live StateFlow in StatsViewModel backed by
    // NoteDao.observeWordCountPerFolder(). The LaunchedEffect(Unit) + mutableStateOf
    // pattern is replaced with collectAsStateWithLifecycle() — the Wordmap now
    // updates in real time when notes are written, without requiring a screen re-entry.
    val folderWordTotals by statsVm.folderWordTotals.collectAsStateWithLifecycle()

    // Book word totals from DB — replaces allNotes.filter+sumOf in the Books category
    val bookWordTotals = bookWordCounts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categories = listOf("Files", "Folders", "Books")
            val wordmapAccent = LocalAccentColor.current
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                categories.forEachIndexed { index, title ->
                    SegmentedButton(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = wordmapAccent,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(title, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { isDescendingSort = !isDescendingSort }) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Sort Toggle",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val rankedItems = remember(allBooks, allNotes, allFolders, folderWordTotals, bookWordTotals, selectedCategory, isDescendingSort) {
            computeWordmapItems(allBooks, allNotes, allFolders, folderWordTotals, bookWordTotals, selectedCategory, isDescendingSort)
        }

        if (rankedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Start writing to see your Wordmap grow",
                        style = androidx.compose.ui.text.TextStyle(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val maxWords = (rankedItems.maxOfOrNull { it.wordCount } ?: 1).coerceAtLeast(1)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(rankedItems, key = { index, item -> "${item.id}_$index" }) { index, item ->
                    val ratio = (item.wordCount.toFloat() / maxWords).coerceIn(0.02f, 1f)
                    val rankNumber = index + 1
                    val isTopRank = rankNumber == 1

                    AnimatedRankCard(
                        rank = rankNumber,
                        item = item,
                        ratio = ratio,
                        isTopRank = isTopRank,
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedRankCard(
    rank: Int,
    item: WordmapItem,
    ratio: Float,
    isTopRank: Boolean,
    index: Int
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 40L).coerceAtMost(300L))
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(
            initialOffsetX = { 40 },
            animationSpec = tween(300)
        )
    ) {
        val hazeState = LocalHazeState.current
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .frostedCard(hazeState, shape = RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = frostedContainerColor(
                    fallback = if (isTopRank) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.surfaceContainer
                )
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "#$rank",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${item.wordCount} words",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.breadcrumb.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.breadcrumb,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val gradientBrush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio)
                            .clip(CircleShape)
                            .background(brush = gradientBrush)
                    )
                }
            }
        }
    }
}

data class WordmapItem(
    val id: String,
    val title: String,
    val breadcrumb: String,
    val wordCount: Int,
    val updatedAt: Long
)

private fun computeWordmapItems(
    allBooks: List<Book>,
    allNotes: List<Note>,
    allFolders: List<Folder>,
    folderWordTotals: Map<String, Int>,  // "bookId|folderPath" -> total words, from DB
    bookWordTotals: Map<String, Int>,    // bookId -> total words, from DB (bookWordCounts)
    category: Int,
    isDescending: Boolean
): List<WordmapItem> {
    val items = when (category) {
        0 -> { // Files — use DB word_count column
            allNotes.map { note ->
                val bookTitle = allBooks.firstOrNull { it.id == note.bookId }?.title ?: "Vault"
                val pathStr = if (note.folderPath == "/") bookTitle else "$bookTitle › ${note.folderPath.trim('/')}"
                WordmapItem(
                    id = note.id,
                    title = note.name,
                    breadcrumb = pathStr,
                    wordCount = note.wordCount,
                    updatedAt = note.updatedAt
                )
            }
        }
        1 -> { // Folders — Phase 5: use DB SUM from folderWordTotals instead of in-memory sumOf
            allFolders.map { folder ->
                val bookTitle = allBooks.firstOrNull { it.id == folder.bookId }?.title ?: "Vault"
                val key = "${folder.bookId}|${folder.path}"
                val notesInFolder = allNotes.filter { it.bookId == folder.bookId && it.folderPath == folder.path }
                WordmapItem(
                    id = "${folder.bookId}_${folder.path}",
                    title = if (folder.path == "/") "Root Folder" else folder.path.trim('/'),
                    breadcrumb = "Book: $bookTitle",
                    wordCount = folderWordTotals[key] ?: notesInFolder.sumOf { it.wordCount },
                    updatedAt = notesInFolder.maxOfOrNull { it.updatedAt } ?: 0L
                )
            }
        }
        else -> { // Books — use DB aggregate from bookWordTotals (no in-memory loop)
            allBooks.map { book ->
                val noteCount = allNotes.count { it.bookId == book.id }
                val lastUpdated = allNotes.filter { it.bookId == book.id }
                    .maxOfOrNull { it.updatedAt } ?: 0L
                WordmapItem(
                    id = book.id,
                    title = book.title,
                    breadcrumb = "$noteCount chapters / files",
                    wordCount = bookWordTotals[book.id] ?: 0,
                    updatedAt = lastUpdated
                )
            }
        }
    }

    return if (isDescending) {
        items.sortedByDescending { it.wordCount }
    } else {
        items.sortedByDescending { it.updatedAt }
    }
}
