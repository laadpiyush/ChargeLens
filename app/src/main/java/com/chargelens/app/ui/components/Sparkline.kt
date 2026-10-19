package com.chargelens.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Thin smoothed line with a soft gradient fill underneath. No axes. */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    fillAlpha: Float = 0.22f,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val pad = 3.dp.toPx()
        val points = scalePoints(values, size.width, size.height, pad)
        val line = catmullRomPath(points)
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = fillAlpha), Color.Transparent)))
        drawPath(
            line,
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.9f))),
            style = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

internal fun scalePoints(values: List<Float>, width: Float, height: Float, pad: Float): List<Offset> {
    val min = values.min()
    val max = values.max()
    val range = max - min
    val stepX = width / (values.size - 1)
    return values.mapIndexed { i, v ->
        val norm = if (range < 1e-3f) 0.5f else (v - min) / range
        Offset(i * stepX, height - pad - norm * (height - 2 * pad))
    }
}

/** Catmull-Rom spline through [points], expressed as cubic Béziers. */
internal fun catmullRomPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[maxOf(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(i + 2, points.size - 1)]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}
