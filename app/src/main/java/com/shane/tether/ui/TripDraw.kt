package com.shane.tether.ui

import com.shane.tether.Segment
import com.shane.tether.Stay
import com.shane.tether.Trip
import com.shane.tether.TripAnalyzer

typealias LL = Pair<Double, Double> // lat, lng

data class Seg(val coords: List<LL>, val colorHex: String)
data class StopMark(val lat: Double, val lng: Double, val minutes: Int, val duringDrive: Boolean)

/** Everything the map needs to draw a day (or one trip) of history. */
data class TrackDrawing(
    val segs: List<Seg>,
    val stops: List<StopMark>,
    val start: LL?,
    val end: LL?,
) {
    val allPoints: List<LL> get() = segs.flatMap { it.coords } + stops.map { it.lat to it.lng }
    val isEmpty: Boolean get() = segs.isEmpty() && stops.isEmpty()
}

/**
 * @param snapped road-snapped geometry per trip (keyed by trip.startTs); raw GPS is used when missing.
 * @param walkHex colour for non-driving movement.
 */
fun buildDrawing(segments: List<Segment>, snapped: Map<Long, List<LL>>, walkHex: String): TrackDrawing {
    val segs = ArrayList<Seg>()
    val stops = ArrayList<StopMark>()
    for (s in segments) {
        when (s) {
            is Trip -> {
                // not snapped (yet / live): show a cleaned, smoothed trace instead of raw zig-zags
                val coords = snapped[s.startTs]
                    ?: com.shane.tether.Snapper.smooth(com.shane.tether.Snapper.clean(s.points).map { it.lat to it.lng })
                        .ifEmpty { s.points.map { it.lat to it.lng } }
                if (s.isDrive) segs += colorize(coords, s) else segs += Seg(coords, walkHex)
            }
            is Stay -> stops += StopMark(s.lat, s.lng, s.minutes, s.duringDrive)
        }
    }
    val firstTrip = segments.firstOrNull { it is Trip } as? Trip
    val lastTrip = segments.lastOrNull { it is Trip } as? Trip
    return TrackDrawing(
        segs, stops,
        firstTrip?.let { snapped[it.startTs]?.firstOrNull() ?: (it.points.first().lat to it.points.first().lng) },
        lastTrip?.let { snapped[it.startTs]?.lastOrNull() ?: (it.points.last().lat to it.points.last().lng) },
    )
}

/** Splits a drive into runs of the same speed colour. Snapped points borrow the speed of the nearest raw point. */
private fun colorize(coords: List<LL>, trip: Trip): List<Seg> {
    if (coords.size < 2) return emptyList()
    val raw = trip.points
    var ri = 0
    fun d2(c: LL, i: Int): Double {
        val dLat = c.first - raw[i].lat
        val dLng = c.second - raw[i].lng
        return dLat * dLat + dLng * dLng
    }
    val colors = coords.map { c ->
        // walk forward along the raw points while they get closer (keeps it in travel order)
        var k = ri
        var best = ri
        var bestD = d2(c, ri)
        while (k + 1 < raw.size && k < ri + 8) {
            k++
            val dk = d2(c, k)
            if (dk < bestD) { bestD = dk; best = k }
        }
        ri = best
        TripAnalyzer.speedHex(trip.speedsKmh.getOrElse(best) { 0 })
    }
    val out = ArrayList<Seg>()
    var start = 0
    for (k in 1 until coords.size) {
        if (colors[k] != colors[k - 1]) {
            out += Seg(coords.subList(start, k + 1), colors[start])
            start = k
        }
    }
    if (coords.size - 1 > start) out += Seg(coords.subList(start, coords.size), colors[start])
    return out
}

/** One zone of the heatmap: level 0 = a little time (green), 1 = some (orange), 2 = a lot (red). */
data class HeatCell(val lat: Double, val lng: Double, val level: Int, val count: Int)

const val HEAT_CELL_M = 120.0

/** Bins points into ~120 m zones and grades each zone relative to the busiest one. */
fun buildHeat(points: List<com.shane.tether.HistPoint>): List<HeatCell> {
    if (points.isEmpty()) return emptyList()
    val refLat = points.first().lat
    val dLat = HEAT_CELL_M / 111_320.0
    val dLng = HEAT_CELL_M / (111_320.0 * kotlin.math.cos(Math.toRadians(refLat)).coerceAtLeast(0.01))
    val counts = HashMap<Pair<Long, Long>, Int>()
    for (p in points) {
        if (p.lat.isNaN() || p.lng.isNaN()) continue
        val key = kotlin.math.floor(p.lat / dLat).toLong() to kotlin.math.floor(p.lng / dLng).toLong()
        counts[key] = (counts[key] ?: 0) + 1
    }
    val max = counts.values.maxOrNull() ?: return emptyList()
    return counts.map { (k, c) ->
        val level = when {
            c >= max * 0.5 -> 2
            c >= max * 0.15 || c >= 6 -> 1
            else -> 0
        }
        HeatCell((k.first + 0.5) * dLat, (k.second + 0.5) * dLng, level, c)
    }.sortedBy { it.level } // red drawn last (on top)
}

fun heatHex(level: Int): String = when (level) {
    2 -> "#EF4444"
    1 -> "#F59E0B"
    else -> "#22C55E"
}
