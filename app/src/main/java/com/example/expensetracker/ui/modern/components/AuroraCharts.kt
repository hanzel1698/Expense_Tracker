package com.example.expensetracker.ui.modern.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ChartPoint
import java.time.LocalDate

/**
 * Rounded-bar trend chart. Feature-parity with BrutalistBarChartWithData:
 * amount labels above bars, period labels below, tap to select/deselect,
 * optional trend line, and a bar-click callback.
 */
@Composable
fun AuroraBarChart(
    chartData: List<ChartPoint>,
    modifier: Modifier = Modifier,
    showTrendLine: Boolean = true,
    onValueSelected: (Float?, Any?) -> Unit = { _, _ -> },
    onBarClicked: (ChartPoint) -> Unit = {}
) {
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val trendLineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxSpent = remember(chartData) { (chartData.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(100f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    if (chartData.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = modifier.padding(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chartData) {
                    detectTapGestures(
                        onTap = { offset ->
                            val itemCount = chartData.size.coerceAtLeast(1)
                            val barWidth = size.width / (itemCount * 1.5f)
                            val spacing = (size.width - (barWidth * itemCount)) / (itemCount + 1)

                            var hit = -1
                            chartData.forEachIndexed { i, _ ->
                                val x = spacing + i * (barWidth + spacing)
                                if (offset.x >= x && offset.x <= x + barWidth) {
                                    hit = i
                                }
                            }
                            if (hit == -1 || hit == selectedIndex) {
                                selectedIndex = null
                                onValueSelected(null, null)
                            } else {
                                selectedIndex = hit
                                onValueSelected(chartData[hit].value, chartData[hit].period)
                                onBarClicked(chartData[hit])
                            }
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val itemCount = chartData.size.coerceAtLeast(1)
            val barWidth = canvasWidth / (itemCount * 1.5f)
            val spacing = (canvasWidth - (barWidth * itemCount)) / (itemCount + 1)

            val paint = Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
                isAntiAlias = true
            }

            paint.textSize = 100f
            var maxLabelWidth = 0f
            chartData.forEach { point ->
                maxLabelWidth = maxOf(maxLabelWidth, paint.measureText(point.label))
            }
            maxLabelWidth = maxLabelWidth.coerceAtLeast(1f)
            val unifiedSize = (100f * (barWidth / maxLabelWidth)).coerceAtMost(38f)
            val labelPadding = 12f
            val topTextHeight = (unifiedSize * 1.5f) + labelPadding
            val bottomTextHeight = (unifiedSize * 1.5f) + labelPadding
            val usableHeight = (canvasHeight - topTextHeight - bottomTextHeight).coerceAtLeast(0f)

            // Bars (rounded)
            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val isSelected = index == selectedIndex
                if (h > 0f) {
                    drawRoundRect(
                        color = if (isSelected) selectedColor else barColor,
                        topLeft = Offset(x, canvasHeight - bottomTextHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                    )
                } else {
                    // Zero-value hint dot
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.15f),
                        topLeft = Offset(x, canvasHeight - bottomTextHeight - 4f),
                        size = Size(barWidth, 4f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }

            // Trend line
            if (showTrendLine && chartData.size > 1) {
                val path = Path()
                chartData.forEachIndexed { index, point ->
                    val x = spacing + index * (barWidth + spacing) + barWidth / 2f
                    val y = canvasHeight - bottomTextHeight - (point.value / maxSpent) * usableHeight
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = trendLineColor.copy(alpha = 0.6f), style = Stroke(width = 3f))
            }

            // Labels
            paint.textSize = unifiedSize
            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val barTop = canvasHeight - bottomTextHeight - h
                val centerX = x + barWidth / 2f
                val isSelected = index == selectedIndex

                // Amount above the bar
                val amountText = if (point.value > 0) "₹${point.value.toInt()}" else ""
                paint.isFakeBoldText = isSelected
                if (amountText.isNotEmpty()) {
                    paint.textSize = unifiedSize
                    val amountWidth = paint.measureText(amountText)
                    paint.textSize = if (amountWidth > barWidth) unifiedSize * (barWidth / amountWidth) else unifiedSize
                    drawContext.canvas.nativeCanvas.drawText(amountText, centerX, barTop - 6f, paint)
                }

                // Period label below the bar (day range for weekly buckets)
                val dateRangeText = if (point.endDate != null && point.period is LocalDate) {
                    val start = point.period as LocalDate
                    val end = point.endDate as LocalDate
                    if (start == end) "${start.dayOfMonth}" else "${start.dayOfMonth}-${end.dayOfMonth}"
                } else {
                    point.label
                }
                paint.textSize = unifiedSize
                val dateRangeWidth = paint.measureText(dateRangeText)
                paint.textSize = if (dateRangeWidth > barWidth) unifiedSize * (barWidth / dateRangeWidth) else unifiedSize
                drawContext.canvas.nativeCanvas.drawText(dateRangeText, centerX, canvasHeight - 5f, paint)
            }
        }
    }
}

/**
 * Horizontal category / subcategory breakdown chart. Feature-parity with the
 * brutalist HorizontalBarChart: tap drills into subcategories (or navigates when
 * already drilled in), long-press asks to open the matching expense list.
 */
@Composable
fun AuroraHorizontalBarChart(
    expenses: List<com.example.expensetracker.model.Expense>,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onBarLongPressed: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    data class BarData(val label: String, val value: Float)

    val chartData = remember(expenses, categories, selectedCategory) {
        if (selectedCategory != null) {
            val subcategories = subcategoriesMap[selectedCategory] ?: emptyList()
            subcategories.map { sub ->
                val total = expenses.filter { it.category == selectedCategory && it.subcategory == sub }
                    .sumOf { it.amount }
                BarData(sub, total.toFloat())
            }.filter { it.value > 0 }.sortedByDescending { it.value }
        } else {
            categories.map { cat ->
                val total = expenses.filter { it.category == cat }.sumOf { it.amount }
                BarData(cat, total.toFloat())
            }.filter { it.value > 0 }.sortedByDescending { it.value }
        }
    }

    val palette = com.example.expensetracker.ui.modern.theme.LocalAuroraExtras.current.chartColors
    val labelColor = MaterialTheme.colorScheme.onSurface
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedTint = MaterialTheme.colorScheme.tertiary

    val maxValue = remember(chartData) { (chartData.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(100f) }
    var selectedIndex by remember(chartData) { mutableStateOf<Int?>(null) }

    if (chartData.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No spending in this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = modifier.padding(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chartData) {
                    fun hitIndex(offset: Offset): Int {
                        val fixedBarHeight = 50f
                        val spacing = 14f
                        val totalItemHeight = fixedBarHeight + spacing
                        var hit = -1
                        chartData.forEachIndexed { i, _ ->
                            val y = spacing + i * totalItemHeight
                            if (offset.y >= y && offset.y <= y + fixedBarHeight) {
                                hit = i
                            }
                        }
                        return hit
                    }

                    detectTapGestures(
                        onTap = { offset ->
                            val hit = hitIndex(offset)
                            if (hit == -1 || hit == selectedIndex) {
                                selectedIndex = null
                                onCategorySelected(null)
                            } else {
                                selectedIndex = hit
                                if (selectedCategory != null) {
                                    onBarLongPressed(selectedCategory, chartData[hit].label)
                                } else {
                                    onCategorySelected(chartData[hit].label)
                                }
                            }
                        },
                        onLongPress = { offset ->
                            val hit = hitIndex(offset)
                            if (hit != -1) {
                                val category = selectedCategory ?: chartData[hit].label
                                val subcategory = if (selectedCategory != null) chartData[hit].label else null
                                onBarLongPressed(category, subcategory)
                            }
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val fixedBarHeight = 50f
            val spacing = 14f
            val totalItemHeight = fixedBarHeight + spacing

            val paint = Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            paint.textSize = 30f
            var maxLabelWidth = 0f
            chartData.forEach { point ->
                maxLabelWidth = maxOf(maxLabelWidth, paint.measureText(point.label))
            }
            val adjustedLabelWidth = maxOf(maxLabelWidth, 80f).coerceAtMost(180f)
            val valueTextWidth = 90f
            val adjustedUsableWidth =
                (canvasWidth - adjustedLabelWidth - valueTextWidth - 30f).coerceAtLeast(0f)

            chartData.forEachIndexed { index, point ->
                val y = spacing + index * totalItemHeight
                val w = ((point.value / maxValue) * adjustedUsableWidth).coerceAtLeast(6f)
                val isSelected = index == selectedIndex
                val base = palette[index % palette.size]

                // Track
                drawRoundRect(
                    color = base.copy(alpha = 0.14f),
                    topLeft = Offset(adjustedLabelWidth + 10f, y),
                    size = Size(adjustedUsableWidth, fixedBarHeight),
                    cornerRadius = CornerRadius(fixedBarHeight / 2f, fixedBarHeight / 2f)
                )
                // Bar
                drawRoundRect(
                    color = if (isSelected) selectedTint else base,
                    topLeft = Offset(adjustedLabelWidth + 10f, y),
                    size = Size(w, fixedBarHeight),
                    cornerRadius = CornerRadius(fixedBarHeight / 2f, fixedBarHeight / 2f)
                )
            }

            chartData.forEachIndexed { index, point ->
                val y = spacing + index * totalItemHeight
                val isSelected = index == selectedIndex

                // Category label
                paint.color = labelColor.toArgb()
                paint.textSize = 28f
                paint.isFakeBoldText = isSelected
                var displayLabel = point.label
                while (paint.measureText(displayLabel) > adjustedLabelWidth && displayLabel.length > 3) {
                    displayLabel = displayLabel.dropLast(2)
                }
                if (displayLabel != point.label) displayLabel = displayLabel.dropLast(1) + "…"
                drawContext.canvas.nativeCanvas.drawText(
                    displayLabel, 4f, y + fixedBarHeight / 2f + 10f, paint
                )

                // Value at end of the track
                paint.color = valueColor.toArgb()
                paint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    "₹${String.format("%.0f", point.value)}",
                    canvasWidth - 4f,
                    y + fixedBarHeight / 2f + 10f,
                    paint
                )
                paint.textAlign = Paint.Align.LEFT
            }
        }
    }
}
