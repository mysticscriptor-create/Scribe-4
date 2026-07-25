package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.primaloptima.scribe.ui.screens.ThemeEditScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.ThemeViewModel

class ThemeEditActivity : ComponentActivity() {

    private val vm: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeId = intent.getStringExtra("theme_id") ?: ""

        setContent {
            ScribeComposeTheme {
                ThemeEditScreen(
                    themeId = themeId,
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}
