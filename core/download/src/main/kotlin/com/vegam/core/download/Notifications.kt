package com.vegam.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.vegam.engine.Formatters

/** Download notification: naam, progress bar, "speed · ETA" aur Pause button. */
object Notifications {
    const val CHANNEL_ID = "vegam_downloads"
    private const val BASE_ID = 1000

    /** App.onCreate mein ek baar. LOW importance = bina awaaz ke, progress update par buzz nahi. */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Chalti hui downloads ka progress"
                    setShowBadge(false)
                }
            )
        }
    }

    fun foregroundInfo(
        context: Context, id: Long, name: String,
        downloaded: Long, total: Long, bytesPerSec: Long,
    ): ForegroundInfo {
        val notification = build(context, id, name, downloaded, total, bytesPerSec)
        val nid = BASE_ID + id.toInt()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(nid, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(nid, notification)
    }

    private fun build(
        context: Context, id: Long, name: String,
        downloaded: Long, total: Long, bytesPerSec: Long,
    ): Notification {
        val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
        val eta = if (bytesPerSec > 0 && total > 0) (total - downloaded) / bytesPerSec else null
        val pause = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, PauseReceiver::class.java).putExtra(PauseReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)   // Phase 4 mein Vegam "V" icon
            .setContentTitle(name)
            .setContentText("$pct% · ${Formatters.speed(bytesPerSec)} · ETA ${Formatters.eta(eta)}")
            .setProgress(100, pct, total <= 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Pause", pause)
            .build()
    }
}
