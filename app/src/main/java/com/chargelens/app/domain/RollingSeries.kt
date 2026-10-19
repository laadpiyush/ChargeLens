package com.chargelens.app.domain

import com.chargelens.app.domain.model.MetricStats

/** Fixed-capacity FIFO of floats. */
class RollingSeries(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var start = 0
    var size = 0
        private set

    fun add(value: Float) {
        if (size < capacity) {
            data[(start + size) % capacity] = value
            size++
        } else {
            data[start] = value
            start = (start + 1) % capacity
        }
    }

    fun toList(): List<Float> = List(size) { data[(start + it) % capacity] }

    fun clear() {
        start = 0
        size = 0
    }
}

class StatsAccumulator {
    private var min = Float.POSITIVE_INFINITY
    private var max = Float.NEGATIVE_INFINITY
    private var sum = 0.0
    private var count = 0

    fun add(v: Float) {
        if (v < min) min = v
        if (v > max) max = v
        sum += v
        count++
    }

    fun snapshot(): MetricStats? =
        if (count == 0) null else MetricStats(min, max, (sum / count).toFloat(), count)

    fun reset() {
        min = Float.POSITIVE_INFINITY
        max = Float.NEGATIVE_INFINITY
        sum = 0.0
        count = 0
    }
}
