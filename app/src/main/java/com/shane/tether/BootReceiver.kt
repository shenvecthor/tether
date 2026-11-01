package com.shane.tether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        if (Prefs.isPaired && Perms.hasBackground(context)) {
            runCatching { LocationService.start(context) }
        }
    }
}
