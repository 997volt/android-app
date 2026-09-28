package com.example.androidapp.ui.workout

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

private const val PREFS_NAME = "rest_alert_permissions"
private const val KEY_ASKED = "asked"

/**
 * Asks for the two permissions the rest alert needs (ROADMAP F13).
 *
 * Both are declared in the manifest but were never requested, which on API 33+
 * means `POST_NOTIFICATIONS` is denied by default and the rest-over notification
 * can never appear — the alert was effectively dead on modern devices. On API 31+
 * the exact alarm silently degraded to inexact forever, because the user was
 * never sent to the settings screen that grants it.
 *
 * Two deliberate choices:
 *
 *  - **Asked on the first logged set**, not at launch. That is the moment the
 *    request has an obvious reason, and a cold prompt on first open is the one
 *    users reflexively decline.
 *  - **Refusal is non-fatal.** The in-app timer keeps working and the alert
 *    degrades to inexact; nothing here needs to succeed for a workout to be
 *    logged. The "asked" flag is persisted so a decline is respected rather than
 *    re-prompted every session.
 *
 * @param enabled true once the user has logged a set in this workout.
 */
@Composable
fun RestAlertPermissions(enabled: Boolean) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Nothing to do either way: a refusal only means no background alert.
    }

    var asked by rememberSaveable { mutableStateOf(preferences.getBoolean(KEY_ASKED, false)) }

    LaunchedEffect(enabled) {
        if (!enabled || asked) return@LaunchedEffect
        asked = true
        preferences.edit { putBoolean(KEY_ASKED, true) }

        // Notifications first, and on its own. Stacking the settings jump on top of
        // a permission dialog would put one prompt behind the other.
        if (needsNotificationPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return@LaunchedEffect
        }

        // Exact alarms are a special permission with no runtime dialog, so the only
        // way to ask is the system settings screen. API 31+ only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestExactAlarmsIfNeeded(context)
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

/**
 * Sends the user to the exact-alarm grant screen.
 *
 * Guarded by `runCatching` because the action is not guaranteed to exist on every
 * device, and a missing settings screen must not crash a workout.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun requestExactAlarmsIfNeeded(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    if (alarmManager.canScheduleExactAlarms()) return

    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
