package com.chargelens.app.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.chargelens.app.domain.model.BatteryCondition
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.PlugType
import com.chargelens.app.domain.model.RawBatteryReading
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the sticky ACTION_BATTERY_CHANGED broadcast and BatteryManager properties. */
@Singleton
class BatteryReader @Inject constructor(@ApplicationContext private val context: Context) {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    fun read(): RawBatteryReading {
        val intent = ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return fromIntent(intent)
    }

    fun fromIntent(intent: Intent?): RawBatteryReading {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        val capacity = property(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        return RawBatteryReading(
            level = if (level >= 0) level else capacity ?: 0,
            scale = if (level >= 0) scale else 100,
            voltage = voltage,
            temperatureTenths = temp,
            plugType = mapPlug(plugged),
            status = mapStatus(status),
            condition = mapHealth(health),
            currentNow = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeCounterUah = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 },
            capacityPercent = capacity,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
        )
    }

    /** Returns null when the property is unsupported (Integer.MIN_VALUE, or an exception on some OEMs). */
    private fun property(id: Int): Int? = try {
        batteryManager?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE }
    } catch (_: Throwable) {
        null
    }

    companion object {
        fun mapPlug(plugged: Int): PlugType = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PlugType.AC
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PlugType.USB
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PlugType.WIRELESS
            plugged and BATTERY_PLUGGED_DOCK != 0 -> PlugType.DOCK
            else -> PlugType.NONE
        }

        fun mapStatus(status: Int): ChargeStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            else -> ChargeStatus.UNKNOWN
        }

        fun mapHealth(health: Int): BatteryCondition = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryCondition.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryCondition.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryCondition.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryCondition.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryCondition.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryCondition.FAILURE
            else -> BatteryCondition.UNKNOWN
        }

        /** BatteryManager.BATTERY_PLUGGED_DOCK, public only from API 33. */
        private const val BATTERY_PLUGGED_DOCK = 8
    }
}
