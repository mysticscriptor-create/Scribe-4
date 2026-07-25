package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.primaloptima.scribe.ui.screens.GuideScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme

class GuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                GuideScreen(onBack = { finish() })
            }
        }
    }
}
