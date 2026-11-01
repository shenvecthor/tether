package com.shane.tether

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Firebase Realtime Database layout (every "d" value is AES-GCM ciphertext):
 *
 * circles/{circleId}/members/{a|b}            = firebase uid (plain, used by security rules)
 * circles/{circleId}/profiles/{slot}          = enc(Profile)
 * circles/{circleId}/live/{slot}              = enc(LivePoint)
 * circles/{circleId}/history/{slot}/{day}/{k} = enc(HistPoint)
 * circles/{circleId}/places/{id}              = enc(Place)
 * circles/{circleId}/events/{k}               = {f: slot, d: enc(event)}
 * circles/{circleId}/messages/{k}             = {f: slot, d: enc(message)}
 * circles/{circleId}/ping/{slot}              = server timestamp ("send me a fresh fix")
 * circles/{circleId}/couple                   = enc(Couple)
 * circles/{circleId}/avatars/{slot}           = enc(base64 JPEG thumbnail)
 * circles/{circleId}/stats/{slot}             = enc(lifetime stats: km, taps, msgs, first)
 * circles/{circleId}/tokens/{slot}            = rejoin token (members-only; lets a restored backup re-claim a slot)
 * circles/{circleId}/proof/{slot}             = token presented when re-claiming
 * circles/{circleId}/daily/{day}/{slot}       = enc(answer to the question of the day)
 */
object Cloud {
    const val QR_PREFIX = "tether1"
    val db: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    val mySlot: String get() = Prefs.slot ?: "a"
    val partnerSlot: String get() = if (mySlot == "a") "b" else "a"

    // ---------------- Sync status (offline queue indicator) ----------------

    private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val _pending = MutableStateFlow(0)
    /** Writes made on this phone that the server hasn't confirmed yet. */
    val pending: StateFlow<Int> = _pending

    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected
    private var watchingConnection = false

    fun watchConnection() {
        if (watchingConnection) return
        watchingConnection = true
        db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _connected.value = (snapshot.value as? Boolean) == true
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /** setValue that's counted in [pending] until the server acknowledges it. */
    private fun DatabaseReference.put(value: Any?) {
        _pending.value = pendingCount.incrementAndGet()
        setValue(value).addOnCompleteListener { _pending.value = pendingCount.decrementAndGet().coerceAtLeast(0) }
    }

    fun circle(): DatabaseReference =
        db.getReference("circles").child(Prefs.circleId ?: error("Not paired"))

    private fun enc(s: String): String = Crypto.encrypt(Prefs.key ?: error("No key"), s)
    private fun dec(s: String?): String? {
        val k = Prefs.key ?: return null
        return s?.let { Crypto.decrypt(k, it) }
    }

    fun dayKey(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    suspend fun signIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid ?: error("Sign-in failed")
    }

    // ---------------- Pairing ----------------

    /** Creates a new private circle and returns the text to show as a QR code. */
    suspend fun createCircle(): String = withTimeout(25_000) {
        val uid = signIn()
        val id = Crypto.randomHex(16)
        val key = Crypto.newKey()
        db.getReference("circles").child(id).child("members").child("a").setValue(uid).await()
        Prefs.circleId = id
        Prefs.key = key
        Prefs.slot = "a"
        Prefs.pairComplete = false
        "$QR_PREFIX|$id|" + Base64.encodeToString(key, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /** Joins the circle encoded in a partner's QR code. */
    suspend fun joinCircle(qr: String) = withTimeout(25_000) {
        val parts = qr.trim().split("|")
        require(parts.size == 3 && parts[0] == QR_PREFIX) { "That isn't a Tether pairing code." }
        val id = parts[1]
        val key = Base64.decode(parts[2], Base64.NO_WRAP or Base64.URL_SAFE)
        require(key.size == 32) { "Pairing code is damaged." }
        val uid = signIn()
        try {
            db.getReference("circles").child(id).child("members").child("b").setValue(uid).await()
        } catch (e: Exception) {
            throw IllegalStateException("This code has already been used. Ask your partner to make a new one.")
        }
        Prefs.circleId = id
        Prefs.key = key
        Prefs.slot = "b"
        Prefs.pairComplete = true
        saveProfile()
    }

    fun partnerJoinedFlow(): Flow<Boolean> =
        valueFlow(circle().child("members").child("b")).map { it.exists() }

    /** Publishes this phone's rejoin token (created once, kept in backups). */
    fun ensureRejoinToken() {
        if (!Prefs.isPaired) return
        if (Prefs.rejoinToken.isEmpty()) Prefs.rejoinToken = Crypto.randomHex(24)
        circle().child("tokens").child(mySlot).setValue(Prefs.rejoinToken)
    }

    /** Re-claims [slot] in [circleId] after a reinstall, using the token from a backup. */
    suspend fun rejoin(circleId: String, slot: String, key: ByteArray, token: String) = withTimeout(25_000) {
        require(slot == "a" || slot == "b") { "Backup pairing data is damaged." }
        require(key.size == 32) { "Backup pairing key is damaged." }
        require(token.isNotEmpty()) { "This backup was made before restore-pairing was supported. Pair again with a QR code." }
        val uid = signIn()
        try {
            db.getReference("circles").child(circleId)
                .updateChildren(mapOf("members/$slot" to uid, "proof/$slot" to token)).await()
        } catch (e: Exception) {
            throw IllegalStateException("The server refused the restore. Make sure the latest database rules are published, then try again.")
        }
        Prefs.circleId = circleId
        Prefs.key = key
        Prefs.slot = slot
        Prefs.rejoinToken = token
        Prefs.pairComplete = true
        Prefs.skippedPairing = false
    }

    suspend fun fetchPlaces(): List<Place> = parsePlaces(circle().child("places").get().await())

    suspend fun fetchCouple(): Couple? =
        dec(circle().child("couple").get().await().getValue(String::class.java))?.let { Couple.fromJson(it) }

    // ---------------- Writes ----------------

    fun saveProfile() {
        if (!Prefs.isPaired) return
        circle().child("profiles").child(mySlot)
            .put(enc(Profile(Prefs.myName, Prefs.myPhone, Prefs.myMood, Prefs.myMoodTs).toJson()))
    }

    fun publishLive(p: LivePoint) {
        if (!Prefs.isPaired) return
        circle().child("live").child(mySlot).put(enc(p.toJson()))
    }

    fun addHistory(p: HistPoint) {
        if (!Prefs.isPaired) return
        circle().child("history").child(mySlot).child(dayKey(p.ts)).push().put(enc(p.toJson()))
    }

    fun postEvent(type: String, text: String, lat: Double? = null, lng: Double? = null, kind: String = "") {
        if (!Prefs.isPaired) return
        val o = JSONObject().put("type", type).put("text", text).put("ts", System.currentTimeMillis())
            .put("kind", kind)
        if (lat != null && lng != null) o.put("lat", lat).put("lng", lng)
        circle().child("events").push().put(mapOf("f" to mySlot, "d" to enc(o.toString())))
    }

    fun sendLove(tap: LoveTap) {
        Sounds.sent()
        val who = Prefs.myName.ifBlank { "Your love" }
        postEvent("love", "$who ${tap.verb} ${tap.emoji}", kind = tap.id)
        if (Prefs.isPaired) {
            Prefs.statTaps = Prefs.statTaps + 1
            publishStats()
        }
    }

    fun setMood(emoji: String) {
        Prefs.myMood = emoji
        Prefs.myMoodTs = System.currentTimeMillis()
        saveProfile()
    }

    fun saveCouple(c: Couple) {
        if (!Prefs.isPaired) return
        circle().child("couple").put(enc(c.toJson()))
    }

    fun saveDailyAnswer(day: String, text: String) {
        if (!Prefs.isPaired) return
        circle().child("daily").child(day).child(mySlot).put(enc(text))
    }

    fun sendMessage(text: String) {
        if (!Prefs.isPaired) return
        val o = JSONObject().put("text", text).put("ts", System.currentTimeMillis())
        Prefs.statMsgs = Prefs.statMsgs + 1
        if (Prefs.statMsgs % 5 == 0) publishStats()
        circle().child("messages").push().put(mapOf("f" to mySlot, "d" to enc(o.toString())))
    }

    fun savePlace(p: Place) {
        if (!Prefs.isPaired) return
        val id = p.id.ifEmpty { circle().child("places").push().key ?: Crypto.randomHex(8) }
        circle().child("places").child(id).put(enc(p.copy(id = id).toJson()))
    }

    fun deletePlace(id: String) {
        if (!Prefs.isPaired) return
        circle().child("places").child(id).removeValue()
    }

    /** Ask the partner's phone to send a fresh, high-accuracy location now. */
    fun ping() {
        if (!Prefs.isPaired) return
        circle().child("ping").child(partnerSlot).setValue(ServerValue.TIMESTAMP)
    }

    /** Keep 30 days of my own history. */
    fun pruneHistory() {
        if (!Prefs.isPaired) return
        val cutoff = dayKey(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
        val ref = circle().child("history").child(mySlot)
        ref.orderByKey().endAt(cutoff).get().addOnSuccessListener { snap ->
            snap.children.forEach { it.ref.removeValue() }
        }
    }

    /** Lifetime counters used by the anniversary look-back. */
    fun publishStats() {
        if (!Prefs.isPaired) return
        if (Prefs.statFirst == 0L) Prefs.statFirst = System.currentTimeMillis()
        val o = JSONObject().put("km", Prefs.statKm.toDouble()).put("taps", Prefs.statTaps)
            .put("msgs", Prefs.statMsgs).put("first", Prefs.statFirst)
        circle().child("stats").child(mySlot).put(enc(o.toString()))
    }

    data class Stats(val km: Double, val taps: Int, val msgs: Int, val first: Long)

    suspend fun fetchStats(slot: String): Stats? {
        if (!Prefs.isPaired) return null
        val j = dec(circle().child("stats").child(slot).get().await().getValue(String::class.java)) ?: return null
        return runCatching {
            val o = JSONObject(j)
            Stats(o.optDouble("km"), o.optInt("taps"), o.optInt("msgs"), o.optLong("first"))
        }.getOrNull()
    }

    /** Uploads my avatar thumbnail (base64 JPEG), or removes it when null. */
    fun saveAvatar(b64: String?) {
        if (!Prefs.isPaired) return
        val ref = circle().child("avatars").child(mySlot)
        if (b64 == null) ref.removeValue() else ref.put(enc(b64))
    }

    fun avatarFlow(slot: String): Flow<String?> = safe {
        valueFlow(circle().child("avatars").child(slot)).map { s -> dec(s.getValue(String::class.java)) }
    }

    suspend fun fetchHistory(slot: String, day: String): List<HistPoint> {
        if (!Prefs.isPaired) return emptyList()
        val s = circle().child("history").child(slot).child(day).get().await()
        return s.children.mapNotNull { c -> dec(c.getValue(String::class.java))?.let { HistPoint.fromJson(it) } }
            .sortedBy { it.ts }
    }

    // ---------------- Reads ----------------

    fun valueFlow(q: Query): Flow<DataSnapshot> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        q.addValueEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }

    private fun <T> safe(block: () -> Flow<T>): Flow<T> =
        if (Prefs.isPaired) block().catch { } else emptyFlow()

    fun liveFlow(slot: String): Flow<LivePoint?> = safe {
        valueFlow(circle().child("live").child(slot))
            .map { s -> dec(s.getValue(String::class.java))?.let { LivePoint.fromJson(it) } }
    }

    fun profileFlow(slot: String): Flow<Profile?> = safe {
        valueFlow(circle().child("profiles").child(slot))
            .map { s -> dec(s.getValue(String::class.java))?.let { Profile.fromJson(it) } }
    }

    fun placesFlow(): Flow<List<Place>> = safe {
        valueFlow(circle().child("places")).map { s -> parsePlaces(s) }
    }

    fun parsePlaces(s: DataSnapshot): List<Place> = s.children.mapNotNull { c ->
        dec(c.getValue(String::class.java))?.let { Place.fromJson(c.key ?: "", it) }
    }.sortedBy { it.name.lowercase() }

    fun messagesFlow(): Flow<List<Msg>> = safe {
        valueFlow(circle().child("messages").orderByKey().limitToLast(300))
            .map { s -> s.children.mapNotNull { parseMsg(it) } }
    }

    fun eventsFlow(): Flow<List<TEvent>> = safe {
        valueFlow(circle().child("events").orderByKey().limitToLast(150))
            .map { s -> s.children.mapNotNull { parseEvent(it) }.reversed() }
    }

    fun coupleFlow(): Flow<Couple> = safe {
        valueFlow(circle().child("couple"))
            .map { s -> dec(s.getValue(String::class.java))?.let { Couple.fromJson(it) } ?: Couple() }
    }

    /** slot -> answer */
    fun dailyFlow(day: String): Flow<Map<String, String>> = safe {
        valueFlow(circle().child("daily").child(day)).map { s ->
            s.children.mapNotNull { c ->
                val k = c.key ?: return@mapNotNull null
                dec(c.getValue(String::class.java))?.let { k to it }
            }.toMap()
        }
    }

    fun historyFlow(slot: String, day: String): Flow<List<HistPoint>> = safe {
        valueFlow(circle().child("history").child(slot).child(day)).map { s ->
            s.children.mapNotNull { c -> dec(c.getValue(String::class.java))?.let { HistPoint.fromJson(it) } }
                .sortedBy { it.ts }
        }
    }

    fun parseMsg(c: DataSnapshot): Msg? {
        val from = c.child("f").getValue(String::class.java) ?: return null
        val j = dec(c.child("d").getValue(String::class.java)) ?: return null
        return runCatching {
            val o = JSONObject(j)
            Msg(c.key ?: "", from, o.getString("text"), o.getLong("ts"))
        }.getOrNull()
    }

    fun parseEvent(c: DataSnapshot): TEvent? {
        val from = c.child("f").getValue(String::class.java) ?: return null
        val j = dec(c.child("d").getValue(String::class.java)) ?: return null
        return runCatching {
            val o = JSONObject(j)
            TEvent(
                c.key ?: "", from, o.getString("type"), o.getString("text"), o.getLong("ts"),
                if (o.has("lat")) o.getDouble("lat") else null,
                if (o.has("lng")) o.getDouble("lng") else null,
                o.optString("kind"),
            )
        }.getOrNull()
    }
}
