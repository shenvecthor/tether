package com.shane.tether

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file = settings, profile, saved places, couple dates and (optionally) the pairing key.
 * With a password it's encrypted with AES-256-GCM using a PBKDF2-SHA256 derived key.
 */
object Backup {
    private const val ITER = 210_000
    private val rng = SecureRandom()

    class NeedsPassword : Exception("This backup is password protected.")
    class WrongPassword : Exception("Wrong password, or the file is damaged.")

    data class Result(
        val places: Int,
        val restoredPairing: Boolean,
        val pairingError: String?,
        val queuedForLater: Boolean,
    )

    fun build(places: List<Place>, couple: Couple, includePairing: Boolean): JSONObject {
        val o = JSONObject()
            .put("app", "tether").put("v", 1).put("created", System.currentTimeMillis())
            .put("profile", JSONObject().put("name", Prefs.myName).put("phone", Prefs.myPhone).put("mood", Prefs.myMood))
            .put(
                "settings", JSONObject()
                    .put("theme", Prefs.themeId).put("lastPink", Prefs.lastPink).put("lastBlue", Prefs.lastBlue)
                    .put("dark", Prefs.darkMode).put("mapType", Prefs.mapType)
                    .put("highAccuracy", Prefs.highAccuracy).put("crash", Prefs.crashDetection).put("paused", Prefs.paused)
            )
            .put("places", JSONArray().apply {
                places.forEach { p ->
                    put(JSONObject().put("name", p.name).put("lat", p.lat).put("lng", p.lng).put("r", p.radius))
                }
            })
            .put("couple", JSONObject(couple.toJson()))
        if (includePairing && Prefs.isPaired) {
            o.put(
                "pairing", JSONObject()
                    .put("circle", Prefs.circleId).put("slot", Prefs.slot)
                    .put("key", Base64.encodeToString(Prefs.key!!, Base64.NO_WRAP))
                    .put("token", Prefs.rejoinToken)
            )
        }
        return o
    }

    fun write(ctx: Context, uri: Uri, data: JSONObject, password: String?) {
        val out = if (password.isNullOrEmpty()) data.toString(2) else encrypt(data.toString(), password).toString(2)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Couldn't open the file for writing.")
    }

    fun read(ctx: Context, uri: Uri, password: String?): JSONObject {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Couldn't open the file.")
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalStateException("That isn't a Tether backup file.") }
        if (o.optString("app") != "tether") throw IllegalStateException("That isn't a Tether backup file.")
        if (o.has("enc")) {
            if (password.isNullOrEmpty()) throw NeedsPassword()
            return decrypt(o, password)
        }
        return o
    }

    /** Applies a backup. Places/couple go to the cloud now if paired, otherwise they're queued until pairing. */
    suspend fun apply(o: JSONObject): Result {
        o.optJSONObject("profile")?.let {
            Prefs.myName = it.optString("name", Prefs.myName)
            Prefs.myPhone = it.optString("phone", Prefs.myPhone)
            Prefs.myMood = it.optString("mood", Prefs.myMood)
        }
        o.optJSONObject("settings")?.let { s ->
            Prefs.themeId = s.optString("theme", Prefs.themeId)
            Prefs.lastPink = s.optString("lastPink", Prefs.lastPink)
            Prefs.lastBlue = s.optString("lastBlue", Prefs.lastBlue)
            Prefs.darkMode = s.optInt("dark", Prefs.darkMode)
            Prefs.mapType = s.optInt("mapType", Prefs.mapType)
            Prefs.highAccuracy = s.optBoolean("highAccuracy", Prefs.highAccuracy)
            Prefs.crashDetection = s.optBoolean("crash", Prefs.crashDetection)
            Prefs.paused = s.optBoolean("paused", Prefs.paused)
        }

        var restoredPairing = false
        var pairingError: String? = null
        val pairing = o.optJSONObject("pairing")
        if (pairing != null && !Prefs.isPaired) {
            try {
                Cloud.rejoin(
                    pairing.getString("circle"), pairing.getString("slot"),
                    Base64.decode(pairing.getString("key"), Base64.NO_WRAP), pairing.optString("token"),
                )
                restoredPairing = true
            } catch (e: Exception) {
                pairingError = e.message ?: "Couldn't restore pairing"
            }
        }

        val placesCount = o.optJSONArray("places")?.length() ?: 0
        var queued = false
        if (Prefs.isPaired) applyShared(o) else {
            Prefs.pendingRestore = JSONObject().put("places", o.optJSONArray("places") ?: JSONArray())
                .put("couple", o.optJSONObject("couple") ?: JSONObject()).toString()
            queued = true
        }
        if (Prefs.isPaired) Cloud.saveProfile()
        return Result(placesCount, restoredPairing, pairingError, queued)
    }

    /** Pushes backed-up places (skipping duplicates) and couple dates (only fills blanks) to the shared circle. */
    suspend fun applyShared(o: JSONObject) {
        val existing = runCatching { Cloud.fetchPlaces() }.getOrDefault(emptyList())
        o.optJSONArray("places")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("name")
                val lat = p.optDouble("lat")
                val lng = p.optDouble("lng")
                val dupe = existing.any { e ->
                    val d = FloatArray(1)
                    android.location.Location.distanceBetween(e.lat, e.lng, lat, lng, d)
                    e.name == name && d[0] < 60f
                }
                if (!dupe && name.isNotBlank()) Cloud.savePlace(Place("", name, lat, lng, p.optDouble("r", 150.0)))
            }
        }
        o.optJSONObject("couple")?.let { c ->
            val backup = Couple.fromJson(c.toString()) ?: return@let
            val current = runCatching { Cloud.fetchCouple() }.getOrNull() ?: Couple()
            Cloud.saveCouple(
                current.copy(
                    anniversary = if (current.anniversary > 0) current.anniversary else backup.anniversary,
                    nextDate = if (current.nextDate > 0) current.nextDate else backup.nextDate,
                    nextLabel = current.nextLabel.ifBlank { backup.nextLabel },
                )
            )
        }
    }

    // ---------------- crypto ----------------

    private fun derive(password: String, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encrypt(plain: String, password: String): JSONObject {
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt, ITER), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().put("app", "tether").put("v", 1).put("enc", "pbkdf2-sha256-aes256gcm")
            .put("iter", ITER).put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(iv + ct, Base64.NO_WRAP))
    }

    private fun decrypt(o: JSONObject, password: String): JSONObject = try {
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val all = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(password, salt, o.optInt("iter", ITER)), GCMParameterSpec(128, all, 0, 12))
        JSONObject(String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8))
    } catch (e: Exception) {
        throw WrongPassword()
    }
}
