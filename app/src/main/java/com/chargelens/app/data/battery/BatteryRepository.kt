package com.chargelens.app.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.chargelens.app.data.settings.AppSettings
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.di.ApplicationScope
import com.chargelens.app.domain.ProcessingConfig
import com.chargelens.app.domain.SnapshotProcessor
import com.chargelens.app.domain.model.LiveBatteryState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Single source of live battery data. Sampling runs only while something collects [state]
 * (visible UI via repeatOnLifecycle, or the monitoring service), and slows down while the screen
 * is off so the service costs as little as possible.
 */
@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: BatteryReader,
    private val settingsRepository: SettingsRepository,
    private val designCapacityReader: DesignCapacityReader,
    @ApplicationScope scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val wakeFilter = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }

    // Survives collector restarts so rolling series and the capacity EMA are not lost when the
    // UI goes to the background and comes back.
    private var processor: SnapshotProcessor? = null
    private var lastPersistedCapacity: Float? = null
    private var lastPersistAt = 0L

    val state: StateFlow<LiveBatteryState> = flow {
        val initial = settingsRepository.load()
        val proc = processor ?: SnapshotProcessor(initial.estimatedCapacityMah).also {
            processor = it
            lastPersistedCapacity = initial.estimatedCapacityMah
        }
        // Wake the loop early on screen-on / plug changes so the slow screen-off cadence never
        // leaves the charging screen showing stale values.
        val wake = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                wake.trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(context, receiver, wakeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            while (currentCoroutineContext().isActive) {
                val settings = settingsRepository.settings.value
                emit(proc.process(reader.read(), settings.toProcessingConfig(), System.currentTimeMillis()))
                maybePersistCapacity(proc.maxCapacityEstimate)
                withTimeoutOrNull(nextDelay(settings)) { wake.receive() }
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 3_000), LiveBatteryState.Empty)

    val designCapacityMah: Int? get() = designCapacityReader.powerProfileCapacityMah

    /** Forgets the learned full-charge capacity (e.g. after a battery replacement). */
    suspend fun resetCapacityEstimate() {
        processor?.resetCapacityEstimate()
        lastPersistedCapacity = null
        settingsRepository.setEstimatedCapacity(null)
    }

    private fun AppSettings.toProcessingConfig() = ProcessingConfig(
        currentUnit = currentUnit,
        signMode = currentSignMode,
        thresholds = thresholds,
        designCapacityMah = designCapacityOverrideMah ?: designCapacityReader.powerProfileCapacityMah,
    )

    private fun nextDelay(settings: AppSettings): Long {
        val interactive = powerManager?.isInteractive ?: true
        return if (interactive) settings.sampleIntervalMs else max(settings.sampleIntervalMs, SCREEN_OFF_INTERVAL_MS)
    }

    private suspend fun maybePersistCapacity(estimate: Float?) {
        if (estimate == null) return
        val now = SystemClock.elapsedRealtime()
        val last = lastPersistedCapacity
        if (last == null || (abs(estimate - last) >= 5f && now - lastPersistAt >= 60_000)) {
            settingsRepository.setEstimatedCapacity(estimate)
            lastPersistedCapacity = estimate
            lastPersistAt = now
        }
    }

    companion object {
        private const val SCREEN_OFF_INTERVAL_MS = 15_000L
    }
}
