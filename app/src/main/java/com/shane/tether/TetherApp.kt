package com.shane.tether

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import org.maplibre.android.MapLibre

class TetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Prefs.init(this)
        MapLibre.getInstance(this)
        // Queue writes while offline and replay them when the connection returns.
        runCatching { FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
        runCatching { Notify.createChannels(this) }
        com.shane.tether.ui.ThemeState.load()
        runCatching { Sounds.init(this) }
        runCatching { Avatars.loadLocal() }
    }
}
