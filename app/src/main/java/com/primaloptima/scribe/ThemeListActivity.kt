package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.primaloptima.scribe.ui.screens.ThemeListScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.ThemeViewModel

class ThemeListActivity : ComponentActivity() {

    private val vm: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                ThemeListScreen(
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }

}
