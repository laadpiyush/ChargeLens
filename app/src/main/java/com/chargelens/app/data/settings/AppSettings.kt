package com.chargelens.app.data.settings

import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.CurrentUnit
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.SpeedThresholds
import com.chargelens.app.domain.model.TemperatureUnit

enum class ChargingScreenMode(val label: String, val description: String) {
    ACTIVITY("Charging display", "Full-screen view that opens when you plug in, even on the lock screen"),
    SCREENSAVER("Screen saver", "Battery gauge only, shown by Android's screen saver while idle and charging"),
    OVERLAY("Overlay", "Draws over other apps (needs the \"Display over other apps\" permission)"),
}

data class ChargingScreenSettings(
    val enabled: Boolean = true,
    val mode: ChargingScreenMode = ChargingScreenMode.ACTIVITY,
    val dismissKeyguard: Boolean = false,
    val amoled: Boolean = false,
    val pixelShift: Boolean = true,
    /** 0 disables auto-dim. */
    val autoDimSeconds: Int = 0,
    /** Dim always-on gauge + clock whenever the screen turns off while charging. */
    val alwaysOnWhileCharging: Boolean = true,
)

data class NotificationSettings(
    val showWattage: Boolean = true,
    val showTemperature: Boolean = true,
    val updateIntervalSeconds: Int = 5,
)

data class DesignSettings(
    val accentArgb: Long = DEFAULT_ACCENT,
    val gaugeThicknessDp: Float = 18f,
    val monoFont: Boolean = false,
    val amoledBlack: Boolean = false,
    val tileOrder: List<Metric> = Metric.entries,
    val hiddenTiles: Set<Metric> = emptySet(),
) {
    val visibleTiles: List<Metric> get() = tileOrder.filterNot { it in hiddenTiles }

    companion object {
        const val DEFAULT_ACCENT = 0xFFA8D8A0
    }
}

data class AppSettings(
    val monitoringEnabled: Boolean = true,
    /** Sample and record only while a charger is connected; idle (no sampling) on battery. */
    val monitorOnlyWhileCharging: Boolean = true,
    /**
     * With [monitorOnlyWhileCharging]: keep a dormant listener on battery so plugging in opens the
     * charging screen / always-on gauge by itself. Off = nothing runs on battery; monitoring starts
     * when the app is opened while charging and stops on unplug.
     */
    val waitForChargerInBackground: Boolean = false,
    val sampleIntervalMs: Long = 1000L,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val currentUnit: CurrentUnit = CurrentUnit.AUTO,
    val currentSignMode: CurrentSignMode = CurrentSignMode.AUTO,
    val thresholds: SpeedThresholds = SpeedThresholds(),
    /** User-entered design capacity; overrides the PowerProfile value. */
    val designCapacityOverrideMah: Int? = null,
    /** Persisted full-charge capacity estimate (EMA). */
    val estimatedCapacityMah: Float? = null,
    val chargingScreen: ChargingScreenSettings = ChargingScreenSettings(),
    val notification: NotificationSettings = NotificationSettings(),
    val design: DesignSettings = DesignSettings(),
) {
    /** Whether the monitoring service should be running right now. */
    fun shouldMonitor(plugged: Boolean): Boolean =
        monitoringEnabled && (plugged || !monitorOnlyWhileCharging || waitForChargerInBackground)
}

