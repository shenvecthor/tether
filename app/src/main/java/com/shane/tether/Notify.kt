package com.shane.tether

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb

object Notify {
    const val CH_TRACK = "tracking"
    const val CH_ALERT = "alerts_v2"
    const val CH_SOS = "sos_alarm"
    const val CH_MSG = "messages"
    const val CH_LOVE = "love_v2"
    const val ID_FG = 1
    const val ID_CRASH = 2
    private var nextId = 100

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        // v1 channels had default sounds; replaced by channels with Tether's own chimes.
        listOf("alerts", "love_taps").forEach { runCatching { nm.deleteNotificationChannel(it) } }
        val chimeAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun raw(name: String) = android.net.Uri.parse("android.resource://${c.packageName}/raw/$name")
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "Location sharing", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while Tether is sharing your location"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Place & battery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(raw("chime_arrive"), chimeAttrs)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOVE, "Love taps 💕", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hearts, kisses and hugs from your partner"
                enableVibration(true)
                // a little heartbeat
                vibrationPattern = longArrayOf(0, 90, 120, 90, 500, 90, 120, 90)
                setSound(raw("chime_receive"), chimeAttrs)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOS, "SOS & crash alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud alarm when your partner needs help"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    private fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(c: Context, tab: String? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tab != null) putExtra("tab", tab)
        }
        return PendingIntent.getActivity(
            c, tab.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun foreground(c: Context, text: String): Notification =
        NotificationCompat.Builder(c, CH_TRACK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Tether is sharing your location")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(c))
            .build()

    fun show(c: Context, channel: String, title: String, text: String, tab: String = "map") {
        if (!canPost(c)) return
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(accent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(c, tab))
            .build()
        NotificationManagerCompat.from(c).notify(nextId++, n)
    }

    private fun accent(): Int = com.shane.tether.ui.Themes.byId(Prefs.themeId).primary.toArgb()

    fun love(c: Context, text: String) {
        if (!canPost(c)) return
        val n = NotificationCompat.Builder(c, CH_LOVE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("💕 Tether")
            .setContentText(text)
            .setColor(accent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(c, "us"))
            .build()
        NotificationManagerCompat.from(c).notify(nextId++, n)
    }

    fun sos(c: Context, title: String, text: String) {
        if (!canPost(c)) return
        val pi = openApp(c, "map")
        val n = NotificationCompat.Builder(c, CH_SOS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFFC62828.toInt())
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(c).notify(nextId++, n)
    }

    fun crashCountdown(c: Context) {
        if (!canPost(c)) return
        val full = PendingIntent.getActivity(
            c, 7, Intent(c, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ok = PendingIntent.getService(
            c, 8, Intent(c, LocationService::class.java).setAction(LocationService.ACTION_CANCEL_CRASH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CH_SOS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Possible crash detected")
            .setContentText("Alerting your partner in 30 seconds unless you tap I'm OK.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "I'm OK", ok)
            .build()
        NotificationManagerCompat.from(c).notify(ID_CRASH, n)
    }

    fun cancel(c: Context, id: Int) = NotificationManagerCompat.from(c).cancel(id)
}
