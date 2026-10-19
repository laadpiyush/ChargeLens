package com.chargelens.app.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chargelens.app.data.history.SampleEntity
import com.chargelens.app.data.history.SessionDao
import com.chargelens.app.data.history.SessionEntity
import com.chargelens.app.data.history.SessionType
import com.chargelens.app.data.settings.ChargingScreenSettings
import com.chargelens.app.ui.charging.AodScreen
import com.chargelens.app.ui.charging.ScreensaverScreen
import com.chargelens.app.ui.details.DetailsScreen
import com.chargelens.app.ui.details.DetailsTopBar
import com.chargelens.app.ui.details.DetailsUiState
import com.chargelens.app.ui.details.PreviewData
import com.chargelens.app.ui.history.HistoryScreen
import com.chargelens.app.ui.history.HistoryTabsScreen
import com.chargelens.app.ui.history.HistoryViewModel
import com.chargelens.app.ui.navigation.BottomBar
import com.chargelens.app.ui.navigation.Destination
import com.chargelens.app.ui.theme.ChargeLensTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.exp

/**
 * Renders README screenshots from realistic sample data (emulators report fixed battery values).
 * Run only on an emulator:
 *   ./gradlew installDebug installDebugAndroidTest   (with ANDROID_SERIAL set)
 *   adb shell am instrument -w -e class com.chargelens.app.screenshots.ScreenshotTest \
 *       com.chargelens.app.test/androidx.test.runner.AndroidJUnitRunner
 * Images land in /sdcard/Android/data/com.chargelens.app/files/screenshots.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val outDir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun AppFrame(route: String, content: @Composable () -> Unit) {
        ChargeLensTheme {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { BottomBar(route) {} },
            ) { padding -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { content() } }
        }
    }

    @Test fun details() {
        rule.setContent {
            AppFrame(Destination.DETAILS.route) {
                DetailsScreen(PreviewData.charging, onToggleMonitoring = {}, topBar = { DetailsTopBar() })
            }
        }
        save("01_details")
    }

    @Test fun tiles() {
        rule.setContent {
            AppFrame(Destination.DETAILS.route) {
                DetailsScreen(PreviewData.charging, onToggleMonitoring = {}, topBar = { DetailsTopBar() })
            }
        }
        rule.onNodeWithTag("details_grid").performScrollToNode(hasTestTag("tile_CHARGE_STATUS"))
        save("02_tiles")
    }

    @Test fun alwaysOn() {
        rule.setContent {
            ChargeLensTheme(forceBlack = true) {
                AodScreen(PreviewData.charging, ChargingScreenSettings(pixelShift = false), onDismiss = {})
            }
        }
        save("03_always_on")
    }

    @Test fun history() {
        val vm = HistoryViewModel(FakeDao)
        rule.setContent {
            AppFrame(Destination.HISTORY.route) {
                HistoryTabsScreen(charging = { HistoryScreen(vm) }, usage = {})
            }
        }
        save("04_history")
    }

    @Test fun screensaver() {
        rule.setContent {
            ChargeLensTheme(forceBlack = true) {
                ScreensaverScreen(PreviewData.charging, ChargingScreenSettings(pixelShift = false), onDimChange = {})
            }
        }
        save("05_screensaver")
    }

    /** Charge sessions with a realistic fast-charge power curve (tapering after ~70 %). */
    private object FakeDao : SessionDao {
        private const val HOUR = 3_600_000L
        private val now = System.currentTimeMillis()
        private val sessions = listOf(
            SessionEntity(3, SessionType.CHARGE, now - 2 * HOUR, now - HOUR, now - HOUR, 21f, 100f, 18_400, 26_900, 38.1f, "AC"),
            SessionEntity(2, SessionType.CHARGE, now - 26 * HOUR, now - 25 * HOUR, now - 25 * HOUR, 34f, 92f, 14_100, 24_300, 36.4f, "AC"),
            SessionEntity(1, SessionType.CHARGE, now - 50 * HOUR, now - 47 * HOUR, now - 47 * HOUR, 12f, 80f, 6_200, 7_500, 33.0f, "USB"),
        )
        private val samples = List(360) { i ->
            val pct = 21f + 79f * (1 - exp(-i / 110.0)).toFloat()
            val watts = if (pct < 70) 26.5f else 25.3f * exp(-(pct - 70) / 9.0).toFloat() + 1.2f
            SampleEntity(i.toLong(), 3, now - 2 * HOUR + i * 10_000L, pct, (watts * 1000).toInt(), (watts * 1000 / 4.2f).toInt(), 4200, 34f + i * 0.01f, false)
        }

        override suspend fun insertSession(session: SessionEntity) = 0L
        override suspend fun updateSession(session: SessionEntity) = Unit
        override suspend fun deleteSession(id: Long) = Unit
        override suspend fun insertSample(sample: SampleEntity) = Unit
        override fun observeSessions(type: SessionType, limit: Int): Flow<List<SessionEntity>> =
            flowOf(if (type == SessionType.CHARGE) sessions else emptyList())
        override fun observeSamples(sessionId: Long): Flow<List<SampleEntity>> = flowOf(samples.filter { it.sessionId == sessionId })
        override suspend fun closeDanglingSessions() = Unit
        override suspend fun allSessions() = sessions
        override suspend fun allSamples() = samples
        override suspend fun clearAll() = Unit
    }
}
