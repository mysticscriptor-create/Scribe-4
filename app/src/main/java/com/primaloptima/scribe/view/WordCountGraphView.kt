package com.primaloptima.scribe.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.primaloptima.scribe.R

/**
 * WordCountGraphView backed by MPAndroidChart for smooth, high-performance
 * word count analytics with animated rounded bars, interactive tooltips, and
 * dynamic theme styling.
 */
class WordCountGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chart: BarChart

    init {
        chart = BarChart(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setFitBars(true)
            extraBottomOffset = 6f
        }
        addView(chart)
    }

    fun setData(
        items: List<Pair<String, Int>>,
        accent: Int = Color.parseColor("#A8651E"),
        text: Int = Color.parseColor("#2A2622"),
        mutedText: Int = Color.parseColor("#7A6F5D")
    ) {
        if (items.isEmpty()) {
            chart.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        items.forEachIndexed { idx, pair ->
            entries.add(BarEntry(idx.toFloat(), pair.second.toFloat()))
            labels.add(pair.first)
        }

        val dataSet = BarDataSet(entries, "Words Written").apply {
            color = accent
            highLightColor = accent
            highLightAlpha = 180
            valueTextColor = text
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) value.toInt().toString() else ""
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.45f
        }

        // Configure X-Axis (Days)
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(true)
            axisLineColor = adjustAlpha(mutedText, 0.3f)
            textColor = mutedText
            textSize = 10f
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(labels)
        }

        // Configure Left Y-Axis (Word counts)
        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = adjustAlpha(mutedText, 0.15f)
            gridLineWidth = 0.8f
            setDrawAxisLine(false)
            textColor = mutedText
            textSize = 9f
            axisMinimum = 0f
        }

        // Disable Right Y-Axis
        chart.axisRight.isEnabled = false

        // Interactive Tooltip
        val marker = CustomChartMarker(context, accent, text)
        chart.marker = marker

        chart.data = barData
        chart.animateY(800)
        chart.invalidate()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private class CustomChartMarker(
        context: Context,
        private val bgAccent: Int,
        private val textColorVal: Int
    ) : MarkerView(context, 0) {

        private val tvMarker: TextView = TextView(context).apply {
            setPadding(16, 8, 16, 8)
        }

        init {
            addView(tvMarker)
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(bgAccent)
                cornerRadius = 12f
            }
            tvMarker.background = shape
            tvMarker.setTextColor(textColorVal)
        }

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            if (e != null) {
                tvMarker.text = "${e.y.toInt()} words"
            }
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF(-(width / 2f), -height.toFloat() - 10f)
        }
    }
}
