package com.shane.tether.ui

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun ago(ts: Long, now: Long): String {
    val s = (now - ts) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}

fun clock(ts: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

fun kmh(mps: Float): Int = (mps * 3.6f).roundToInt()

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** On-device reverse geocoding (no API key). */
suspend fun reverseGeocode(c: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION")
        val a = Geocoder(c, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
            ?: return@runCatching null
        val street = a.thoroughfare?.let { t -> listOfNotNull(a.subThoroughfare, t).joinToString(" ") }
        listOfNotNull(street, a.locality ?: a.subAdminArea).joinToString(", ")
            .ifEmpty { a.getAddressLine(0) }
    }.getOrNull()
}

fun openDirections(c: Context, lat: Double, lng: Double, label: String = "Partner") {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun dial(c: Context, phone: String) {
    runCatching {
        c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Float {
    val d = FloatArray(1)
    android.location.Location.distanceBetween(aLat, aLng, bLat, bLng, d)
    return d[0]
}

/** "Together 🥰" when close, otherwise "3.2 km apart". Null if either location is unknown. */
fun apartText(me: com.shane.tether.LivePoint?, partner: com.shane.tether.LivePoint?): String? {
    if (me == null || partner == null || me.paused || partner.paused) return null
    val m = distanceMeters(me.lat, me.lng, partner.lat, partner.lng)
    return when {
        m < 150f -> "Together right now 🥰"
        m < 1000f -> "${m.roundToInt()} m apart"
        m < 100_000f -> "%.1f km apart".format(m / 1000f)
        else -> "${(m / 1000f).roundToInt()} km apart"
    }
}

fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

/** Date-picker millis (UTC midnight) -> epoch day. */
fun pickerEpochDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)

fun prettyDate(millis: Long): String =
    java.time.LocalDate.ofEpochDay(pickerEpochDay(millis))
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

/** One-off device location (used in preview mode, where the sharing service isn't running). */
@android.annotation.SuppressLint("MissingPermission")
suspend fun currentDeviceLocation(c: Context): android.location.Location? = runCatching {
    if (!com.shane.tether.Perms.hasFine(c)) return@runCatching null
    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(c)
        .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .await()
}.getOrNull()

/** ("3.2 km", "apart") or ("Together", "right now"); null if either location is unknown. */
fun apartParts(me: com.shane.tether.LivePoint?, partner: com.shane.tether.LivePoint?): Pair<String, String>? {
    if (me == null || partner == null || me.paused || partner.paused) return null
    val m = distanceMeters(me.lat, me.lng, partner.lat, partner.lng)
    return when {
        m < 150f -> "Together" to "right now"
        m < 1000f -> "${m.roundToInt()} m" to "apart"
        m < 100_000f -> "%.1f km".format(m / 1000f) to "apart"
        else -> "${(m / 1000f).roundToInt()} km" to "apart"
    }
}
