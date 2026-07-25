package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.primaloptima.scribe.ui.screens.SheetsScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.SheetsViewModel

class SheetsActivity : ComponentActivity() {

    private val vm: SheetsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScribeComposeTheme {
                SheetsScreen(
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}
