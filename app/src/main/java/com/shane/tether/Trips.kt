package com.shane.tether

import android.location.Location
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface Segment {
    val startTs: Long
    val endTs: Long
}

/** Somewhere they stayed 10+ minutes. [duringDrive] = a stop between two drives (shown in its own colour). */
data class Stay(
    override val startTs: Long,
    override val endTs: Long,
    val lat: Double,
    val lng: Double,
    val duringDrive: Boolean = false,
) : Segment {
    val minutes: Int get() = ((endTs - startTs) / 60_000L).toInt()
}

data class Trip(
    val points: List<HistPoint>,
    override val startTs: Long,
    override val endTs: Long,
    val distanceM: Double,
    val maxKmh: Int,
    val avgKmh: Int,
    val isDrive: Boolean,
    /** Speed (km/h) at each point, smoothed. */
    val speedsKmh: List<Int>,
) : Segment

/**
 * Turns a day of raw location points into a timeline of stays and trips.
 *  - a stay = points within ~120 m of each other for 10+ minutes
 *  - a trip = everything between stays; it's a drive if it went fast enough
 */
object TripAnalyzer {
    const val STOP_MIN_MS = 10 * 60_000L
    private const val STAY_RADIUS_M = 120f
    private const val MIN_TRIP_M = 250.0

    fun analyze(raw: List<HistPoint>): List<Segment> {
        if (raw.size < 2) return emptyList()
        val p = raw.sortedBy { it.ts }
        val n = p.size

        // 1) find stays
        val stays = ArrayList<IntRange>()
        var i = 0
        while (i < n) {
            var j = i
            while (j + 1 < n && dist(p[i], p[j + 1]) < STAY_RADIUS_M) j++
            if (p[j].ts - p[i].ts >= STOP_MIN_MS) {
                stays += i..j
                i = j + 1
            } else i++
        }

        // 2) build ordered segments (trips connect the end of one stay to the start of the next)
        val segs = ArrayList<Segment>()
        var cursor = 0
        for (r in stays) {
            val from = if (cursor == 0) 0 else cursor - 1
            if (r.first > from) makeTrip(p.subList(from, r.first + 1))?.let { segs += it }
            val sub = p.subList(r.first, r.last + 1)
            segs += Stay(p[r.first].ts, p[r.last].ts, sub.map { it.lat }.average(), sub.map { it.lng }.average())
            cursor = r.last + 1
        }
        if (cursor < n) {
            val from = if (cursor == 0) 0 else cursor - 1
            if (n - 1 > from) makeTrip(p.subList(from, n))?.let { segs += it }
        }

        // 3) merge back-to-back stays at the same spot (tiny wanders in between were dropped)
        val merged = ArrayList<Segment>()
        for (s in segs) {
            val last = merged.lastOrNull()
            if (s is Stay && last is Stay && dist(last.lat, last.lng, s.lat, s.lng) < 200f) {
                merged[merged.size - 1] = last.copy(endTs = s.endTs)
            } else merged += s
        }

        // 4) flag stops made between two drives
        return merged.mapIndexed { k, s ->
            if (s is Stay) {
                val before = merged.getOrNull(k - 1)
                val after = merged.getOrNull(k + 1)
                if (before is Trip && before.isDrive && after is Trip && after.isDrive) s.copy(duringDrive = true) else s
            } else s
        }
    }

    private fun makeTrip(tp: List<HistPoint>): Trip? {
        if (tp.size < 2) return null
        var d = 0.0
        val speeds = IntArray(tp.size)
        var maxS = 0f
        for (k in 1 until tp.size) {
            val dd = dist(tp[k - 1], tp[k])
            d += dd
            val dt = (tp[k].ts - tp[k - 1].ts) / 1000f
            val derived = if (dt > 0) dd / dt else 0f
            var s = max(tp[k].speed, if (dt in 1f..600f) derived else 0f)
            if (s > 70f) s = tp[k].speed.coerceAtMost(70f) // ignore GPS jumps (>250 km/h)
            speeds[k] = (s * 3.6f).roundToInt()
            if (s > maxS) maxS = s
        }
        speeds[0] = speeds.getOrElse(1) { 0 }
        if (d < MIN_TRIP_M) return null
        val dur = (tp.last().ts - tp.first().ts) / 1000.0
        val avg = if (dur > 0) d / dur else 0.0
        val drive = maxS >= 8f || avg >= 5.0 || tp.count { it.drv } >= 2
        // light smoothing so colours don't flicker
        val smooth = speeds.indices.map { k ->
            val a = speeds[max(0, k - 1)]
            val b = speeds[k]
            val c = speeds[minOf(speeds.size - 1, k + 1)]
            (a + 2 * b + c) / 4
        }
        return Trip(tp.toList(), tp.first().ts, tp.last().ts, d, (maxS * 3.6f).roundToInt(), (avg * 3.6).roundToInt(), drive, smooth)
    }

    private fun dist(a: HistPoint, b: HistPoint): Float = dist(a.lat, a.lng, b.lat, b.lng)

    fun dist(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLng, bLat, bLng, r)
        return r[0]
    }

    /** Morning / afternoon / … for the timeline headers. */
    fun partOfDay(hour: Int): String = when (hour) {
        in 0..4 -> "🌌 Late night"
        in 5..11 -> "🌅 Morning"
        in 12..16 -> "☀️ Afternoon"
        in 17..20 -> "🌆 Evening"
        else -> "🌙 Night"
    }

    /** Colour for a speed in km/h (slow → fast). */
    fun speedHex(kmh: Int): String = when {
        kmh < 15 -> "#8B7CF6"  // crawling / traffic
        kmh < 40 -> "#22C55E"  // town
        kmh < 70 -> "#F59E0B"  // main road
        else -> "#EF4444"      // highway
    }
}
