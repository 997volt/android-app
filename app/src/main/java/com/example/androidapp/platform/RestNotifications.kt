package com.example.androidapp.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.androidapp.MainActivity
import com.example.androidapp.R

/**
 * The "rest is over" notification (ROADMAP P1.4).
 *
 * Posted from a broadcast receiver, so nothing has to stay running while the
 * user rests. If `POST_NOTIFICATIONS` has not been granted the post is a no-op,
 * which is the correct degraded behaviour: the timer still runs in the app.
 */
internal object RestNotifications {

    const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1

    fun notifyRestOver(context: Context) {
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.rest_over_title))
            .setContentText(context.getString(R.string.rest_over_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        // NotificationManagerCompat.notify throws without the permission on
        // API 33+, so check it rather than crashing on a background thread.
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.rest_channel_name),
                // Default importance: a rest ending matters, but it is not an alarm.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.rest_channel_description) },
        )
    }
}
