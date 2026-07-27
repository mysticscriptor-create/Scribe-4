package com.primaloptima.scribe.ui.screens

import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.ThemeListActivity
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.util.WritingStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScribeApp
    val prefs = remember { app.prefs }
    val themeManager = remember { app.themeManager }
    val writingStats = remember { WritingStats(prefs) }

    var showWordCount by remember { mutableStateOf(prefs.showWordCount) }
    var typewriterMode by remember { mutableStateOf(prefs.typewriterMode) }
    var lineSpacing by remember { mutableStateOf(prefs.lineSpacing) }
    var fontSize by remember { mutableFloatStateOf(prefs.editorFontSize.toFloat()) }
    var dailyGoal by remember { mutableIntStateOf(prefs.dailyGoal) }

    var showGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
            // Appearance Section
            Text("Appearance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, ThemeListActivity::class.java))
                    },
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
                            onCheckedChange = {
                                showWordCount = it
                                prefs.showWordCount = it
                            }
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
                            onCheckedChange = {
                                typewriterMode = it
                                prefs.typewriterMode = it
                            }
                        )
                    }

                    Column {
                        Text("Editor Font Size: ${fontSize.toInt()} sp", fontWeight = FontWeight.Medium)
                        Slider(
                            value = fontSize,
                            onValueChange = {
                                fontSize = it
                                prefs.editorFontSize = it.toInt()
                            },
                            valueRange = 12f..28f
                        )
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
                    Text("Words today: ${writingStats.todayWords}", fontSize = 13.sp)
                    Text("Current streak: ${writingStats.currentStreak()} days", fontSize = 13.sp)
                    Text("Longest streak: ${writingStats.longestStreak()} days", fontSize = 13.sp)
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
                            dailyGoal = num
                            writingStats.setDailyGoal(num)
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
