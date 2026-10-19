package com.chargelens.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chargelens.app.data.settings.DesignSettings

object Palette {
    val Background = Color(0xFF0F1512)
    val Tile = Color(0xFF181F1B)
    val Card = Color(0xFF1C2420)
    val Track = Color(0xFF262A28)
    val OnBackground = Color(0xFFE1E4DF)
    val Muted = Color(0xFF9AA59C)
    val Discharge = Color(0xFFFFB27A)
    val Hot = Color(0xFFFF8A7A)
    val Inactive = Color(0xFF5D6660)

    val BlackTile = Color(0xFF0D0F0E)
    val BlackCard = Color(0xFF111413)

    val Accents = listOf(
        0xFFA8D8A0, // soft green (default)
        0xFF8FD9C8, // mint
        0xFFA5C8FF, // blue
        0xFFCFBCFF, // lavender
        0xFFFFD27A, // amber
        0xFFFFB4A0, // coral
        0xFFFFB0C8, // pink
    )
}

@Immutable
data class ChargeLensExtras(
    val accent: Color,
    val track: Color,
    val tile: Color,
    val card: Color,
    val muted: Color,
    val discharge: Color,
    val hot: Color,
    val inactive: Color,
    val gaugeThickness: Dp,
    val numberFont: FontFamily,
)

val LocalExtras = staticCompositionLocalOf {
    ChargeLensExtras(
        accent = Color(DesignSettings.DEFAULT_ACCENT),
        track = Palette.Track,
        tile = Palette.Tile,
        card = Palette.Card,
        muted = Palette.Muted,
        discharge = Palette.Discharge,
        hot = Palette.Hot,
        inactive = Palette.Inactive,
        gaugeThickness = 18.dp,
        numberFont = FontFamily.Default,
    )
}

/**
 * Dark, green-tinted theme. [forceBlack] gives pure black for AMOLED screens (charging screen
 * option); [DesignSettings.amoledBlack] does the same app-wide.
 */
@Composable
fun ChargeLensTheme(
    design: DesignSettings = DesignSettings(),
    forceBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val black = forceBlack || design.amoledBlack
    val accent = Color(design.accentArgb)
    val background = if (black) Color.Black else Palette.Background
    val tile = if (black) Palette.BlackTile else Palette.Tile
    val card = if (black) Palette.BlackCard else Palette.Card
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF10210F),
        primaryContainer = accent.copy(alpha = 0.25f),
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = Color(0xFF10210F),
        secondaryContainer = accent.copy(alpha = 0.22f),
        onSecondaryContainer = accent,
        background = background,
        onBackground = Palette.OnBackground,
        surface = background,
        onSurface = Palette.OnBackground,
        surfaceVariant = tile,
        onSurfaceVariant = Palette.Muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = tile,
        surfaceContainer = tile,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        outline = Palette.Inactive,
        outlineVariant = Palette.Track,
    )
    val extras = ChargeLensExtras(
        accent = accent,
        track = Palette.Track,
        tile = tile,
        card = card,
        muted = Palette.Muted,
        discharge = Palette.Discharge,
        hot = Palette.Hot,
        inactive = Palette.Inactive,
        gaugeThickness = design.gaugeThicknessDp.dp,
        numberFont = if (design.monoFont) FontFamily.Monospace else FontFamily.Default,
    )
    CompositionLocalProvider(LocalExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
