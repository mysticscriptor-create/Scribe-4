package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.primaloptima.scribe.ui.screens.SettingsScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
