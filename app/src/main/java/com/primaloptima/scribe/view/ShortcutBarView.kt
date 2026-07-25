package com.primaloptima.scribe.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.primaloptima.scribe.R
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.util.ThemeManager

/**
 * Horizontal shortcut bar shown above the keyboard.
 *
 * Layout (left → right):
 *   [Undo] [Redo] | divider | [shortcut1] [shortcut2] ... | [+]
 *
 * Undo/Redo buttons are disabled (alpha 0.35) when the stack is empty.
 */
class ShortcutBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    interface Listener {
        fun onUndoClick()
        fun onRedoClick()
        fun onShortcutClick(shortcut: ShortcutAction)
        fun onAddShortcutClick()
    }

    var listener: Listener? = null

    private val container: LinearLayout
    private lateinit var undoBtn: TextView
    private lateinit var redoBtn: TextView
    private lateinit var addBtn: TextView
    private val shortcutButtons = mutableListOf<TextView>()

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER

        // Root horizontal scroll container
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        addView(container)

        buildStaticButtons()
    }

    private fun buildStaticButtons() {
        val density = resources.displayMetrics.density
        val buttonH = (44 * density).toInt()
        val buttonPadH = (14 * density).toInt()
        val buttonPadV = (8 * density).toInt()
        val dividerW = (1 * density).toInt()
        val dividerH = (20 * density).toInt()

        // Undo button
        undoBtn = makeButton(context, "↺", buttonPadH, buttonPadV) { listener?.onUndoClick() }
        container.addView(undoBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonH))

        // Redo button
        redoBtn = makeButton(context, "↻", buttonPadH, buttonPadV) { listener?.onRedoClick() }
        container.addView(redoBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonH))

        // Divider
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dividerW, dividerH).also {
                it.topMargin = ((buttonH - dividerH) / 2)
                it.leftMargin = (4 * density).toInt()
                it.rightMargin = (4 * density).toInt()
            }
        }
        container.addView(divider)

        // Shortcuts are added in refreshShortcuts()

        // Add (+) button — always at the end
        addBtn = makeButton(context, "+", buttonPadH, buttonPadV) { listener?.onAddShortcutClick() }
        // Will be re-added in refreshShortcuts after all shortcuts
    }

    /**
     * Refresh the shortcut buttons list and update undo/redo state.
     * Call this whenever shortcuts or the active theme change.
     */
    fun refreshShortcuts(shortcuts: List<ShortcutAction>, theme: AppTheme) {
        val density = resources.displayMetrics.density
        val buttonH = (44 * density).toInt()
        val buttonPadH = (12 * density).toInt()
        val buttonPadV = (8 * density).toInt()

        // Remove old shortcut buttons and the add button from the end
        for (btn in shortcutButtons) container.removeView(btn)
        shortcutButtons.clear()
        container.removeView(addBtn)

        // Apply theme colors
        val bgColor = ThemeManager.parseColor(theme.colors.toolbar)
        val textColor = ThemeManager.parseColor(theme.colors.toolbarText)
        val mutedColor = ThemeManager.parseColor(theme.colors.mutedText)
        val borderColor = ThemeManager.parseColor(theme.colors.border)

        setBackgroundColor(bgColor)
        undoBtn.setTextColor(textColor)
        redoBtn.setTextColor(textColor)

        // Update divider color
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            if (v !is TextView) v.setBackgroundColor(borderColor)
        }

        // Add shortcut buttons
        for (shortcut in shortcuts) {
            val btn = makeButton(context, shortcut.label, buttonPadH, buttonPadV) {
                listener?.onShortcutClick(shortcut)
            }
            btn.setTextColor(textColor)
            container.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonH))
            shortcutButtons.add(btn)
        }

        // Re-add the + button at the end
        addBtn.setTextColor(mutedColor)
        container.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonH))
    }

    fun setUndoEnabled(enabled: Boolean) {
        undoBtn.isEnabled = enabled
        undoBtn.alpha = if (enabled) 1f else 0.35f
    }

    fun setRedoEnabled(enabled: Boolean) {
        redoBtn.isEnabled = enabled
        redoBtn.alpha = if (enabled) 1f else 0.35f
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private fun makeButton(
        ctx: Context,
        label: String,
        padH: Int,
        padV: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(ctx).apply {
            text = label
            textSize = 15f
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick() }
            background = null
            isClickable = true
            isFocusable = true
        }
    }
}
