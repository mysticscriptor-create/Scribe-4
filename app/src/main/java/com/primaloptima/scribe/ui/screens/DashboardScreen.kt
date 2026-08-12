package com.primaloptima.scribe.ui.screens

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.airbnb.lottie.compose.*
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.components.*
import com.primaloptima.scribe.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DashboardTabContent(
    vm: DashboardViewModel,
    allBooks: List<Book>,
    bookWordCounts: Map<String, Int>,
    onOpenNote: (noteId: String, bookId: String) -> Unit,
    onOpenBook: (Book) -> Unit,
    onGoToStats: () -> Unit,
    onGoToBooks: () -> Unit,
    onOpenSheets: () -> Unit
) {
    // Fix 7: collectAsStateWithLifecycle() unsubscribes when the UI is not visible,
    // reducing DB→Flow→UI cycles while the app is in the background.
    // Pairs correctly with SharingStarted.WhileSubscribed(5000) in the ViewModel.
    val ongoingBookId   by vm.ongoingProjectBookId.collectAsStateWithLifecycle()
    val chapters        by vm.ongoingProjectChapters.collectAsStateWithLifecycle()
    val currentStreak   by vm.currentStreak.collectAsStateWithLifecycle()
    val todayWords      by vm.todayWords.collectAsStateWithLifecycle()
    val dailyGoalPref   by vm.dailyGoal.collectAsStateWithLifecycle()
    val bookGoal        by vm.currentBookGoal.collectAsStateWithLifecycle()
    val weekData        by vm.weeklyWordData.collectAsStateWithLifecycle()

    val ongoingBook = remember(ongoingBookId, allBooks) {
        ongoingBookId?.let { id -> allBooks.firstOrNull { it.id == id } }
    }

    // Phase 3: use bookWordCounts (SQL SUM) instead of ongoingProjectAllNotes.sumOf
    val totalProjectWords = remember(ongoingBookId, bookWordCounts) {
        ongoingBookId?.let { bookWordCounts[it] } ?: 0
    }

    val dailyGoal   = if (ongoingBookId != null) bookGoal.dailyWords else dailyGoalPref
    val totalTarget = if (ongoingBookId != null) bookGoal.totalTarget else 120_000

    var showBookPicker  by remember { mutableStateOf(false) }
    var showGoalDialog  by remember { mutableStateOf(false) }

    val accentColor = LocalAccentColor.current

    // Fix 3: LaunchedEffect(Unit) removed — weeklyWordData, todayWords, and
    // currentStreak is a live StateFlow in DashboardViewModel that updates
    // automatically whenever writing_log changes. No manual load on screen entry.

    CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Greeting ──────────────────────────────────────────────────────
            item {
                DashboardGreeting(streak = currentStreak, accentColor = accentColor)
            }

            // ── Current Project card ──────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(10.dp))
                if (ongoingBook == null) {
                    NoProjectCard(
                        accentColor  = accentColor,
                        onSetProject = { showBookPicker = true }
                    )
                } else {
                    CurrentProjectCard(
                        book              = ongoingBook,
                        chapters          = chapters,
                        totalWords        = totalProjectWords,
                        totalTarget       = totalTarget,
                        accentColor       = accentColor,
                        onContinueWriting = {
                            val latest = chapters.firstOrNull()
                            if (latest != null) onOpenNote(latest.id, latest.bookId)
                        },
                        onNewChapter = {
                            vm.createChapter { note -> onOpenNote(note.id, note.bookId) }
                        },
                        onOpenBook = { onOpenBook(ongoingBook) }
                    )
                }
            }

            // ── Quick Actions (inside card) ───────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(14.dp))
                QuickActionsCard(
                    chapters     = chapters,
                    accentColor  = accentColor,
                    onOpenNote   = onOpenNote,
                    onOpenBook   = { ongoingBook?.let { onOpenBook(it) } },
                    onGoToStats  = onGoToStats,
                    onOpenSheets = onOpenSheets,
                    onGoToBooks  = onGoToBooks
                )
            }

            // ── Writing Progress ──────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(14.dp))
                WritingProgressCard(
                    todayWords  = todayWords,
                    dailyGoal   = dailyGoal,
                    weekData    = weekData,
                    streak      = currentStreak,
                    totalWords  = totalProjectWords,
                    totalTarget = totalTarget,
                    accentColor = accentColor,
                    onGoToStats = { showGoalDialog = true }
                )
            }

            // ── Recent Chapters ───────────────────────────────────────────────
            if (ongoingBook != null && chapters.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    RecentChaptersCard(
                        chapters    = chapters.take(3),
                        accentColor = accentColor,
                        onSeeAll    = { ongoingBook?.let { onOpenBook(it) } },
                        onOpenNote  = onOpenNote
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // ── Book picker bottom sheet ──────────────────────────────────────────────
    if (showBookPicker) {
        BookPickerSheet(
            books            = allBooks,
            currentOngoingId = ongoingBookId,
            accentColor      = accentColor,
            onSelect = { book ->
                vm.setOngoingProject(book.id)
                showBookPicker = false
            },
            onDismiss = { showBookPicker = false }
        )
    }

    // ── Goal setting bottom sheet ─────────────────────────────────────────────
    if (showGoalDialog) {
        GoalSettingSheet(
            bookId      = ongoingBookId,
            currentGoal = bookGoal,
            vm          = vm,
            accentColor = accentColor,
            onDismiss   = { showGoalDialog = false }
        )
    }
}

// ── Greeting ──────────────────────────────────────────────────────────────────

@Composable
private fun DashboardGreeting(streak: Int, accentColor: Color) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    val subtitle = when {
        hour < 12 -> "The blank page is waiting for you."
        hour < 17 -> "Every word shapes your story."
        else      -> "Great stories begin with one more sentence."
    }

    val (textColor, textMod) = rememberAdaptiveTextColor(
        fallback = MaterialTheme.colorScheme.onSurface
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = greeting,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = textColor,
                    modifier   = textMod
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint     = accentColor
                )
            }
            Text(
                text     = subtitle,
                fontSize = 13.sp,
                color    = textColor.copy(alpha = 0.6f),
                modifier = textMod
            )
        }

        if (streak > 0) {
            ScribePill(
                text  = "🔥 $streak ${if (streak == 1) "Day" else "Days"}",
                color = accentColor
            )
        }
    }
}

// ── No Project empty state ────────────────────────────────────────────────────

@Composable
private fun NoProjectCard(
    accentColor: Color,
    onSetProject: () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("shelf_empty.json")
    )

    ScribeCard(
        modifier     = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = ScribeCardTokens.RadiusLarge,
        onClick      = null
    ) {
        Column(
            modifier                = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(10.dp)
        ) {
            LottieAnimation(
                composition = composition,
                iterations  = LottieConstants.IterateForever,
                modifier    = Modifier.size(80.dp)
            )
            Text(
                "No ongoing project",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Set a book as your ongoing project to track chapters, word count, and daily progress here.",
                fontSize  = 13.sp,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onSetProject,
                colors  = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape   = RoundedCornerShape(ScribeCardTokens.RadiusSmall)
            ) {
                Icon(
                    Icons.Outlined.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Ongoing Project", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Current Project card ──────────────────────────────────────────────────────

private val PLACEHOLDER_TAGS = listOf("Dark Fantasy", "Adventure", "Romance")

@Composable
private fun CurrentProjectCard(
    book: Book,
    chapters: List<Note>,
    totalWords: Int,
    totalTarget: Int,
    accentColor: Color,
    onContinueWriting: () -> Unit,
    onNewChapter: () -> Unit,
    onOpenBook: () -> Unit
) {
    val context          = LocalContext.current
    val lastChapter      = chapters.firstOrNull()
    val lastTimestamp    = lastChapter?.let { formatRelativeTime(it.updatedAt) }
    val currentChapterN  = chapters.indexOfFirst { it.id == lastChapter?.id }
        .let { if (it >= 0) it + 1 else chapters.size }
    val chapterCount     = chapters.size
    val progressFraction = (totalWords.toFloat() / totalTarget).coerceIn(0f, 1f)

    ScribeCard(
        modifier     = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = ScribeCardTokens.RadiusLarge
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Cover + meta ──────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {

                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .heightIn(min = 165.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(
                            topStart    = ScribeCardTokens.RadiusLarge,
                            bottomStart = ScribeCardTokens.RadiusLarge
                        ))
                        .clickable { onOpenBook() }
                ) {
                    if (book.coverUri != null) {
                        AsyncImage(
                            model = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                ImageRequest.Builder(context)
                                    .data(book.coverUri)
                                    .allowHardware(false)
                                    .build()
                            } else book.coverUri,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint     = accentColor
                            )
                        }
                    }
                }

                // Right column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        ScribeSectionLabel(text = "Current Project")
                        Text(
                            text       = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            color      = MaterialTheme.colorScheme.onSurface,
                            modifier   = Modifier.basicMarquee()
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.basicMarquee()
                    ) {
                        PLACEHOLDER_TAGS.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor.copy(alpha = 0.09f))
                                    .border(0.5.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text       = tag,
                                    fontSize   = 11.sp,
                                    lineHeight = 11.sp,
                                    color      = accentColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.TextSnippet,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                "${formatWordCount(totalWords)} / ${formatWordCount(totalTarget)} words",
                                fontSize   = 15.sp,
                                lineHeight = 15.sp,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ScribeProgressBar(
                                progress = progressFraction,
                                modifier = Modifier.weight(1f).height(6.dp)
                            )
                            Text(
                                "${(progressFraction * 100).toInt()}%",
                                fontSize   = 13.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = accentColor
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (chapterCount > 0) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.MenuBook,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentColor.copy(alpha = 0.70f)
                                )
                                Text(
                                    "Ch. $currentChapterN of $chapterCount",
                                    fontSize   = 15.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = accentColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                        if (lastChapter != null && lastTimestamp != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text       = "${lastChapter.name}  ·  $lastTimestamp",
                                    fontSize   = 15.sp,
                                    lineHeight = 15.sp,
                                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            ScribeCardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = onContinueWriting,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape    = RoundedCornerShape(ScribeCardTokens.RadiusSmall),
                    enabled  = chapters.isNotEmpty()
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (chapters.isEmpty()) "No chapters yet" else "Continue Writing",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1
                    )
                }
                OutlinedButton(
                    onClick  = onNewChapter,
                    modifier = Modifier.height(42.dp),
                    shape    = RoundedCornerShape(ScribeCardTokens.RadiusSmall),
                    border   = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Add, "New Chapter",
                        modifier = Modifier.size(15.dp), tint = accentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 13.sp, color = accentColor,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Quick Actions card ────────────────────────────────────────────────────────

@Composable
private fun QuickActionsCard(
    chapters: List<Note>,
    accentColor: Color,
    onOpenNote: (noteId: String, bookId: String) -> Unit,
    onOpenBook: () -> Unit,
    onGoToStats: () -> Unit,
    onOpenSheets: () -> Unit,
    onGoToBooks: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Outlined.Edit,               "Write",      {
            val latest = chapters.firstOrNull()
            if (latest != null) onOpenNote(latest.id, latest.bookId) else onGoToBooks()
        }),
        Triple(Icons.Outlined.AccountTree,        "Outline",    { onOpenBook() }),
        Triple(Icons.Outlined.People,             "Characters", { onOpenBook() }),
        Triple(Icons.Outlined.Explore,            "World",      { onOpenSheets() }),
        Triple(Icons.Outlined.BarChart,           "Stats",      { onGoToStats() })
    )

    ScribeContentCard(
        title    = "Quick Actions",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEachIndexed { index, (icon, label, action) ->
                UniformActionTile(
                    icon        = icon,
                    label       = label,
                    onClick     = action,
                    accentColor = accentColor,
                    isFirst     = index == 0,
                    modifier    = Modifier
                        .weight(1f)
                        .aspectRatio(0.85f)
                )
            }
        }
    }
}

@Composable
private fun UniformActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    val hazeState    = LocalHazeState.current
    val hasBgImage   = localHasBgImage()
    val solidSurface = LocalSolidSurface.current
    val shape        = RoundedCornerShape(ScribeCardTokens.RadiusSmall)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label         = "tile-press"
    )

    val bgColor = if (isFirst)
        accentColor.copy(alpha = 0.14f)
    else
        frostedContainerColor(
            fallback = if (hasBgImage) solidSurface.copy(alpha = 0.70f)
                       else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bgColor)
            .border(
                width = 0.6.dp,
                color = if (isFirst) accentColor.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = shape
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                modifier           = Modifier.size(20.dp),
                tint               = if (isFirst) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text       = label,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (isFirst) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                maxLines   = 1
            )
        }
    }
}

// ── Writing Progress card ─────────────────────────────────────────────────────

@Composable
private fun WritingProgressCard(
    todayWords: Int,
    dailyGoal: Int,
    weekData: List<Triple<String, Int, Boolean>>,
    streak: Int,
    totalWords: Int,
    totalTarget: Int,
    accentColor: Color,
    onGoToStats: () -> Unit
) {
    val weekTotal     = weekData.sumOf { it.second }
    val weekGoal      = dailyGoal * 7
    val cal           = Calendar.getInstance()
    val daysInMonth   = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthGoal     = dailyGoal * daysInMonth
    val monthWritten  = (weekTotal * (daysInMonth / 7f)).toInt()
    val monthProgress = if (monthGoal > 0) (monthWritten.toFloat() / monthGoal).coerceIn(0f, 1f) else 0f

    val milestones    = listOf(1_000, 5_000, 10_000, 25_000, 50_000, 75_000, 100_000, 120_000)
    val nextMilestone = milestones.firstOrNull { it > totalWords } ?: (totalWords + 10_000)
    val wordsToGo     = nextMilestone - totalWords

    val streakDots    = weekData.map { (label, count, _) -> Pair(label, count > 0) }
    val goalMet       = dailyGoal > 0 && todayWords >= dailyGoal
    val onSurface     = MaterialTheme.colorScheme.onSurface
    val outline       = MaterialTheme.colorScheme.outline

    ScribeContentCard(
        title        = "Your Progress",
        modifier     = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        actionLabel  = "Set Goal ›",
        onAction     = onGoToStats
    ) {
        // ── Three stat columns ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScribeStatColumn(
                modifier  = Modifier.weight(1f),
                label     = "Today",
                value     = formatWordCount(todayWords),
                subLabel  = "/ ${formatWordCount(dailyGoal)} words",
                icon      = Icons.Outlined.Edit,
                badge     = if (goalMet) "Goal reached! 🎉" else null
            )

            ScribeVerticalDivider(modifier = Modifier.align(Alignment.CenterVertically))

            ScribeStatColumn(
                modifier  = Modifier.weight(1f),
                label     = "Streak",
                value     = "$streak",
                subLabel  = "days",
                icon      = Icons.Outlined.LocalFireDepartment,
                iconTint  = Color(0xFFFF6B00),
                extra     = {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        streakDots.forEach { (_, hasWords) ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasWords) accentColor
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }
            )

            ScribeVerticalDivider(modifier = Modifier.align(Alignment.CenterVertically))

            ScribeStatColumn(
                modifier  = Modifier.weight(1f),
                label     = "Monthly",
                value     = "${(monthProgress * 100).toInt()}%",
                subLabel  = "${formatWordCount(monthWritten)} written",
                icon      = Icons.Outlined.TrackChanges,
                extra     = {
                    ScribeProgressBar(
                        progress = monthProgress,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .padding(top = 2.dp)
                    )
                }
            )
        }

        ScribeCardDivider()

        // ── Premium week bar chart ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "This Week",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "${formatWordCount(weekTotal)} / ${formatWordCount(weekGoal)} words",
                    fontSize = 11.sp,
                    color    = onSurface.copy(alpha = 0.45f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            PremiumWeekBarChart(
                weekData    = weekData,
                accentColor = accentColor,
                modifier    = Modifier.fillMaxWidth().height(80.dp)
            )
        }

        ScribeCardDivider()

        // ── Next Milestone ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGoToStats() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.StarBorder, null,
                    modifier = Modifier.size(14.dp),
                    tint     = onSurface.copy(alpha = 0.5f))
                Text(
                    "Next Milestone",
                    fontSize   = 12.sp,
                    color      = onSurface.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${formatWordCount(wordsToGo)} words to ${formatWordCount(nextMilestone)}",
                    fontSize   = 12.sp,
                    color      = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Default.ChevronRight, null,
                    modifier = Modifier.size(14.dp), tint = accentColor)
            }
        }

        // ── Motivational quote ────────────────────────────────────────────────
        if (todayWords == 0) {
            ScribeCardDivider()
            Text(
                "\" A page a day builds a world. \"",
                fontSize  = 12.sp,
                fontStyle = FontStyle.Italic,
                color     = onSurface.copy(alpha = 0.38f),
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

// ── Premium Week Bar Chart ────────────────────────────────────────────────────

@Composable
private fun PremiumWeekBarChart(
    weekData: List<Triple<String, Int, Boolean>>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal      = remember(weekData) { (weekData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1) }
    val trackColor  = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    val labelColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val todayLabelColor = accentColor

    val animatedFractions = weekData.mapIndexed { i, (_, count, _) ->
        val anim by animateFloatAsState(
            targetValue   = count.toFloat() / maxVal,
            animationSpec = tween(500 + i * 60, easing = FastOutSlowInEasing),
            label         = "bar-$i"
        )
        anim
    }

    val labelHeightDp = 16.dp

    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val labelHeightPx = with(density) { labelHeightDp.toPx() }
        val dotSizePx = with(density) { 4.dp.toPx() }
        val barGapPx  = with(density) { 7.dp.toPx() }
        val cornerPx  = with(density) { 6.dp.toPx() }

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount     = weekData.size
            val chartH       = size.height - labelHeightPx
            val totalGap     = barGapPx * (barCount - 1)
            val barW         = (size.width - totalGap) / barCount

            weekData.forEachIndexed { i, (label, _, isToday) ->
                val left      = i * (barW + barGapPx)
                val fraction  = animatedFractions[i]
                val barH      = (fraction * chartH).coerceAtLeast(4f)
                val top       = chartH - barH

                drawRoundRect(
                    color        = trackColor,
                    topLeft      = Offset(left, 0f),
                    size         = Size(barW, chartH),
                    cornerRadius = CornerRadius(cornerPx)
                )

                val barGradient = Brush.verticalGradient(
                    colors = listOf(
                        if (isToday) accentColor else accentColor.copy(alpha = 0.75f),
                        accentColor.copy(alpha = if (isToday) 0.30f else 0.15f)
                    ),
                    startY = top,
                    endY   = chartH
                )
                drawRoundRect(
                    brush        = barGradient,
                    topLeft      = Offset(left, top),
                    size         = Size(barW, barH),
                    cornerRadius = CornerRadius(cornerPx)
                )

                if (isToday && fraction > 0.01f) {
                    drawCircle(
                        color  = accentColor,
                        radius = dotSizePx,
                        center = Offset(left + barW / 2f, top - dotSizePx - 2f)
                    )
                    drawCircle(
                        color  = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        radius = dotSizePx * 0.45f,
                        center = Offset(left + barW / 2f, top - dotSizePx - 2f)
                    )
                }

                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    left + barW / 2f,
                    size.height - labelHeightPx * 0.08f,
                    android.graphics.Paint().apply {
                        color       = if (isToday) todayLabelColor.toArgb()
                                      else labelColor.toArgb()
                        textSize    = labelHeightPx * 0.68f
                        textAlign   = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        if (isToday) isFakeBoldText = true
                    }
                )
            }

            drawLine(
                color       = trackColor.copy(alpha = 0.5f),
                start       = Offset(0f, chartH),
                end         = Offset(size.width, chartH),
                strokeWidth = 1f
            )
        }
    }
}

// ── Recent Chapters card ──────────────────────────────────────────────────────

@Composable
private fun RecentChaptersCard(
    chapters: List<Note>,
    accentColor: Color,
    onSeeAll: () -> Unit,
    onOpenNote: (noteId: String, bookId: String) -> Unit
) {
    ScribeContentCard(
        title       = "Continue where you left off",
        modifier    = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = ScribeCardTokens.RadiusMedium,
        actionLabel = "See All ›",
        onAction    = onSeeAll
    ) {
        chapters.forEachIndexed { index, chapter ->
            val wordCount = chapter.wordCount
            val previewLine = remember(chapter.content) {
                chapter.content.lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()?.take(70) ?: ""
            }

            CompactChapterRow(
                chapter     = chapter,
                wordCount   = wordCount,
                previewLine = previewLine,
                accentColor = accentColor,
                showDivider = index < chapters.lastIndex,
                isMostRecent = index == 0,
                onClick     = { onOpenNote(chapter.id, chapter.bookId) }
            )
        }
    }
}

@Composable
private fun CompactChapterRow(
    chapter: Note,
    wordCount: Int,
    previewLine: String,
    accentColor: Color,
    showDivider: Boolean,
    isMostRecent: Boolean,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline   = MaterialTheme.colorScheme.outline

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(ScribeCardTokens.RadiusTiny))
                    .background(accentColor.copy(alpha = 0.09f))
                    .border(0.6.dp, accentColor.copy(alpha = 0.16f),
                        RoundedCornerShape(ScribeCardTokens.RadiusTiny)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint     = accentColor.copy(alpha = 0.80f)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text       = chapter.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text     = "${formatRelativeTime(chapter.updatedAt)}  ·  $wordCount words",
                    fontSize = 11.sp,
                    color    = onSurface.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (previewLine.isNotEmpty()) {
                    Text(
                        text      = previewLine,
                        fontSize  = 11.sp,
                        color     = onSurface.copy(alpha = 0.45f),
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            if (isMostRecent) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.height(30.dp),
                    shape    = RoundedCornerShape(ScribeCardTokens.RadiusTiny),
                    border   = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                ) {
                    Text(
                        "Continue",
                        fontSize   = 10.sp,
                        color      = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 14.dp),
                color    = outline.copy(alpha = 0.07f)
            )
        }
    }
}

// ── Goal Setting Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSettingSheet(
    bookId: String?,
    currentGoal: com.primaloptima.scribe.util.BookGoal,
    vm: DashboardViewModel,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val solidSurface = LocalSolidSurface.current

    var dailyWords   by remember { mutableStateOf(currentGoal.dailyWords.toFloat()) }
    var totalTarget  by remember { mutableStateOf(currentGoal.totalTarget) }

    val targetOptions = listOf(50_000, 80_000, 100_000, 120_000, 200_000)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = solidSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Set Writing Goals",
                fontWeight = FontWeight.Bold,
                fontSize   = 19.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Daily Goal",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ScribeCardTokens.RadiusTiny))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${dailyWords.toInt()} words",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = accentColor
                        )
                    }
                }
                Slider(
                    value         = dailyWords,
                    onValueChange = { dailyWords = it },
                    valueRange    = 100f..3000f,
                    steps         = 28,
                    colors        = SliderDefaults.colors(
                        thumbColor           = accentColor,
                        activeTrackColor     = accentColor,
                        inactiveTrackColor   = accentColor.copy(alpha = 0.20f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("3,000", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Book Target",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    targetOptions.forEach { option ->
                        val isSelected = totalTarget == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(ScribeCardTokens.RadiusTiny))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) accentColor.copy(alpha = 0.40f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    RoundedCornerShape(ScribeCardTokens.RadiusTiny)
                                )
                                .clickable { totalTarget = option }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${option / 1000}k",
                                fontSize   = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color      = if (isSelected) accentColor
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val goal = com.primaloptima.scribe.util.BookGoal(
                        dailyWords    = dailyWords.toInt(),
                        totalTarget   = totalTarget,
                        chapterTarget = currentGoal.chapterTarget
                    )
                    if (bookId != null) {
                        vm.saveBookGoal(bookId, goal)
                    } else {
                        vm.setDailyGoal(dailyWords.toInt())
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape    = RoundedCornerShape(ScribeCardTokens.RadiusSmall)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Goals", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Book picker bottom sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookPickerSheet(
    books: List<Book>,
    currentOngoingId: String?,
    accentColor: Color,
    onSelect: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val solidSurface = LocalSolidSurface.current
    val context      = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = solidSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Set Ongoing Project",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "A Chapters folder will be created in the selected book.",
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (books.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No books yet. Create a book first.",
                        color = MaterialTheme.colorScheme.outline)
                }
            } else {
                books.forEach { book ->
                    val isSelected = book.id == currentOngoingId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ScribeCardTokens.RadiusSmall))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(book) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 50.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            if (book.coverUri != null) {
                                AsyncImage(
                                    model = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                        ImageRequest.Builder(context)
                                            .data(book.coverUri)
                                            .allowHardware(false)
                                            .build()
                                    } else book.coverUri,
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.AutoStories, null,
                                        modifier = Modifier.size(18.dp), tint = accentColor)
                                }
                            }
                        }
                        Text(
                            book.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 15.sp,
                            color      = if (isSelected) accentColor
                                         else MaterialTheme.colorScheme.onSurface,
                            modifier   = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, "Current project",
                                modifier = Modifier.size(18.dp), tint = accentColor)
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

private fun formatWordCount(count: Int): String = when {
    count >= 1_000 -> "${count / 1_000}k"
    else           -> "$count"
}

private fun formatRelativeTime(timestamp: Long): String {
    val now       = System.currentTimeMillis()
    val diffMs    = now - timestamp
    val diffMin   = diffMs / 60_000
    val diffHours = diffMs / 3_600_000
    val diffDays  = diffMs / 86_400_000

    return when {
        diffMin < 1    -> "Just now"
        diffMin < 60   -> "$diffMin min ago"
        diffHours < 24 -> "Today, ${SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))}"
        diffDays == 1L -> "Yesterday, ${SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))}"
        diffDays < 7   -> SimpleDateFormat("EEE, h:mm a", Locale.US).format(Date(timestamp))
        else           -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}
