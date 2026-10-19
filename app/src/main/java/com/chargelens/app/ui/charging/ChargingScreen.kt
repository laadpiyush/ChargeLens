package com.chargelens.app.ui.charging

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chargelens.app.data.settings.ChargingScreenSettings
import com.chargelens.app.ui.details.ChargingTopBar
import com.chargelens.app.ui.details.DetailsScreen
import com.chargelens.app.ui.details.DetailsUiState
import com.chargelens.app.ui.details.GaugeHero
import kotlinx.coroutines.delay

/** Pixel-shift offsets in dp, cycled once a minute to avoid burn-in on OLED panels. */
private val SHIFT_PATTERN = listOf(0 to 0, 3 to 2, -2 to 4, -4 to -1, 1 to -3, 4 to 1, -1 to 3, -3 to -3)

/**
 * The Details screen in full-screen form (no bottom nav, close button), with optional burn-in
 * protection and auto-dim. Used by the charging activity and the overlay.
 */
@Composable
fun ChargingScreen(
    state: DetailsUiState,
    prefs: ChargingScreenSettings,
    onClose: () -> Unit,
    onToggleMonitoring: () -> Unit,
    onDimChange: (Boolean) -> Unit,
    enableDetailSheets: Boolean = true,
) {
    BurnInSafeContainer(prefs, onDimChange) { shift ->
        DetailsScreen(
            state = state,
            onToggleMonitoring = onToggleMonitoring,
            topBar = { ChargingTopBar(onClose) },
            enableDetailSheets = enableDetailSheets,
            modifier = Modifier
                .safeDrawingPadding()
                .offset { shift },
        )
    }
}

/**
 * Screen saver: just the battery gauge, centred, with the time estimate always visible. Touching
 * the screen ends the screen saver (handled by Android), so there are no buttons.
 */
@Composable
fun ScreensaverScreen(
    state: DetailsUiState,
    prefs: ChargingScreenSettings,
    onDimChange: (Boolean) -> Unit,
) {
    BurnInSafeContainer(prefs, onDimChange) { shift ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .offset { shift },
            contentAlignment = Alignment.Center,
        ) {
            val size = minOf(maxWidth, maxHeight) * 0.85f
            GaugeHero(state, Modifier.size(size), showEstimate = true)
        }
    }
}

/**
 * Shared full-screen host: background and content colour (these screens live outside the app's
 * Scaffold), slow pixel shift against burn-in, and auto-dim that wakes on touch.
 */
@Composable
internal fun BurnInSafeContainer(
    prefs: ChargingScreenSettings,
    onDimChange: (Boolean) -> Unit,
    /** Larger shifts for content that stays on screen for hours (always-on view). */
    shiftScale: Int = 1,
    content: @Composable (shift: IntOffset) -> Unit,
) {
    var dimmed by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val currentOnDimChange by rememberUpdatedState(onDimChange)

    LaunchedEffect(prefs.autoDimSeconds, lastTouch) {
        if (prefs.autoDimSeconds > 0) {
            delay(prefs.autoDimSeconds * 1000L)
            dimmed = true
        }
    }
    LaunchedEffect(dimmed) { currentOnDimChange(dimmed) }

    val density = LocalDensity.current
    val shiftTarget by produceState(IntOffset.Zero, prefs.pixelShift) {
        value = IntOffset.Zero
        if (!prefs.pixelShift) return@produceState
        var i = 0
        while (true) {
            delay(60_000)
            i = (i + 1) % SHIFT_PATTERN.size
            val (x, y) = SHIFT_PATTERN[i]
            value = with(density) { IntOffset((x * shiftScale).dp.roundToPx(), (y * shiftScale).dp.roundToPx()) }
        }
    }
    val shift by animateIntOffsetAsState(shiftTarget, tween(1500), label = "pixelShift")

    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Observe every touch (without consuming) to restart the auto-dim timer.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastTouch = SystemClock.uptimeMillis()
                    }
                },
        ) {
            content(shift)
            AnimatedVisibility(visible = dimmed, enter = fadeIn(tween(800)), exit = fadeOut(tween(200))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .testTag("dim_overlay")
                        .semantics { contentDescription = "Screen dimmed. Tap to wake." }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                dimmed = false
                                lastTouch = SystemClock.uptimeMillis()
                            }
                        },
                )
            }
        }
    }
}
