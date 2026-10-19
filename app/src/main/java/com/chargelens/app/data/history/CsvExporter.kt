package com.chargelens.app.data.history

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SessionDao,
) {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /** Writes sessions followed by a blank line and all samples. Returns rows written. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sessions = dao.allSessions()
        val samples = dao.allSamples()
        val types = sessions.associate { it.id to it.type.name }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
            w.appendLine("# sessions")
            w.appendLine("session_id,type,start,end,start_percent,end_percent,avg_power_mw,peak_power_mw,peak_temp_c,plug,screen_on_ms,screen_off_ms,screen_on_drop_pct,screen_off_drop_pct,screen_on_mah,screen_off_mah")
            sessions.forEach { s ->
                w.appendLine(
                    listOf(
                        s.id, s.type, time(s.startTime), s.endTime?.let(::time).orEmpty(), s.startPercent, s.endPercent,
                        s.avgPowerMw, s.peakPowerMw, s.peakTempC ?: "", s.plugType.orEmpty(), s.screenOnMs, s.screenOffMs,
                        s.screenOnDropPct, s.screenOffDropPct, s.screenOnMah, s.screenOffMah,
                    ).joinToString(","),
                )
            }
            w.appendLine()
            w.appendLine("# samples")
            w.appendLine("session_id,session_type,time,percent,power_mw,current_ma,voltage_mv,temperature_c,screen_on")
            samples.forEach { s ->
                w.appendLine(
                    listOf(
                        s.sessionId, types[s.sessionId].orEmpty(), time(s.time), s.percent, s.powerMw ?: "",
                        s.currentMa ?: "", s.voltageMv ?: "", s.temperatureC ?: "", s.screenOn,
                    ).joinToString(","),
                )
            }
        } ?: error("Could not open $uri")
        sessions.size + samples.size
    }

    private fun time(ms: Long): String = iso.format(Date(ms))
}
