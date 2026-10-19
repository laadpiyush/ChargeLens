package com.chargelens.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargelens.app.ui.theme.LocalExtras

@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalExtras.current.accent,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    sparkline: List<Float>? = null,
    sparkColor: Color = LocalExtras.current.accent,
    onClick: (() -> Unit)? = null,
) {
    val extras = LocalExtras.current
    val shape = RoundedCornerShape(24.dp)
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = accessibilityLabel
        if (onClick != null) {
            role = Role.Button
            onClick(label = "Show $label details") { onClick(); true }
        }
    }
    val body: @Composable () -> Unit = {
        Box {
            if (sparkline != null && sparkline.size >= 2) {
                Sparkline(
                    values = sparkline,
                    color = sparkColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 44.dp)
                        .height(44.dp),
                )
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        color = extras.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(52.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        value,
                        fontFamily = extras.numberFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClick != null) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = extras.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    val tileModifier = modifier.heightIn(min = 132.dp).then(semantics)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = extras.tile, modifier = tileModifier, content = body)
    } else {
        Surface(shape = shape, color = extras.tile, modifier = tileModifier, content = body)
    }
}
