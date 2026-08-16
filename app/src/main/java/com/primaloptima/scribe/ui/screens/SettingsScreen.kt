package com.primaloptima.scribe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenThemes: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScribeApp
    val themeManager = remember { app.themeManager }
    val vm: SettingsViewModel = viewModel()

    val showWordCount by vm.showWordCount.collectAsStateWithLifecycle()
    val typewriterMode by vm.typewriterMode.collectAsStateWithLifecycle()
    val lineSpacing by vm.lineSpacing.collectAsStateWithLifecycle()
    val fontSize by vm.editorFontSize.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoal.collectAsStateWithLifecycle()
    val homeStartPage by vm.homeStartPage.collectAsStateWithLifecycle()
    val todayWords by vm.todayWords.collectAsStateWithLifecycle()
    val currentStreak by vm.currentStreak.collectAsStateWithLifecycle()
    val longestStreak by vm.longestStreak.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadStats() }

    // History settings
    val autoHistoryEnabled by vm.autoHistoryEnabled.collectAsStateWithLifecycle()
    val manualCheckpointsEnabled by vm.manualCheckpointsEnabled.collectAsStateWithLifecycle()
    val autoHistorySlots by vm.autoHistorySlots.collectAsStateWithLifecycle()
    val manualCheckpointSlots by vm.manualCheckpointSlots.collectAsStateWithLifecycle()
    val autoHistoryMinWords by vm.autoHistoryMinWords.collectAsStateWithLifecycle()

    var showGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = "Settings",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Home Section
            Text("Home", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start on", fontWeight = FontWeight.Medium)
                    Text(
                        "Which screen opens when you launch Scribe.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = homeStartPage == "books",
                            onClick = { vm.setHomeStartPage("books") },
                            label = { Text("Books") },
                            leadingIcon = if (homeStartPage == "books") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = homeStartPage == "dashboard",
                            onClick = { vm.setHomeStartPage("dashboard") },
                            label = { Text("Dashboard") },
                            leadingIcon = if (homeStartPage == "dashboard") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            HorizontalDivider()

            // Appearance Section
            Text("Appearance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenThemes() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Theme", fontWeight = FontWeight.Bold)
                        Text(themeManager.activeTheme().name, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            HorizontalDivider()

            // Writing Options Section
            Text("Writing Options", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Word Count FAB", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = showWordCount,
                            onCheckedChange = { vm.setShowWordCount(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Typewriter Mode (Center Active Line)", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = typewriterMode,
                            onCheckedChange = { vm.setTypewriterMode(it) }
                        )
                    }

                    Column {
                        Text("Editor Font Size: $fontSize sp", fontWeight = FontWeight.Medium)
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = { vm.setEditorFontSize(it.toInt()) },
                            valueRange = 12f..28f
                        )
                    }
                }
            }

            HorizontalDivider()

            // Version History Section
            Text("Version History", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-save History", fontWeight = FontWeight.Medium)
                            Text("Saves a snapshot when you leave a note", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoHistoryEnabled,
                            onCheckedChange = { vm.setAutoHistoryEnabled(it) }
                        )
                    }

                    if (autoHistoryEnabled) {
                        Column {
                            Text("Auto-save slots: $autoHistorySlots", fontWeight = FontWeight.Medium)
                            Text("How many auto-saves to keep per note", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Slider(
                                value = autoHistorySlots.toFloat(),
                                onValueChange = { vm.setAutoHistorySlots(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }

                        Column {
                            Text("Min words to trigger: $autoHistoryMinWords words", fontWeight = FontWeight.Medium)
                            Text("Net word change needed to auto-save", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Slider(
                                value = autoHistoryMinWords.toFloat(),
                                onValueChange = { vm.setAutoHistoryMinWords(it.toInt()) },
                                valueRange = 1f..100f,
                                steps = 98
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Manual Checkpoints", fontWeight = FontWeight.Medium)
                            Text("Saved when you tap the bookmark button", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = manualCheckpointsEnabled,
                            onCheckedChange = { vm.setManualCheckpointsEnabled(it) }
                        )
                    }

                    if (manualCheckpointsEnabled) {
                        Column {
                            Text("Checkpoint slots: $manualCheckpointSlots", fontWeight = FontWeight.Medium)
                            Text("How many checkpoints to keep per note", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Slider(
                                value = manualCheckpointSlots.toFloat(),
                                onValueChange = { vm.setManualCheckpointSlots(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Daily Goals Section
            Text("Goals & Progress", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGoalDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily Word Goal", fontWeight = FontWeight.Bold)
                        Text("$dailyGoal words", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Words today: $todayWords", fontSize = 13.sp)
                    Text("Current streak: $currentStreak days", fontSize = 13.sp)
                    Text("Longest streak: $longestStreak days", fontSize = 13.sp)
                }
            }
        }
    }

    if (showGoalDialog) {
        var goalInput by remember { mutableStateOf("$dailyGoal") }
        FrostedDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Daily Word Goal") },
            text = {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Target Words") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = goalInput.toIntOrNull()
                        if (num != null && num >= 50) {
                            vm.setDailyGoal(num)
                            showGoalDialog = false
                            Toast.makeText(context, "Daily goal updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}
