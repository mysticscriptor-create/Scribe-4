package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.primaloptima.scribe.ui.screens.ShortcutsScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel

class ShortcutsActivity : ComponentActivity() {

    private val vm: ShortcutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                ShortcutsScreen(
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}
