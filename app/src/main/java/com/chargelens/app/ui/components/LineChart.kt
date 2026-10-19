package com.chargelens.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Larger chart with min / mid / max gridlines and labels. */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    formatLabel: (Float) -> String = { "%.1f".format(it) },
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = labelColor, fontSize = 11.sp)
    Canvas(modifier) {
        if (values.size < 2) {
            val text = measurer.measure("Collecting data…", style)
            drawText(text, topLeft = Offset((size.width - text.size.width) / 2f, (size.height - text.size.height) / 2f))
            return@Canvas
        }
        var min = values.min()
        var max = values.max()
        if (max - min < 1e-3f) { min -= 1f; max += 1f }
        val labels = listOf(max, (max + min) / 2f, min).map { measurer.measure(formatLabel(it), style) }
        val labelWidth = labels.maxOf { it.size.width } + 8.dp.toPx()
        val top = labels.first().size.height / 2f
        val bottom = size.height - labels.last().size.height / 2f
        val chartWidth = size.width - labelWidth

        labels.forEachIndexed { i, text ->
            val y = top + (bottom - top) * i / 2f
            drawLine(gridColor, Offset(labelWidth, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
        }

        val stepX = chartWidth / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(labelWidth + i * stepX, bottom - (v - min) / (max - min) * (bottom - top))
        }
        val line = catmullRomPath(points)
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent), startY = top, endY = bottom))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
