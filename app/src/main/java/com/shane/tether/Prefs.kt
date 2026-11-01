package com.shane.tether

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences
    @Volatile private var cachedKey: ByteArray? = null

    fun init(c: Context) {
        sp = c.getSharedPreferences("tether", Context.MODE_PRIVATE)
    }

    private fun s(k: String): String? = sp.getString(k, null)
    private fun s(k: String, v: String?) = sp.edit().putString(k, v).apply()
    private fun gb(k: String, def: Boolean) = sp.getBoolean(k, def)
    private fun pb(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var circleId: String?
        get() = s("circle")
        set(v) = s("circle", v)

    var slot: String?
        get() = s("slot")
        set(v) = s("slot", v)

    var key: ByteArray?
        get() = cachedKey ?: s("key")?.let { Crypto.unwrap(it) }?.also { cachedKey = it }
        set(v) {
            cachedKey = v
            s("key", v?.let { Crypto.wrap(it) })
        }

    var pairComplete: Boolean
        get() = gb("paired", false)
        set(v) = pb("paired", v)

    var onboarded: Boolean
        get() = gb("onboarded", false)
        set(v) = pb("onboarded", v)

    var myName: String
        get() = s("name") ?: ""
        set(v) = s("name", v)

    var myPhone: String
        get() = s("phone") ?: ""
        set(v) = s("phone", v)

    var paused: Boolean
        get() = gb("paused", false)
        set(v) = pb("paused", v)

    var highAccuracy: Boolean
        get() = gb("hiacc", false)
        set(v) = pb("hiacc", v)

    var crashDetection: Boolean
        get() = gb("crash", true)
        set(v) = pb("crash", v)

    var lowBatterySent: Boolean
        get() = gb("lowbatt", false)
        set(v) = pb("lowbatt", v)

    var insidePlaces: Set<String>
        get() = sp.getStringSet("inside", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("inside", v).apply()

    /** Theme id (see Themes). Migrates the old pink on/off switch. */
    var themeId: String
        get() = s("theme") ?: if (gb("pink", true)) "blush" else "ocean"
        set(v) = s("theme", v)

    var lastPink: String
        get() = s("lastPink") ?: "blush"
        set(v) = s("lastPink", v)

    var lastBlue: String
        get() = s("lastBlue") ?: "ocean"
        set(v) = s("lastBlue", v)

    /** 0 = system, 1 = light, 2 = dark */
    var darkMode: Int
        get() = sp.getInt("dark", 0)
        set(v) = sp.edit().putInt("dark", v).apply()

    /** 0 = standard, 1 = satellite, 2 = terrain */
    var mapType: Int
        get() = sp.getInt("maptype", 0)
        set(v) = sp.edit().putInt("maptype", v).apply()

    /** Secret that lets this phone re-claim its pairing slot after a reinstall (kept in backups). */
    var rejoinToken: String
        get() = s("rejoin") ?: ""
        set(v) = s("rejoin", v)

    /** Places / couple dates from a backup restored before pairing; applied once paired. */
    var pendingRestore: String?
        get() = s("pendingRestore")
        set(v) = s("pendingRestore", v)

    var sounds: Boolean
        get() = gb("sounds", true)
        set(v) = pb("sounds", v)

    var seasonal: Boolean
        get() = gb("seasonal", false)
        set(v) = pb("seasonal", v)

    /** My avatar thumbnail (base64 JPEG), kept locally and uploaded encrypted when paired. */
    var myAvatar: String?
        get() = s("avatar")
        set(v) = s("avatar", v)

    /** Day key of the last milestone celebration shown. */
    var lastCelebration: String
        get() = s("lastCeleb") ?: ""
        set(v) = s("lastCeleb", v)

    var dyingSent: Boolean
        get() = gb("dying", false)
        set(v) = pb("dying", v)

    // ---- lifetime stats (for the look-back card) ----
    var statKm: Float
        get() = sp.getFloat("statKm", 0f)
        set(v) = sp.edit().putFloat("statKm", v).apply()

    var statTaps: Int
        get() = sp.getInt("statTaps", 0)
        set(v) = sp.edit().putInt("statTaps", v).apply()

    var statMsgs: Int
        get() = sp.getInt("statMsgs", 0)
        set(v) = sp.edit().putInt("statMsgs", v).apply()

    var statFirst: Long
        get() = sp.getLong("statFirst", 0L)
        set(v) = sp.edit().putLong("statFirst", v).apply()

    /** User chose "Skip for now" and is browsing without a partner. */
    var skippedPairing: Boolean
        get() = gb("skippedPair", false)
        set(v) = pb("skippedPair", v)

    var myMood: String
        get() = s("mood") ?: ""
        set(v) = s("mood", v)

    var myMoodTs: Long
        get() = sp.getLong("moodts", 0L)
        set(v) = sp.edit().putLong("moodts", v).apply()

    var lastPing: Long
        get() = sp.getLong("lastping", 0L)
        set(v) = sp.edit().putLong("lastping", v).apply()

    fun lastKey(name: String): String = s("last_$name") ?: ""
    fun setLastKey(name: String, v: String) = s("last_$name", v)

    val isPaired get() = pairComplete && circleId != null && slot != null && key != null

    fun clearPairing() {
        cachedKey = null
        sp.edit()
            .remove("circle").remove("slot").remove("key").remove("paired")
            .remove("inside").remove("lowbatt").remove("lastping")
            .remove("last_events").remove("last_messages").remove("rejoin")
            .apply()
    }
}
