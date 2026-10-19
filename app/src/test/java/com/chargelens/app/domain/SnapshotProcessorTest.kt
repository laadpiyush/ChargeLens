package com.chargelens.app.domain

import com.chargelens.app.domain.model.BatteryCondition
import com.chargelens.app.domain.model.ChargeSpeed
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.CurrentUnit
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.PlugType
import com.chargelens.app.domain.model.RawBatteryReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotProcessorTest {

    private fun reading(
        current: Int? = 3_073_000,
        status: ChargeStatus = ChargeStatus.CHARGING,
        plug: PlugType = PlugType.AC,
        counter: Int? = 2_230_000,
        capacity: Int? = 45,
        voltage: Int? = 3994,
    ) = RawBatteryReading(
        level = capacity ?: 45, scale = 100, voltage = voltage, temperatureTenths = 376,
        plugType = plug, status = status, condition = BatteryCondition.GOOD,
        currentNow = current, chargeCounterUah = counter, capacityPercent = capacity,
    )

    @Test fun `produces signed power and status label`() {
        val p = SnapshotProcessor(initialMaxCapacityMah = 4903f)
        val s = p.process(reading(), ProcessingConfig(designCapacityMah = 4855), now = 1L).snapshot!!
        assertEquals(3073, s.currentMa)
        assertEquals(3994, s.voltageMv)
        assertEquals(12274, s.powerMw)
        assertEquals(37.6f, s.temperatureC!!, 0.001f)
        assertEquals(ChargeSpeed.FAST, s.speed)
        assertEquals(100f, s.healthPercent!!, 0f)
    }

    @Test fun `inverted-sign device still reads positive while charging`() {
        val p = SnapshotProcessor()
        val s = p.process(reading(current = -3_073_000), ProcessingConfig(), 1L).snapshot!!
        assertEquals(3073, s.currentMa)
        assertTrue(s.powerMw!! > 0)
    }

    @Test fun `discharging current is negative regardless of reported sign`() {
        val p = SnapshotProcessor()
        val s = p.process(reading(current = 450_000, status = ChargeStatus.DISCHARGING, plug = PlugType.NONE), ProcessingConfig(), 1L).snapshot!!
        assertEquals(-450, s.currentMa)
        assertEquals(ChargeSpeed.DISCHARGING, s.speed)
    }

    @Test fun `milliamp devices are detected and stay detected`() {
        val p = SnapshotProcessor()
        assertEquals(1800, p.process(reading(current = 1800), ProcessingConfig(), 1L).snapshot!!.currentMa)
        // Once a µA-sized value is seen, small values are read as µA too.
        p.process(reading(current = 2_000_000), ProcessingConfig(), 2L)
        assertEquals(5, p.process(reading(current = 5000), ProcessingConfig(), 3L).snapshot!!.currentMa)
    }

    @Test fun `unit override wins over auto-detect`() {
        val p = SnapshotProcessor()
        val s = p.process(reading(current = 1800), ProcessingConfig(currentUnit = CurrentUnit.MICRO_AMPS), 1L).snapshot!!
        assertEquals(2, s.currentMa) // 1800 µA → 1.8 mA, rounded
    }

    @Test fun `as-reported mode keeps the raw sign`() {
        val p = SnapshotProcessor()
        val s = p.process(reading(current = -3_073_000), ProcessingConfig(signMode = CurrentSignMode.AS_REPORTED), 1L).snapshot!!
        assertEquals(-3073, s.currentMa)
    }

    @Test fun `capacity estimate converges and yields precise percent`() {
        val p = SnapshotProcessor()
        repeat(200) { p.process(reading(counter = 2_450_000, capacity = 50), ProcessingConfig(), it.toLong()) }
        assertEquals(4900f, p.maxCapacityEstimate!!, 1f)
        val s = p.process(reading(counter = 2_229_000, capacity = 45), ProcessingConfig(), 300L).snapshot!!
        assertEquals(45.5f, s.percent, 0.2f)
    }

    @Test fun `percent does not wobble while charging on a stepped fuel gauge`() {
        // Samsung-style: counter updates in ~14.8 mAh steps every ~9 s, level ticks independently.
        val p = SnapshotProcessor(initialMaxCapacityMah = 4990f)
        var counter = 4_048_350
        var level = 81
        var last = 0f
        for (t in 0 until 300) {
            // The level lags the counter (it is computed from the previous reading) and its
            // rounding differs slightly from our capacity estimate, as on a real gauge.
            level = (counter / 1000f / 4955f * 100f).toInt()
            if (t % 9 == 0 && t > 0) counter += 14_775
            val s = p.process(reading(counter = counter, capacity = level), ProcessingConfig(), t.toLong()).snapshot!!
            assertTrue("percent went down at t=$t: $last -> ${s.percent}", s.percent >= last)
            last = s.percent
        }
    }

    @Test fun `capacity estimate only moves when the level ticks`() {
        val p = SnapshotProcessor(initialMaxCapacityMah = 5000f)
        repeat(30) { p.process(reading(counter = 4_048_350, capacity = 81), ProcessingConfig(), it.toLong()) }
        assertEquals(5000f, p.maxCapacityEstimate!!, 0f)
        p.process(reading(counter = 4_063_125, capacity = 82), ProcessingConfig(), 31L)
        // One EMA step towards 4063.1 / 0.82 = 4955 mAh.
        assertEquals(4995.5f, p.maxCapacityEstimate!!, 1f)
    }

    @Test fun `missing sensors produce nulls not crashes`() {
        val p = SnapshotProcessor()
        val s = p.process(reading(current = null, counter = null, capacity = null, voltage = null), ProcessingConfig(), 1L).snapshot!!
        assertNull(s.currentMa)
        assertNull(s.powerMw)
        assertNull(s.voltageMv)
        assertNull(s.maxCapacityMah)
        assertNull(s.healthPercent)
        assertEquals(45f, s.percent, 0f)
    }

    @Test fun `session stats reset when plug state changes`() {
        val p = SnapshotProcessor()
        repeat(5) { p.process(reading(), ProcessingConfig(), it.toLong()) }
        val charging = p.process(reading(), ProcessingConfig(), 5L)
        assertEquals(6, charging.stats[Metric.CURRENT]!!.count)
        val unplugged = p.process(reading(current = 400_000, status = ChargeStatus.DISCHARGING, plug = PlugType.NONE), ProcessingConfig(), 6L)
        assertEquals(1, unplugged.stats[Metric.CURRENT]!!.count)
        assertEquals(6L, unplugged.sessionStartedAt)
        // Rolling series keeps history across the change.
        assertEquals(7, unplugged.series[Metric.CURRENT]!!.size)
    }

    @Test fun `rolling series is capped`() {
        val p = SnapshotProcessor(historySize = 10)
        repeat(25) { p.process(reading(), ProcessingConfig(), it.toLong()) }
        assertEquals(10, p.process(reading(), ProcessingConfig(), 26L).series[Metric.VOLTAGE]!!.size)
    }
}
