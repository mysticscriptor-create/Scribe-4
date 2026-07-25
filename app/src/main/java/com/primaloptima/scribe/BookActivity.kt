package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.screens.BookScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.BookViewModel

class BookActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID    = "bookId"
        const val EXTRA_BOOK_TITLE = "bookTitle"
    }

    private val vm: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: Note.DEFAULT_BOOK_ID
        vm.init(bookId)

        setContent {
            ScribeComposeTheme {
                BookScreen(
                    vm = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}
