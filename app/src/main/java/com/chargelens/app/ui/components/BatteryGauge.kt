package com.chargelens.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * 270° arc open at the bottom. Progress sweeps clockwise from the lower-left; [content] sits in
 * the middle.
 */
@Composable
fun BatteryGauge(
    progress: Float,
    thickness: Dp,
    progressColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "gauge",
    )
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(trackColor, START_ANGLE, SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            if (animated > 0.001f) {
                drawArc(progressColor, START_ANGLE, SWEEP * animated, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
