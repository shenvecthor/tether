package com.shane.tether

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the last crash so the app can show it (with a Copy button) on the next launch. */
object CrashLog {
    private const val PREFS = "crashlog"
    private const val KEY = "last"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "?"
                val report = buildString {
                    append("Tether ").append(version)
                    append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
                    append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    append(" · thread ").append(thread.name).append("\n\n")
                    append(Log.getStackTraceString(e))
                }
                // commit(): the process is about to die, so write synchronously
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, report.take(12_000)).commit()
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun last(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
