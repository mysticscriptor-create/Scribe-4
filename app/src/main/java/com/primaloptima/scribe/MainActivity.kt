package com.primaloptima.scribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.screens.MainEditorScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.NoteListViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
        const val EXTRA_BOOK_ID = "bookId"
    }

    private val editorVm: EditorViewModel by viewModels()
    private val bookVm: BookViewModel by viewModels()
    private val noteListVm: NoteListViewModel by viewModels()
    private val shortcutsVm: ShortcutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: Note.DEFAULT_BOOK_ID
        val initialNoteId = intent.getStringExtra(EXTRA_NOTE_ID)

        bookVm.init(bookId)

        setContent {
            ScribeComposeTheme {
                MainEditorScreen(
                    editorVm = editorVm,
                    bookVm = bookVm,
                    noteListVm = noteListVm,
                    shortcutsVm = shortcutsVm,
                    initialNoteId = initialNoteId,
                    onBack = { finish() }
                )
            }
        }
    }
}
