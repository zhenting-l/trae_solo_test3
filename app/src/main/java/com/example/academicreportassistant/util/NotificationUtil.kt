package com.lzt.summaryofslides.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationUtil {
    const val ChannelId = "analysis_progress"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ChannelId) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "Summary of Slides",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        text: String,
    ) = NotificationCompat.Builder(context, ChannelId)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
}
