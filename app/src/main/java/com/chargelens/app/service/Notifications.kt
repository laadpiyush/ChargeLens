package com.chargelens.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chargelens.app.MainActivity
import com.chargelens.app.R
import com.chargelens.app.data.settings.AppSettings
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.ui.charging.ChargingActivity
import com.chargelens.app.util.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, context.getString(R.string.channel_monitoring), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_monitoring_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_IDLE, "Waiting for charger", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Minimised notification while ChargeLens waits for a charger"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHARGING, context.getString(R.string.channel_charging), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_charging_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    fun monitoringNotification(snapshot: BatterySnapshot?, settings: AppSettings): Notification {
        val title = snapshot?.let { "${Format.percent(it.percent)}% · ${it.speed.label.lowercase().replaceFirstChar(Char::uppercase)}" }
            ?: "Monitoring battery"
        val parts = buildList {
            if (snapshot != null) {
                if (settings.notification.showWattage) snapshot.powerMw?.let { add(Format.watts(it)) }
                if (settings.notification.showTemperature) snapshot.temperatureC?.let { add(Format.temperature(it, settings.temperatureUnit)) }
                snapshot.timeToFullMinutes?.takeIf { it > 0 && snapshot.isPlugged }?.let { add("Full in ${Format.duration(it)}") }
                snapshot.timeRemainingMinutes?.takeIf { !snapshot.isPlugged }?.let { add("${Format.duration(it)} left") }
            }
        }
        val pause = PendingIntent.getService(
            context, 1,
            Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(parts.joinToString(" · ").ifEmpty { "Live battery stats" })
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Pause monitoring", pause)
            .build()
    }

    /** Shown while idle on battery ("only while charging"); silent and minimised. */
    fun idleNotification(): Notification {
        val pause = PendingIntent.getService(
            context, 1,
            Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_IDLE)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Waiting for charger")
            .setContentText("Monitoring starts when you plug in")
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Turn off", pause)
            .build()
    }

    fun updateMonitoring(notification: Notification) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(ID_MONITORING, notification)
        }
    }

    /**
     * High-priority notification whose full-screen intent opens [ChargingActivity]. With the screen
     * off or locked the system launches the activity directly; otherwise it shows a heads-up that
     * opens it on tap.
     */
    fun showChargingPrompt(snapshot: BatterySnapshot?, target: Intent = ChargingActivity.intent(context)) {
        val intent = PendingIntent.getActivity(
            context, 2, target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_CHARGING)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Charging")
            .setContentText(snapshot?.let { "${Format.percent(it.percent)}% · tap to open the charging screen" } ?: "Tap to open the charging screen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
        if (canUseFullScreenIntent()) builder.setFullScreenIntent(intent, true)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // A full-screen intent only fires when the notification is newly posted.
            manager.cancel(ID_CHARGING)
            manager.notify(ID_CHARGING, builder.build())
        }
    }

    fun cancelChargingPrompt() = manager.cancel(ID_CHARGING)

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_MONITORING = "monitoring"
        const val CHANNEL_CHARGING = "charging_screen"
        const val CHANNEL_IDLE = "idle"
        const val ID_MONITORING = 1001
        const val ID_CHARGING = 1002
    }
}
