package com.chargelens.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.ui.components.MonitoringState
import com.chargelens.app.ui.details.DetailsScreen
import com.chargelens.app.ui.details.PreviewData
import com.chargelens.app.ui.theme.ChargeLensTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailsScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun showsGaugeMonitoringCardAndAllTiles() {
        rule.setContent {
            ChargeLensTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DetailsScreen(state = PreviewData.charging, onToggleMonitoring = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("percent", useUnmergedTree = true).assertTextEquals("45.48")
        rule.onNodeWithTag("status_label", useUnmergedTree = true).assertTextEquals("FAST CHARGING")
        rule.onNodeWithText("+12274 mW", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Monitoring Active").assertIsDisplayed()

        val expected = mapOf(
            Metric.VOLTAGE to "3994 mV",
            Metric.CURRENT to "+3073 mA",
            Metric.WATTAGE to "+12.3 W",
            Metric.TEMPERATURE to "37.6°C",
            Metric.HEALTH to "100%",
            Metric.PLUGGED to "AC",
            Metric.MAX_CAPACITY to "4903 mAh",
            Metric.CHARGE_STATUS to "Charging",
        )
        expected.forEach { (metric, value) ->
            rule.onNodeWithTag("details_grid").performScrollToNode(hasTestTag("tile_${metric.name}"))
            rule.onNodeWithTag("tile_${metric.name}").assertIsDisplayed()
            rule.onNodeWithText(value, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun monitoringToggleInvokesCallbackAndUpdatesCard() {
        var active by mutableStateOf(true)
        var toggles = 0
        rule.setContent {
            ChargeLensTheme {
                DetailsScreen(
                    state = PreviewData.charging.copy(monitoring = if (active) MonitoringState.ACTIVE else MonitoringState.OFF),
                    onToggleMonitoring = { toggles++; active = !active },
                )
            }
        }
        rule.onNodeWithText("Turn off").performClick()
        assertEquals(1, toggles)
        rule.onNodeWithText("Monitoring Paused").assertIsDisplayed()
        rule.onNodeWithText("Turn on").assertIsDisplayed()
    }

    @Test
    fun dischargingShowsDischargingLabel() {
        rule.setContent {
            ChargeLensTheme { DetailsScreen(state = PreviewData.discharging, onToggleMonitoring = {}) }
        }
        rule.onNodeWithTag("status_label", useUnmergedTree = true).assertTextEquals("DISCHARGING")
    }
}
