package com.example.androidapp.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the rest-over alarm and posts the notification.
 *
 * Its own file because Android instantiates it by name from the manifest: a receiver
 * buried in the scheduler that arms it is hard to find from the declaration that refers
 * to it.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        RestNotifications.notifyRestOver(context)
    }
}
