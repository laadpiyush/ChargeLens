package com.chargelens.app.data.history

import android.database.sqlite.SQLiteException
import android.util.Log
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.PlugType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Splits the live stream into charge / discharge sessions and stores them with periodic samples.
 * Driven sequentially from the monitoring service's collector, so no locking is needed.
 */
@Singleton
class SessionRecorder @Inject constructor(private val dao: SessionDao) {

    private var session: SessionEntity? = null
    private var powerSum = 0.0
    private var powerCount = 0
    private var lastSampleTime = 0L
    private var lastPercent = 0f
    private var lastSampleWrite = 0L
    private var lastRowWrite = 0L
    private var initialised = false

    @Volatile
    var screenOn: Boolean = true

    suspend fun onSnapshot(s: BatterySnapshot) {
        try {
            record(s)
        } catch (e: SQLiteException) {
            // e.g. history cleared under an open session (FK failure): start a fresh session.
            Log.w(TAG, "Session write failed, restarting session", e)
            session = null
        }
    }

    private suspend fun record(s: BatterySnapshot) {
        if (!initialised) {
            dao.closeDanglingSessions()
            initialised = true
        }
        val type = if (s.plugType != PlugType.NONE) SessionType.CHARGE else SessionType.DISCHARGE
        val current = session
        if (current == null || current.type != type) {
            current?.let { finish(it, s.timestamp) }
            start(type, s)
            return
        }

        val dt = (s.timestamp - lastSampleTime).coerceIn(0, MAX_GAP_MS)
        val drop = (lastPercent - s.percent).coerceAtLeast(0f)
        val mah = s.currentMa?.let { abs(it) * dt / 3_600_000f } ?: 0f
        var updated = current.copy(
            updatedAt = s.timestamp,
            endPercent = s.percent,
            peakPowerMw = max(current.peakPowerMw, abs(s.powerMw ?: 0)),
            peakTempC = listOfNotNull(current.peakTempC, s.temperatureC).maxOrNull(),
        )
        updated = if (screenOn) {
            updated.copy(screenOnMs = updated.screenOnMs + dt, screenOnDropPct = updated.screenOnDropPct + drop, screenOnMah = updated.screenOnMah + mah)
        } else {
            updated.copy(screenOffMs = updated.screenOffMs + dt, screenOffDropPct = updated.screenOffDropPct + drop, screenOffMah = updated.screenOffMah + mah)
        }
        s.powerMw?.let { powerSum += abs(it); powerCount++ }
        updated = updated.copy(avgPowerMw = if (powerCount > 0) (powerSum / powerCount).toInt() else 0)
        session = updated
        lastSampleTime = s.timestamp
        lastPercent = s.percent

        if (s.timestamp - lastSampleWrite >= SAMPLE_EVERY_MS) {
            dao.insertSample(s.toSample(updated.id))
            lastSampleWrite = s.timestamp
        }
        if (s.timestamp - lastRowWrite >= ROW_EVERY_MS) {
            dao.updateSession(updated)
            lastRowWrite = s.timestamp
        }
    }

    /** Called when monitoring stops so the open session is saved. */
    suspend fun flush() {
        runCatching { session?.let { finish(it, it.updatedAt) } }
        session = null
    }

    private suspend fun start(type: SessionType, s: BatterySnapshot) {
        val entity = SessionEntity(
            type = type,
            startTime = s.timestamp,
            startPercent = s.percent,
            plugType = s.plugType.takeIf { it != PlugType.NONE }?.label,
            peakTempC = s.temperatureC,
            peakPowerMw = abs(s.powerMw ?: 0),
        )
        val id = dao.insertSession(entity)
        session = entity.copy(id = id)
        powerSum = 0.0
        powerCount = 0
        lastSampleTime = s.timestamp
        lastPercent = s.percent
        lastRowWrite = s.timestamp
        dao.insertSample(s.toSample(id))
        lastSampleWrite = s.timestamp
    }

    private suspend fun finish(entity: SessionEntity, end: Long) {
        val tooShort = end - entity.startTime < MIN_SESSION_MS && abs(entity.endPercent - entity.startPercent) < 1f
        if (tooShort) dao.deleteSession(entity.id) else dao.updateSession(entity.copy(endTime = end, updatedAt = end))
    }

    private fun BatterySnapshot.toSample(sessionId: Long) = SampleEntity(
        sessionId = sessionId,
        time = timestamp,
        percent = percent,
        powerMw = powerMw,
        currentMa = currentMa,
        voltageMv = voltageMv,
        temperatureC = temperatureC,
        screenOn = screenOn,
    )

    companion object {
        private const val TAG = "SessionRecorder"
        private const val SAMPLE_EVERY_MS = 10_000L
        private const val ROW_EVERY_MS = 30_000L
        private const val MIN_SESSION_MS = 2 * 60_000L
        /** Gaps longer than this (process death, deep doze) are not counted as screen time. */
        private const val MAX_GAP_MS = 60_000L
    }
}
