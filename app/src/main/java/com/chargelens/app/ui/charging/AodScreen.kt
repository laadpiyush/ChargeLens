package com.chargelens.app.ui.charging

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargelens.app.data.settings.ChargingScreenSettings
import com.chargelens.app.ui.details.DetailsUiState
import com.chargelens.app.ui.details.GaugeHero
import com.chargelens.app.ui.theme.LocalExtras
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Always-on view while charging: clock, date and the battery gauge on pure black, slightly faded
 * and drifting a few pixels each minute. A tap dismisses it to the lock screen.
 */
@Composable
fun AodScreen(
    state: DetailsUiState,
    prefs: ChargingScreenSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L * (60 - (value / 1000) % 60)) // tick on the minute
        }
    }
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    val extras = LocalExtras.current

    BurnInSafeContainer(prefs.copy(pixelShift = true, autoDimSeconds = 0), onDimChange = {}, shiftScale = 3) { shift ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }, onDoubleTap = { onDismiss() }) }
                .testTag("aod"),
            contentAlignment = Alignment.Center,
        ) {
            val gaugeSize = minOf(maxWidth, maxHeight) * 0.62f
            Column(
                Modifier
                    .offset { shift }
                    .alpha(0.85f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    timeFormat.format(Date(now)),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = extras.numberFont,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.muted,
                )
                Spacer(Modifier.height(36.dp))
                GaugeHero(state, Modifier.size(gaugeSize), showEstimate = true)
            }
        }
    }
}
