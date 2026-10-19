package com.chargelens.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chargelens.app.di.ApplicationScope
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.CurrentUnit
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.SpeedThresholds
import com.chargelens.app.domain.model.TemperatureUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = context.dataStore

    val flow: Flow<AppSettings> = store.data.map { it.toSettings() }

    /** Hot copy for synchronous reads (receivers, sampler loop). Starts with defaults until loaded. */
    val settings: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun load(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    suspend fun setEstimatedCapacity(mah: Float?) = store.edit {
        if (mah == null) it.remove(K.estCapacity) else it[K.estCapacity] = mah
    }

    private object K {
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val onlyCharging = booleanPreferencesKey("monitor_only_while_charging")
        val waitInBackground = booleanPreferencesKey("wait_for_charger_in_background")
        val interval = longPreferencesKey("sample_interval_ms")
        val tempUnit = stringPreferencesKey("temperature_unit")
        val currentUnit = stringPreferencesKey("current_unit")
        val signMode = stringPreferencesKey("current_sign_mode")
        val slow = floatPreferencesKey("threshold_slow")
        val fast = floatPreferencesKey("threshold_fast")
        val superFast = floatPreferencesKey("threshold_super_fast")
        val designCap = intPreferencesKey("design_capacity_override")
        val estCapacity = floatPreferencesKey("estimated_capacity")

        val csEnabled = booleanPreferencesKey("cs_enabled")
        val csMode = stringPreferencesKey("cs_mode")
        val csKeyguard = booleanPreferencesKey("cs_dismiss_keyguard")
        val csAmoled = booleanPreferencesKey("cs_amoled")
        val csPixelShift = booleanPreferencesKey("cs_pixel_shift")
        val csDim = intPreferencesKey("cs_auto_dim_seconds")
        val csAlwaysOn = booleanPreferencesKey("cs_always_on")

        val nWatt = booleanPreferencesKey("notif_wattage")
        val nTemp = booleanPreferencesKey("notif_temperature")
        val nInterval = intPreferencesKey("notif_interval_s")

        val accent = longPreferencesKey("design_accent")
        val thickness = floatPreferencesKey("design_gauge_thickness")
        val mono = booleanPreferencesKey("design_mono")
        val amoledBlack = booleanPreferencesKey("design_amoled")
        val tileOrder = stringPreferencesKey("design_tile_order")
        val hiddenTiles = stringPreferencesKey("design_hidden_tiles")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            monitoringEnabled = this[K.monitoring] ?: d.monitoringEnabled,
            monitorOnlyWhileCharging = this[K.onlyCharging] ?: d.monitorOnlyWhileCharging,
            waitForChargerInBackground = this[K.waitInBackground] ?: d.waitForChargerInBackground,
            sampleIntervalMs = this[K.interval] ?: d.sampleIntervalMs,
            temperatureUnit = enumOr(this[K.tempUnit], d.temperatureUnit),
            currentUnit = enumOr(this[K.currentUnit], d.currentUnit),
            currentSignMode = enumOr(this[K.signMode], d.currentSignMode),
            thresholds = SpeedThresholds(
                slowBelowW = this[K.slow] ?: d.thresholds.slowBelowW,
                fastFromW = this[K.fast] ?: d.thresholds.fastFromW,
                superFastFromW = this[K.superFast] ?: d.thresholds.superFastFromW,
            ),
            designCapacityOverrideMah = this[K.designCap],
            estimatedCapacityMah = this[K.estCapacity],
            chargingScreen = ChargingScreenSettings(
                enabled = this[K.csEnabled] ?: d.chargingScreen.enabled,
                mode = enumOr(this[K.csMode], d.chargingScreen.mode),
                dismissKeyguard = this[K.csKeyguard] ?: d.chargingScreen.dismissKeyguard,
                amoled = this[K.csAmoled] ?: d.chargingScreen.amoled,
                pixelShift = this[K.csPixelShift] ?: d.chargingScreen.pixelShift,
                autoDimSeconds = this[K.csDim] ?: d.chargingScreen.autoDimSeconds,
                alwaysOnWhileCharging = this[K.csAlwaysOn] ?: d.chargingScreen.alwaysOnWhileCharging,
            ),
            notification = NotificationSettings(
                showWattage = this[K.nWatt] ?: d.notification.showWattage,
                showTemperature = this[K.nTemp] ?: d.notification.showTemperature,
                updateIntervalSeconds = this[K.nInterval] ?: d.notification.updateIntervalSeconds,
            ),
            design = DesignSettings(
                accentArgb = this[K.accent] ?: d.design.accentArgb,
                gaugeThicknessDp = this[K.thickness] ?: d.design.gaugeThicknessDp,
                monoFont = this[K.mono] ?: d.design.monoFont,
                amoledBlack = this[K.amoledBlack] ?: d.design.amoledBlack,
                tileOrder = parseTileOrder(this[K.tileOrder]),
                hiddenTiles = this[K.hiddenTiles].orEmpty().split(',')
                    .mapNotNull { name -> Metric.entries.firstOrNull { it.name == name } }.toSet(),
            ),
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[K.monitoring] = s.monitoringEnabled
        this[K.onlyCharging] = s.monitorOnlyWhileCharging
        this[K.waitInBackground] = s.waitForChargerInBackground
        this[K.interval] = s.sampleIntervalMs
        this[K.tempUnit] = s.temperatureUnit.name
        this[K.currentUnit] = s.currentUnit.name
        this[K.signMode] = s.currentSignMode.name
        this[K.slow] = s.thresholds.slowBelowW
        this[K.fast] = s.thresholds.fastFromW
        this[K.superFast] = s.thresholds.superFastFromW
        if (s.designCapacityOverrideMah == null) remove(K.designCap) else this[K.designCap] = s.designCapacityOverrideMah
        if (s.estimatedCapacityMah == null) remove(K.estCapacity) else this[K.estCapacity] = s.estimatedCapacityMah

        this[K.csEnabled] = s.chargingScreen.enabled
        this[K.csMode] = s.chargingScreen.mode.name
        this[K.csKeyguard] = s.chargingScreen.dismissKeyguard
        this[K.csAmoled] = s.chargingScreen.amoled
        this[K.csPixelShift] = s.chargingScreen.pixelShift
        this[K.csDim] = s.chargingScreen.autoDimSeconds
        this[K.csAlwaysOn] = s.chargingScreen.alwaysOnWhileCharging

        this[K.nWatt] = s.notification.showWattage
        this[K.nTemp] = s.notification.showTemperature
        this[K.nInterval] = s.notification.updateIntervalSeconds

        this[K.accent] = s.design.accentArgb
        this[K.thickness] = s.design.gaugeThicknessDp
        this[K.mono] = s.design.monoFont
        this[K.amoledBlack] = s.design.amoledBlack
        this[K.tileOrder] = s.design.tileOrder.joinToString(",") { it.name }
        this[K.hiddenTiles] = s.design.hiddenTiles.joinToString(",") { it.name }
    }

    private fun parseTileOrder(raw: String?): List<Metric> {
        val parsed = raw.orEmpty().split(',').mapNotNull { n -> Metric.entries.firstOrNull { it.name == n } }.distinct()
        // Append tiles added in newer versions so nothing silently disappears.
        return parsed + Metric.entries.filterNot { it in parsed }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
