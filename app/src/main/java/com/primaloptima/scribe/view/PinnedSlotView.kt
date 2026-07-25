package com.primaloptima.scribe.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.primaloptima.scribe.data.Note

class PinnedSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pinnedNotes = mutableListOf<Note>()
    private var currentIndex = 0

    var onOpenNoteInEditor: ((Note) -> Unit)? = null
    var onAddNoteToSlot: (() -> Unit)? = null

    private val tvNoteTitle: TextView
    private val tvNotePath: TextView?
    private val tvNoteContent: TextView
    private val btnPrev: ImageButton
    private val btnNext: ImageButton
    private val btnEdit: ImageButton
    private val btnAdd: ImageButton
    private val btnUnpin: ImageButton
    private val emptyStateContainer: View
    private val contentContainer: View
    private val tvEmptyMsg: TextView?

    init {
        val emptyLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        tvEmptyMsg = TextView(context).apply {
            text = "Tap + to pin a note"
            textSize = 14f
            gravity = Gravity.CENTER
        }
        btnAdd = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            background = null
        }
        emptyLayout.addView(tvEmptyMsg)
        emptyLayout.addView(btnAdd)
        emptyStateContainer = emptyLayout

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvNoteTitle = TextView(context).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        btnPrev = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            background = null
        }
        btnNext = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_next)
            background = null
        }
        btnEdit = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            background = null
        }
        btnUnpin = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
        }

        headerRow.addView(tvNoteTitle)
        headerRow.addView(btnPrev)
        headerRow.addView(btnNext)
        headerRow.addView(btnEdit)
        headerRow.addView(btnUnpin)

        tvNotePath = TextView(context).apply {
            textSize = 12f
        }

        tvNoteContent = TextView(context).apply {
            textSize = 14f
            maxLines = 5
        }

        contentLayout.addView(headerRow)
        contentLayout.addView(tvNotePath)
        contentLayout.addView(tvNoteContent)
        contentContainer = contentLayout

        addView(emptyStateContainer)
        addView(contentContainer)

        btnPrev.setOnClickListener {
            if (pinnedNotes.isNotEmpty()) {
                currentIndex = (currentIndex - 1 + pinnedNotes.size) % pinnedNotes.size
                updateUi()
            }
        }

        btnNext.setOnClickListener {
            if (pinnedNotes.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % pinnedNotes.size
                updateUi()
            }
        }

        btnEdit.setOnClickListener {
            val current = currentNote()
            if (current != null) {
                onOpenNoteInEditor?.invoke(current)
            }
        }

        btnAdd.setOnClickListener {
            onAddNoteToSlot?.invoke()
        }

        btnUnpin.setOnClickListener {
            if (pinnedNotes.isNotEmpty() && currentIndex in pinnedNotes.indices) {
                pinnedNotes.removeAt(currentIndex)
                if (currentIndex >= pinnedNotes.size) {
                    currentIndex = maxOf(0, pinnedNotes.size - 1)
                }
                updateUi()
            }
        }

        emptyStateContainer.setOnClickListener {
            onAddNoteToSlot?.invoke()
        }
    }

    fun setSlotLabel(label: String) {
        tvEmptyMsg?.text = "Tap + to pin a note ($label)"
    }

    fun setNotes(notes: List<Note>) {
        pinnedNotes.clear()
        pinnedNotes.addAll(notes)
        if (currentIndex >= pinnedNotes.size) {
            currentIndex = maxOf(0, pinnedNotes.size - 1)
        }
        updateUi()
    }

    fun addNote(note: Note) {
        if (pinnedNotes.none { it.id == note.id }) {
            pinnedNotes.add(note)
            currentIndex = pinnedNotes.size - 1
            updateUi()
        }
    }

    fun currentNote(): Note? {
        return if (pinnedNotes.isNotEmpty() && currentIndex in pinnedNotes.indices) {
            pinnedNotes[currentIndex]
        } else null
    }

    private fun updateUi() {
        if (pinnedNotes.isEmpty()) {
            emptyStateContainer.visibility = View.VISIBLE
            contentContainer.visibility = View.GONE
            btnPrev.visibility = View.GONE
            btnNext.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnUnpin.visibility = View.GONE
        } else {
            emptyStateContainer.visibility = View.GONE
            contentContainer.visibility = View.VISIBLE
            btnPrev.visibility = if (pinnedNotes.size > 1) View.VISIBLE else View.GONE
            btnNext.visibility = if (pinnedNotes.size > 1) View.VISIBLE else View.GONE
            btnEdit.visibility = View.VISIBLE
            btnUnpin.visibility = View.VISIBLE

            val note = pinnedNotes[currentIndex]
            tvNoteTitle.text = if (pinnedNotes.size > 1) "${note.name} (${currentIndex + 1}/${pinnedNotes.size})" else note.name
            
            val cleanPath = note.folderPath.trim('/')
            tvNotePath?.text = if (cleanPath.isEmpty()) "/ ${note.name}" else "/ $cleanPath / ${note.name}"
            
            val contentText = note.content.ifBlank { "(empty note)" }
            com.primaloptima.scribe.util.MarkdownUtil.renderWithMarkwon(context, contentText, tvNoteContent)
        }
    }
}
