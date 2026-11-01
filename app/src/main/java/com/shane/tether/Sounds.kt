package com.shane.tether

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Soft in-app chimes (generated tones bundled in res/raw). Respects the Sounds setting. */
object Sounds {
    private var pool: SoundPool? = null
    private var send = 0
    private var receive = 0
    private var arrive = 0

    fun init(c: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        send = p.load(c, R.raw.chime_send, 1)
        receive = p.load(c, R.raw.chime_receive, 1)
        arrive = p.load(c, R.raw.chime_arrive, 1)
        pool = p
    }

    private fun play(id: Int, vol: Float = 0.6f) {
        if (!Prefs.sounds || id == 0) return
        pool?.play(id, vol, vol, 1, 0, 1f)
    }

    fun sent() = play(send, 0.5f)
    fun received() = play(receive)
    fun arrived() = play(arrive)
}
