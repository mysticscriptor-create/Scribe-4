package com.primaloptima.scribe

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.primaloptima.scribe.data.Note

/**
 * Manages in-app floating reference windows. Each window is a draggable overlay
 * added directly to the activity's content frame.
 */
class FloatingWindowManager(private val activity: AppCompatActivity) {

    private val root: ViewGroup =
        activity.findViewById<ViewGroup>(android.R.id.content)

    private val activeWindows = mutableMapOf<String, View>()

    fun openWindow(note: Note) {
        if (activeWindows.containsKey(note.id)) {
            activeWindows[note.id]?.bringToFront()
            return
        }
        val windowView = createWindowView(note)
        root.addView(windowView)
        activeWindows[note.id] = windowView
    }

    fun closeWindow(noteId: String) {
        activeWindows.remove(noteId)?.let { root.removeView(it) }
    }

    fun closeAll() {
        activeWindows.values.toList().forEach { root.removeView(it) }
        activeWindows.clear()
    }

    val windowCount: Int get() = activeWindows.size

    @SuppressLint("ClickableViewAccessibility")
    private fun createWindowView(note: Note): View {
        val ctx = activity
        val dm = ctx.resources.displayMetrics
        val initW = (dm.widthPixels * 0.75f).toInt()
        val initH = (dm.heightPixels * 0.45f).toInt()
        val initX = (dm.widthPixels  * 0.12f)
        val initY = (dm.heightPixels * 0.20f)

        val container = FrameLayout(ctx).apply {
            elevation = 24f
            alpha     = 0f
            translationX = initX
            translationY = initY
        }
        container.layoutParams = FrameLayout.LayoutParams(initW, initH)

        val innerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Title bar
        val titleBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F3F4F6"))
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(ctx).apply {
            text = note.name
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(ctx).apply {
            text = "✕"
            textSize = 16f
            setPadding(16, 0, 16, 0)
            setTextColor(Color.GRAY)
            setOnClickListener { closeWindow(note.id) }
        }

        titleBar.addView(tvTitle)
        titleBar.addView(btnClose)

        val scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val tvContent = TextView(ctx).apply {
            text = note.content.ifBlank { "Empty note" }
            textSize = 13f
            setPadding(16, 16, 16, 16)
            setTextColor(Color.DKGRAY)
        }

        scrollView.addView(tvContent)
        innerLayout.addView(titleBar)
        innerLayout.addView(scrollView)
        container.addView(innerLayout)

        container.animate().alpha(1f).setDuration(180).start()

        var downRawX = 0f
        var downRawY = 0f
        var downTx   = 0f
        var downTy   = 0f

        titleBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTx   = container.translationX
                    downTy   = container.translationY
                    container.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    container.translationX = (downTx + dx)
                        .coerceIn(0f, (dm.widthPixels  - initW).toFloat())
                    container.translationY = (downTy + dy)
                        .coerceIn(0f, (dm.heightPixels - initH).toFloat())
                    true
                }
                else -> false
            }
        }

        return container
    }
}
