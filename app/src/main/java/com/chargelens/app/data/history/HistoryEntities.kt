package com.chargelens.app.data.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionType { CHARGE, DISCHARGE }

@Entity(tableName = "sessions", indices = [Index("type"), Index("startTime")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SessionType,
    val startTime: Long,
    val endTime: Long? = null,
    /** Last time the row was written; used to close sessions left open by a killed process. */
    val updatedAt: Long = startTime,
    val startPercent: Float,
    val endPercent: Float = startPercent,
    val avgPowerMw: Int = 0,
    val peakPowerMw: Int = 0,
    val peakTempC: Float? = null,
    val plugType: String? = null,
    val screenOnMs: Long = 0,
    val screenOffMs: Long = 0,
    /** Percentage points lost (discharge) while the screen was on / off. */
    val screenOnDropPct: Float = 0f,
    val screenOffDropPct: Float = 0f,
    /** Charge drawn while the screen was on / off, integrated from current. */
    val screenOnMah: Float = 0f,
    val screenOffMah: Float = 0f,
) {
    val durationMs: Long get() = (endTime ?: updatedAt) - startTime
}

@Entity(
    tableName = "samples",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val time: Long,
    val percent: Float,
    val powerMw: Int?,
    val currentMa: Int?,
    val voltageMv: Int?,
    val temperatureC: Float?,
    val screenOn: Boolean,
)
