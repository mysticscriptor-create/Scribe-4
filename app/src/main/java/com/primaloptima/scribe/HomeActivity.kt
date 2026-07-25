package com.primaloptima.scribe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.ui.screens.HomeScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.HomeViewModel

class HomeActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        setContent {
            ScribeComposeTheme {
                HomeScreen(
                    vm = vm,
                    onOpenBook = { book -> openBook(book) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenSheets = { startActivity(Intent(this, SheetsActivity::class.java)) },
                    onOpenThemes = { startActivity(Intent(this, ThemeListActivity::class.java)) }
                )
            }
        }
    }

    private fun openBook(book: Book) {
        startActivity(
            Intent(this, BookActivity::class.java)
                .putExtra(BookActivity.EXTRA_BOOK_ID, book.id)
                .putExtra(BookActivity.EXTRA_BOOK_TITLE, book.title)
        )
    }
}
