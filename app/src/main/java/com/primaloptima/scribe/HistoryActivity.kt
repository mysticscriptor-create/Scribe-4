package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.primaloptima.scribe.ui.screens.HistoryScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme

class HistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                HistoryScreen(onBack = { finish() })
            }
        }
    }
}
