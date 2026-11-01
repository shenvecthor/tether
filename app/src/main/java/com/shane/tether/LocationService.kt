package com.shane.tether

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Always-on foreground service. It:
 *  - shares this phone's location, battery and driving state (encrypted)
 *  - evaluates shared Places and posts arrive / leave events
 *  - answers "refresh" pings from the partner with a fresh high-accuracy fix
 *  - listens for the partner's events and messages and raises local notifications
 *    (so no paid Cloud Functions / push server is needed)
 *  - runs crash detection while driving
 */
class LocationService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.shane.tether.REFRESH"
        const val ACTION_CANCEL_CRASH = "com.shane.tether.CANCEL_CRASH"
        const val ACTION_CRASH_NOW = "com.shane.tether.CRASH_NOW"
        const val ACTION_STOP = "com.shane.tether.STOP"

        @Volatile var lastLocation: Location? = null
        @Volatile var crashCountdownEndsAt: Long = 0L

        fun start(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, LocationService::class.java))
        }

        fun refresh(c: Context) {
            ContextCompat.startForegroundService(
                c, Intent(c, LocationService::class.java).setAction(ACTION_REFRESH)
            )
        }

        fun send(c: Context, action: String) {
            c.startService(Intent(c, LocationService::class.java).setAction(action))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, LocationService::class.java))
        }
    }

    private enum class Mode { NORMAL, DRIVING, LIVE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fused: FusedLocationProviderClient
    private var mode = Mode.NORMAL
    private var liveJob: Job? = null
    private var started = false

    private var driving = false
    private var fastCount = 0
    private var slowCount = 0

    private var lastHist: Location? = null
    private var lastHistTs = 0L
    private var lastPublishedPaused = false
    private var lastStatsKm = 0f
    private var places: List<Place> = emptyList()

    private val dbListeners = mutableListOf<Pair<Query, Any>>()

    private var sensorManager: SensorManager? = null
    private var crashJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        runCatching {
            ContextCompat.registerReceiver(
                this, shutdownReceiver, android.content.IntentFilter(Intent.ACTION_SHUTDOWN),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.isPaired || !Perms.hasFine(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            goForeground()
            started = true
            scope.launch {
                runCatching { Cloud.signIn() }
                runCatching { Cloud.ensureRejoinToken() }
                runCatching { Cloud.watchConnection() }
                runCatching { lastStatsKm = Prefs.statKm; Cloud.publishStats() }
                attachListeners()
                runCatching { Cloud.pruneHistory() }
            }
            requestUpdates()
            requestFreshFix()
        }
        when (intent?.action) {
            ACTION_REFRESH -> {
                updateForegroundText()
                requestUpdates()
                requestFreshFix()
                Cloud.saveProfile()
            }
            ACTION_CANCEL_CRASH -> cancelCrash()
            ACTION_CRASH_NOW -> fireCrash()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(shutdownReceiver) }
        fused.removeLocationUpdates(locationCallback)
        stopCrashSensor()
        dbListeners.forEach { (q, l) ->
            when (l) {
                is ValueEventListener -> q.removeEventListener(l)
                is ChildEventListener -> q.removeEventListener(l)
            }
        }
        dbListeners.clear()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- Foreground notification ----------------

    private fun fgText() = if (Prefs.paused) "Sharing is paused" else "Visible to your partner"

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, Notify.ID_FG, Notify.foreground(this, fgText()), type)
    }

    private fun updateForegroundText() {
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(Notify.ID_FG, Notify.foreground(this, fgText()))
        }
    }

    // ---------------- Location ----------------

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!Perms.hasFine(this)) return
        val req = when (mode) {
            Mode.LIVE -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L).build()
            Mode.DRIVING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
                .setMinUpdateIntervalMillis(5_000L).build()
            Mode.NORMAL -> LocationRequest.Builder(
                if (Prefs.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                60_000L
            ).setMinUpdateIntervalMillis(30_000L).build()
        }
        fused.removeLocationUpdates(locationCallback)
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshFix() {
        if (!Perms.hasFine(this)) return
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) onLocation(loc, force = true) }
    }

    private fun setMode(m: Mode) {
        if (m == mode) return
        mode = m
        requestUpdates()
    }

    /** Partner asked for a fresh position: go high-accuracy for 3 minutes. */
    private fun goLive() {
        requestFreshFix()
        if (mode != Mode.LIVE) setMode(Mode.LIVE)
        liveJob?.cancel()
        liveJob = scope.launch {
            delay(3 * 60_000L)
            setMode(if (driving) Mode.DRIVING else Mode.NORMAL)
        }
    }

    private fun onLocation(loc: Location, force: Boolean = false) {
        lastLocation = loc
        val now = System.currentTimeMillis()
        val speed = if (loc.hasSpeed()) loc.speed else 0f

        // Driving detection: two fast fixes start a drive, four slow ones end it.
        if (speed > 7f) { fastCount++; slowCount = 0 } else if (speed < 2.5f) { slowCount++; fastCount = 0 }
        if (!driving && fastCount >= 2) {
            driving = true
            if (mode != Mode.LIVE) setMode(Mode.DRIVING)
            if (Prefs.crashDetection) startCrashSensor()
        } else if (driving && slowCount >= 4) {
            driving = false
            if (mode != Mode.LIVE) setMode(Mode.NORMAL)
            stopCrashSensor()
        }

        val bm = getSystemService(BatteryManager::class.java) ?: return
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        checkBattery(battery, charging, loc)

        if (!Prefs.isPaired) return

        if (Prefs.paused) {
            if (!lastPublishedPaused || force) {
                Cloud.publishLive(LivePoint(0.0, 0.0, 0f, 0f, battery, charging, now, false, true, null))
                lastPublishedPaused = true
            }
            return
        }
        lastPublishedPaused = false

        val placeName = evaluatePlaces(loc)
        Cloud.publishLive(
            LivePoint(loc.latitude, loc.longitude, loc.accuracy, speed, battery, charging, now, driving, false, placeName)
        )

        val prev = lastHist
        // Denser points while driving so routes look smooth; a heartbeat point every 5 min when still
        // (lets the timeline detect stops of 10+ minutes).
        val minMove = if (driving) 25f else 40f
        if (loc.accuracy < 100 &&
            (prev == null || prev.distanceTo(loc) >= minMove || now - lastHistTs >= 5 * 60_000L)
        ) {
            Cloud.addHistory(HistPoint(loc.latitude, loc.longitude, now, speed, driving, loc.accuracy))
            // lifetime distance (ignores GPS jumps)
            if (prev != null) {
                val d = prev.distanceTo(loc)
                if (d in 20f..5000f) {
                    Prefs.statKm = Prefs.statKm + d / 1000f
                    if (Prefs.statKm - lastStatsKm >= 1f) {
                        lastStatsKm = Prefs.statKm
                        Cloud.publishStats()
                    }
                }
            }
            lastHist = loc
            lastHistTs = now
        }
    }

    private fun checkBattery(battery: Int, charging: Boolean, loc: Location) {
        // Battery about to die: send one last precise location so the partner knows where the phone was.
        if (battery in 1..10 && !charging && !Prefs.dyingSent) {
            Prefs.dyingSent = true
            sendLastLocation("🪫 ${me()}'s battery is almost dead ($battery%). This is their last shared location.")
        } else if ((battery >= 20 || charging) && Prefs.dyingSent) {
            Prefs.dyingSent = false
        }
        if (battery in 1..15 && !charging && !Prefs.lowBatterySent) {
            Prefs.lowBatterySent = true
            Cloud.postEvent("battery", "${me()}'s phone battery is low ($battery%)", loc.latitude, loc.longitude)
        } else if ((battery >= 25 || charging) && Prefs.lowBatterySent) {
            Prefs.lowBatterySent = false
        }
    }

    private fun me() = Prefs.myName.ifBlank { "Your partner" }

    @SuppressLint("MissingPermission")
    private fun sendLastLocation(text: String) {
        val fallback = lastLocation
        if (!Perms.hasFine(this)) {
            Cloud.postEvent("battery_dying", text, fallback?.latitude, fallback?.longitude)
            return
        }
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { l ->
                val use = l ?: fallback
                use?.let { onLocation(it, force = true) }
                Cloud.postEvent("battery_dying", text, use?.latitude, use?.longitude)
            }
            .addOnFailureListener { Cloud.postEvent("battery_dying", text, fallback?.latitude, fallback?.longitude) }
    }

    /** Best effort: phone is shutting down — post the last known location. */
    private val shutdownReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val l = lastLocation
            Cloud.postEvent("battery_dying", "📴 ${me()}'s phone is switching off. This is their last shared location.", l?.latitude, l?.longitude)
        }
    }

    /** Simple geofencing with hysteresis so GPS jitter doesn't cause flapping. */
    private fun evaluatePlaces(loc: Location): String? {
        val inside = Prefs.insidePlaces.toMutableSet()
        var current: String? = null
        val d = FloatArray(1)
        for (p in places) {
            Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, d)
            val dist = d[0]
            val wasInside = p.id in inside
            if (!wasInside && dist <= p.radius && loc.accuracy < 150) {
                inside += p.id
                Cloud.postEvent("arrive", "${me()} arrived at ${p.name}", p.lat, p.lng)
            } else if (wasInside && dist > p.radius + 50 + loc.accuracy / 2) {
                inside -= p.id
                Cloud.postEvent("leave", "${me()} left ${p.name}", loc.latitude, loc.longitude)
            }
            if (p.id in inside) current = p.name
        }
        // forget places that were deleted
        inside.retainAll(places.map { it.id }.toSet())
        Prefs.insidePlaces = inside
        return current
    }

    // ---------------- Firebase listeners ----------------

    private fun attachListeners() {
        if (!Prefs.isPaired) return
        val c = Cloud.circle()

        val placesQ: Query = c.child("places")
        val placesL = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                places = Cloud.parsePlaces(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        placesQ.addValueEventListener(placesL)
        dbListeners += placesQ to placesL

        val pingQ: Query = c.child("ping").child(Cloud.mySlot)
        val pingL = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ts = (snapshot.value as? Number)?.toLong() ?: return
                if (ts > Prefs.lastPing) {
                    Prefs.lastPing = ts
                    // only react to recent pings (not a stale value seen at start-up)
                    if (ts > System.currentTimeMillis() - 5 * 60_000L) goLive()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        pingQ.addValueEventListener(pingL)
        dbListeners += pingQ to pingL

        watchNew("events") { s ->
            val e = Cloud.parseEvent(s) ?: return@watchNew
            if (e.from == Cloud.mySlot) return@watchNew
            if (e.ts < System.currentTimeMillis() - 30 * 60_000L) return@watchNew
            when (e.type) {
                "sos" -> Notify.sos(this, "SOS from your partner", e.text)
                "crash" -> Notify.sos(this, "Possible crash", e.text)
                "checkin" -> Notify.show(this, Notify.CH_ALERT, "Check-in", e.text, "alerts")
                "battery_dying" -> Notify.show(this, Notify.CH_ALERT, "Last location shared", e.text, "alerts")
                // when the app is open, the in-app floating hearts handle it
                "love" -> { if (!MainActivity.resumed) Notify.love(this, e.text) }
                else -> Notify.show(this, Notify.CH_ALERT, "Tether", e.text, "alerts")
            }
        }

        watchNew("messages") { s ->
            val m = Cloud.parseMsg(s) ?: return@watchNew
            if (m.from == Cloud.mySlot) return@watchNew
            if (m.ts < System.currentTimeMillis() - 30 * 60_000L) return@watchNew
            if (MainActivity.chatVisible) return@watchNew
            Notify.show(this, Notify.CH_MSG, "New message", m.text, "chat")
        }
    }

    /** Calls [onNew] once for each child added after the last one we've seen. */
    private fun watchNew(path: String, onNew: (DataSnapshot) -> Unit) {
        val q: Query = Cloud.circle().child(path).orderByKey().limitToLast(20)
        val l = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val k = snapshot.key ?: return
                if (k <= Prefs.lastKey(path)) return
                Prefs.setLastKey(path, k)
                onNew(snapshot)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        q.addChildEventListener(l)
        dbListeners += q to l
    }

    // ---------------- Crash detection ----------------

    private val crashListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (g > 4.0f && crashJob == null && driving) startCrashCountdown()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startCrashSensor() {
        val sm = sensorManager ?: return
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(crashListener, acc, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopCrashSensor() {
        sensorManager?.unregisterListener(crashListener)
    }

    private fun startCrashCountdown() {
        crashCountdownEndsAt = System.currentTimeMillis() + 30_000L
        Notify.crashCountdown(this)
        crashJob = scope.launch {
            delay(30_000L)
            fireCrash()
        }
    }

    private fun cancelCrash() {
        crashJob?.cancel()
        crashJob = null
        crashCountdownEndsAt = 0L
        Notify.cancel(this, Notify.ID_CRASH)
    }

    private fun fireCrash() {
        crashJob?.cancel()
        crashJob = null
        crashCountdownEndsAt = 0L
        Notify.cancel(this, Notify.ID_CRASH)
        val l = lastLocation
        Cloud.postEvent(
            "crash",
            "${me()}'s phone detected a possible car crash and they didn't respond.",
            l?.latitude, l?.longitude
        )
    }
}
