package com.chargelens.app.domain

import com.chargelens.app.domain.model.ChargeSpeed
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.SpeedThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryMathTest {

    @Test fun `voltage is normalised to millivolts`() {
        assertEquals(3994, BatteryMath.normalizeVoltageMv(3994))
        assertEquals(4000, BatteryMath.normalizeVoltageMv(4))          // reported in volts
        assertEquals(3994, BatteryMath.normalizeVoltageMv(3_994_000))  // reported in µV
        assertNull(BatteryMath.normalizeVoltageMv(0))
        assertNull(BatteryMath.normalizeVoltageMv(null))
    }

    @Test fun `power is volts times amps`() {
        // 3994 mV × 3073 mA = 12273.6 mW
        assertEquals(12273.56f, BatteryMath.powerMw(3994, 3073f), 0.01f)
        assertEquals(-1647.52f, BatteryMath.powerMw(3994, -412.5f), 0.01f)
    }

    @Test fun `unit detection by magnitude`() {
        assertTrue(BatteryMath.looksLikeMicroAmps(3_073_000))
        assertTrue(BatteryMath.looksLikeMicroAmps(-450_000))
        assertFalse(BatteryMath.looksLikeMicroAmps(3073))
        assertEquals(3073f, BatteryMath.toMilliAmps(3_073_000, isMicroAmps = true), 0.001f)
        assertEquals(3073f, BatteryMath.toMilliAmps(3073, isMicroAmps = false), 0.001f)
    }

    @Test fun `auto sign follows status`() {
        // Device reports charging as negative (common on Samsung / some Qualcomm kernels).
        assertEquals(1500f, BatteryMath.applySign(-1500f, ChargeStatus.CHARGING, CurrentSignMode.AUTO, null), 0f)
        assertEquals(-400f, BatteryMath.applySign(400f, ChargeStatus.DISCHARGING, CurrentSignMode.AUTO, null), 0f)
        assertEquals(-400f, BatteryMath.applySign(-400f, ChargeStatus.DISCHARGING, CurrentSignMode.AUTO, null), 0f)
        assertEquals(-200f, BatteryMath.applySign(200f, ChargeStatus.NOT_CHARGING, CurrentSignMode.AUTO, null), 0f)
    }

    @Test fun `full status uses the learned device convention`() {
        assertEquals(-30f, BatteryMath.applySign(30f, ChargeStatus.FULL, CurrentSignMode.AUTO, deviceInverted = true), 0f)
        assertEquals(30f, BatteryMath.applySign(30f, ChargeStatus.FULL, CurrentSignMode.AUTO, deviceInverted = false), 0f)
        assertEquals(30f, BatteryMath.applySign(30f, ChargeStatus.FULL, CurrentSignMode.AUTO, deviceInverted = null), 0f)
    }

    @Test fun `manual sign overrides ignore status`() {
        assertEquals(-1500f, BatteryMath.applySign(-1500f, ChargeStatus.CHARGING, CurrentSignMode.AS_REPORTED, null), 0f)
        assertEquals(1500f, BatteryMath.applySign(-1500f, ChargeStatus.CHARGING, CurrentSignMode.INVERTED, null), 0f)
    }

    @Test fun `capacity sample from charge counter and percent`() {
        // 2,230,000 µAh at 45 % → 4955.6 mAh
        assertEquals(4955.56f, BatteryMath.capacitySampleMah(2_230_000, 45)!!, 0.01f)
        assertNull(BatteryMath.capacitySampleMah(2_230_000, 10)) // too imprecise at low %
        assertNull(BatteryMath.capacitySampleMah(null, 50))
        assertNull(BatteryMath.capacitySampleMah(0, 50))
    }

    @Test fun `precise percent uses charge counter and falls back to level`() {
        assertEquals(45.48f, BatteryMath.precisePercent(2_230_000, 4903f, 45, 100), 0.01f)
        assertEquals(45f, BatteryMath.precisePercent(null, 4903f, 45, 100), 0f)
        assertEquals(45f, BatteryMath.precisePercent(2_230_000, null, 45, 100), 0f)
        // Implausible result (charge counter in odd units) → integer level.
        assertEquals(45f, BatteryMath.precisePercent(223_000, 4903f, 45, 100), 0f)
        // Non-100 scale.
        assertEquals(50f, BatteryMath.precisePercent(null, null, 128, 256), 0f)
    }

    @Test fun `shown percent never goes backwards against the current`() {
        assertEquals(45.30f, BatteryMath.monotonicPercent(45.30f, 45.28f, 3000f), 0f) // charging: hold
        assertEquals(45.35f, BatteryMath.monotonicPercent(45.30f, 45.35f, 3000f), 0f) // charging: rise
        assertEquals(45.30f, BatteryMath.monotonicPercent(45.30f, 45.33f, -400f), 0f) // discharging: hold
        assertEquals(45.20f, BatteryMath.monotonicPercent(45.30f, 45.20f, -400f), 0f)
        assertEquals(43.00f, BatteryMath.monotonicPercent(45.30f, 43.00f, 3000f), 0f) // big jump accepted
        assertEquals(45.28f, BatteryMath.monotonicPercent(null, 45.28f, 3000f), 0f)
    }

    @Test fun `ema smooths towards samples`() {
        assertEquals(100f, BatteryMath.ema(null, 100f, 0.1f), 0f)
        assertEquals(101f, BatteryMath.ema(100f, 110f, 0.1f), 0.0001f)
    }

    @Test fun `health is capped at 100 percent`() {
        assertEquals(100f, BatteryMath.healthPercent(4903f, 4855)!!, 0f)
        assertEquals(80f, BatteryMath.healthPercent(4000f, 5000)!!, 0.001f)
        assertNull(BatteryMath.healthPercent(4000f, null))
        assertNull(BatteryMath.healthPercent(null, 5000))
    }

    @Test fun `charge speed thresholds`() {
        val t = SpeedThresholds()
        fun speed(w: Float) = BatteryMath.chargeSpeed(1000f, w, ChargeStatus.CHARGING, plugged = true, thresholds = t)
        assertEquals(ChargeSpeed.SLOW, speed(4.9f))
        assertEquals(ChargeSpeed.NORMAL, speed(5f))
        assertEquals(ChargeSpeed.FAST, speed(12.3f))
        assertEquals(ChargeSpeed.SUPER_FAST, speed(25f))
        assertEquals(ChargeSpeed.DISCHARGING, BatteryMath.chargeSpeed(-300f, -1.2f, ChargeStatus.DISCHARGING, false, t))
        assertEquals(ChargeSpeed.FULL, BatteryMath.chargeSpeed(10f, 0.04f, ChargeStatus.FULL, true, t))
        // Plugged but the phone draws more than the charger supplies.
        assertEquals(ChargeSpeed.DISCHARGING, BatteryMath.chargeSpeed(-100f, -0.4f, ChargeStatus.CHARGING, true, t))
    }

    @Test fun `time estimates`() {
        // 2500 mAh missing at 2500 mA → 60 min
        assertEquals(60, BatteryMath.timeToFullMinutes(2500f, 5000f, 2500f))
        assertNull(BatteryMath.timeToFullMinutes(2500f, 5000f, -300f))
        // 2500 mAh left at -500 mA → 300 min
        assertEquals(300, BatteryMath.timeRemainingMinutes(2500f, -500f))
        assertNull(BatteryMath.timeRemainingMinutes(2500f, 800f))
    }

    @Test fun `fahrenheit conversion`() {
        assertEquals(99.68f, BatteryMath.celsiusToFahrenheit(37.6f), 0.01f)
    }
}
