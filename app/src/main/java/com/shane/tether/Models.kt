package com.shane.tether

import org.json.JSONObject

data class LivePoint(
    val lat: Double,
    val lng: Double,
    val acc: Float,
    val speed: Float,          // m/s
    val battery: Int,
    val charging: Boolean,
    val ts: Long,
    val driving: Boolean,
    val paused: Boolean,
    val place: String?,
) {
    fun toJson(): String = JSONObject()
        .put("lat", lat).put("lng", lng).put("acc", acc.toDouble()).put("spd", speed.toDouble())
        .put("bat", battery).put("chg", charging).put("ts", ts).put("drv", driving)
        .put("pau", paused).put("plc", place ?: "")
        .toString()

    companion object {
        fun fromJson(s: String): LivePoint? = runCatching {
            val o = JSONObject(s)
            LivePoint(
                o.getDouble("lat"), o.getDouble("lng"), o.optDouble("acc", 0.0).toFloat(),
                o.optDouble("spd", 0.0).toFloat(), o.optInt("bat", -1), o.optBoolean("chg"),
                o.getLong("ts"), o.optBoolean("drv"), o.optBoolean("pau"),
                o.optString("plc").ifEmpty { null },
            )
        }.getOrNull()
    }
}

data class HistPoint(
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val speed: Float,          // m/s
    val drv: Boolean = false,  // recorded while driving
    val acc: Float = 0f,
) {
    fun toJson(): String = JSONObject().put("lat", lat).put("lng", lng).put("ts", ts)
        .put("spd", speed.toDouble()).put("drv", drv).put("acc", acc.toDouble()).toString()

    companion object {
        fun fromJson(s: String): HistPoint? = runCatching {
            val o = JSONObject(s)
            HistPoint(
                o.getDouble("lat"), o.getDouble("lng"), o.getLong("ts"),
                o.optDouble("spd", 0.0).toFloat(), o.optBoolean("drv"), o.optDouble("acc", 0.0).toFloat(),
            )
        }.getOrNull()
    }
}

data class Place(val id: String, val name: String, val lat: Double, val lng: Double, val radius: Double) {
    fun toJson(): String = JSONObject().put("name", name).put("lat", lat).put("lng", lng)
        .put("r", radius).toString()

    companion object {
        fun fromJson(id: String, s: String): Place? = runCatching {
            val o = JSONObject(s)
            Place(id, o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.getDouble("r"))
        }.getOrNull()
    }
}

data class Profile(val name: String, val phone: String, val mood: String = "", val moodTs: Long = 0L) {
    fun toJson(): String = JSONObject().put("name", name).put("phone", phone)
        .put("mood", mood).put("moodTs", moodTs).toString()

    companion object {
        fun fromJson(s: String): Profile? = runCatching {
            val o = JSONObject(s)
            Profile(o.optString("name"), o.optString("phone"), o.optString("mood"), o.optLong("moodTs"))
        }.getOrNull()
    }
}

/** Shared relationship details. Dates are UTC-midnight millis from the date picker (0 = not set). */
data class Couple(val anniversary: Long = 0L, val nextDate: Long = 0L, val nextLabel: String = "") {
    fun toJson(): String = JSONObject().put("ann", anniversary).put("next", nextDate)
        .put("nextLabel", nextLabel).toString()

    companion object {
        fun fromJson(s: String): Couple? = runCatching {
            val o = JSONObject(s)
            Couple(o.optLong("ann"), o.optLong("next"), o.optString("nextLabel"))
        }.getOrNull()
    }
}

data class Msg(val key: String, val from: String, val text: String, val ts: Long)

/** type: arrive, leave, sos, crash, battery, checkin, love (kind = LoveTap id) */
data class TEvent(
    val key: String,
    val from: String,
    val type: String,
    val text: String,
    val ts: Long,
    val lat: Double?,
    val lng: Double?,
    val kind: String = "",
)
