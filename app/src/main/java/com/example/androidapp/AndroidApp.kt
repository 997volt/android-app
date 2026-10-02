package com.example.androidapp

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.platform.CrashLogStore
import com.example.androidapp.platform.CrashMetadata
import com.example.androidapp.platform.CrashRecorder
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.time.Instant

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * creates the application-level dependency container that `@AndroidEntryPoint`
 * activities and `@HiltViewModel` ViewModels are injected from (ROADMAP F4).
 */
@HiltAndroidApp
class AndroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        forgetTheRestAlertChannel()
    }

    /**
     * Removes the notification channel the deleted rest alert used to create (ROADMAP B37).
     *
     * Android keeps a channel across updates until uninstall, so a device that ran a build before N26
     * still lists "Rest timer" in its notification settings — for an app that now declares no
     * permissions and posts nothing. The code that created it is gone; the channel is not, because
     * deleting data on someone's device is not something a removed feature gets to leave behind.
     *
     * **Costs nothing and asks for nothing**: deleting a channel needs no permission, and the call is a
     * no-op on a device that never had one, which is every install since. It is done on every launch
     * rather than once behind a flag, because a flag to save a no-op is more code than the no-op.
     */
    private fun forgetTheRestAlertChannel() {
        getSystemService(NotificationManager::class.java)
            ?.deleteNotificationChannel(REST_ALERT_CHANNEL_ID)
    }



    /**
     * Records uncaught exceptions to a file in app-private storage (ROADMAP F11).
     *
     * Local only: the app has no `INTERNET` permission, so nothing is transmitted and
     * a crash log leaves the device only if the user exports one. Also written to
     * logcat, which is what `adb logcat -b crash` reads after the fact.
     *
     * Version comes from the package manager rather than `BuildConfig`, so it is the
     * same value Android compares when deciding whether an update is installable.
     */
    private fun installCrashRecorder() {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()

        CrashRecorder(
            store = CrashLogStore(File(filesDir, CRASH_DIR)),
            timeSource = TimeSource { Instant.now() },
            metadata = {
                @Suppress("DEPRECATION") // versionCode: fine on minSdk 26, longVersionCode is 28+
                val code = info?.versionCode ?: 0
                CrashMetadata(
                    appVersion = info?.versionName ?: "unknown",
                    versionCode = code,
                    androidVersion = Build.VERSION.RELEASE ?: "unknown",
                    deviceModel = Build.MODEL ?: "unknown",
                )
            },
        ).install()

        Log.i(TAG, "crash recorder installed")
    }

    private companion object {
        const val TAG = "AndroidApp"
        const val CRASH_DIR = "crash-logs"

        /** The id the deleted rest-alert builder used; kept only so it can be forgotten (B37). */
        const val REST_ALERT_CHANNEL_ID = "rest_timer"
    }
}
