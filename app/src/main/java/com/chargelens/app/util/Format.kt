package com.chargelens.app.util

import com.chargelens.app.domain.BatteryMath
import com.chargelens.app.domain.model.TemperatureUnit
import java.util.Locale
import kotlin.math.abs

object Format {
    fun percent(p: Float): String = String.format(Locale.US, "%.2f", p)

    fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()

    fun milliWatts(mw: Int): String = "${signed(mw)} mW"

    fun watts(mw: Int): String = String.format(Locale.US, "%+.1f W", mw / 1000f).replace("+0.0", "0.0").replace("-0.0", "0.0")

    fun temperatureValue(c: Float, unit: TemperatureUnit): Float =
        if (unit == TemperatureUnit.FAHRENHEIT) BatteryMath.celsiusToFahrenheit(c) else c

    fun temperature(c: Float, unit: TemperatureUnit): String =
        String.format(Locale.US, "%.1f%s", temperatureValue(c, unit), unitSymbol(unit))

    fun unitSymbol(unit: TemperatureUnit) = if (unit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"

    fun duration(minutes: Int): String {
        val m = abs(minutes)
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${m % 60} min"
        }
    }

    fun durationMs(ms: Long): String = duration((ms / 60_000L).toInt())

    fun oneDecimal(v: Float): String = String.format(Locale.US, "%.1f", v)
}
