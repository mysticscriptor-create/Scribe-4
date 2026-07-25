package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.primaloptima.scribe.ui.screens.CrashScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace was captured."

        setContent {
            ScribeComposeTheme {
                CrashScreen(stackTrace = trace)
            }
        }
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }
}
