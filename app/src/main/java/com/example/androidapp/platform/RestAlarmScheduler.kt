package com.example.androidapp.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.androidapp.domain.RestNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires the "rest over" notification with a single `AlarmManager` alarm
 * (ROADMAP P1.4).
 *
 * One alarm at a known instant beats keeping a process alive: no foreground
 * service, no service type, no wakelock. The cost is precision — exact alarms
 * need `SCHEDULE_EXACT_ALARM`, which the user may not grant, so this degrades to
 * an inexact alarm rather than failing. A rest timer that is a minute late is
 * still useful; one that never fires is not. Making this exact always would
 * require the P4.2 foreground service.
 */
@Singleton
class RestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestNotifier {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant) {
        val manager = alarmManager ?: return
        val triggerAtMillis = endsAt.toEpochMilli()

        // canScheduleExactAlarms() is API 31+; below that exact alarms are ordinary.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        if (canBeExact) {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent(),
            )
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent())
        }
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    /** `FLAG_UPDATE_CURRENT` so rescheduling replaces rather than stacks alarms. */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 1001
    }
}

