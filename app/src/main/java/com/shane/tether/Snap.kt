package com.shane.tether

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Puts finished drives onto real roads using OSRM (OpenStreetMap, free, no key):
 *  1. clean the GPS trace (drop inaccurate fixes, spikes and parked jitter)
 *  2. map-match it in chunks; low-confidence chunks fall back to routing between waypoints
 *  3. post-correct the result (remove little "dart down a side street and back" spurs)
 * Results are cached per trip. If everything fails, a smoothed version of the raw trace is used.
 */
object Snapper {
    private const val UA = "Tether/1.6 (private couples app for Android)"
    private const val OSRM = "https://router.project-osrm.org"
    private const val CHUNK = 80
    private const val CACHE = "snap_cache_v3"

    /** @return snapped [lat, lng] pairs, or null to fall back to the raw path. */
    suspend fun snap(ctx: Context, cacheKey: String, pts: List<HistPoint>): List<Pair<Double, Double>>? =
        withContext(Dispatchers.IO) {
            val sp = ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
            sp.getString(cacheKey, null)?.let { return@withContext decode(it) }
            val clean = clean(pts)
            if (clean.size < 2) return@withContext null

            val out = ArrayList<Pair<Double, Double>>()
            var networkOk = false
            var start = 0
            while (start < clean.size - 1) {
                val end = minOf(start + CHUNK, clean.size)
                val chunk = clean.subList(start, end)
                val part = runCatching { match(chunk) }.getOrNull()
                    ?: runCatching { route(chunk) }.getOrNull()
                if (part != null) networkOk = true
                val use = part ?: smooth(chunk.map { it.lat to it.lng })
                if (out.isNotEmpty() && use.isNotEmpty()) out += use.drop(1) else out += use
                start = end - 1 // overlap one point so chunks join
            }
            if (!networkOk || out.size < 2) return@withContext null
            val fixed = removeSpurs(out)
            if (sp.all.size > 200) sp.edit().clear().apply()
            sp.edit().putString(cacheKey, encode(fixed)).apply()
            fixed
        }

    // ---------------------------------------------------------------- 1) cleaning

    /** Drops inaccurate fixes, impossible jumps, back-and-forth spikes and near-duplicate points. */
    fun clean(pts: List<HistPoint>): List<HistPoint> {
        val sorted = pts.sortedBy { it.ts }.filter { it.acc <= 0f || it.acc <= 60f }
        if (sorted.size < 3) return sorted
        val out = ArrayList<HistPoint>()
        for (p in sorted) {
            val last = out.lastOrNull()
            if (last == null) { out += p; continue }
            val d = dist(last.lat, last.lng, p.lat, p.lng)
            val dt = (p.ts - last.ts) / 1000.0
            if (d < 12f) continue                               // parked / crawling jitter
            if (dt > 0 && d / dt > 65.0) continue               // > ~235 km/h: GPS jump
            out += p
        }
        // remove single-point spikes: A, B, C where B sticks out and C comes back near A
        val res = ArrayList<HistPoint>()
        for (k in out.indices) {
            val b = out[k]
            val c = out.getOrNull(k + 1)
            val a = res.lastOrNull()
            if (a == null || c == null) { res += b; continue }
            val ab = dist(a.lat, a.lng, b.lat, b.lng)
            val bc = dist(b.lat, b.lng, c.lat, c.lng)
            val ac = dist(a.lat, a.lng, c.lat, c.lng)
            val spike = ab > 40f && bc > 40f && ac * 1f < 0.35f * (ab + bc)
            if (!spike) res += b
        }
        return res
    }

    /** Light moving-average smoothing for raw paths (used when snapping isn't available). */
    fun smooth(c: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (c.size < 3) return c
        return c.indices.map { k ->
            if (k == 0 || k == c.lastIndex) c[k] else {
                val a = c[k - 1]; val b = c[k]; val d = c[k + 1]
                ((a.first + 2 * b.first + d.first) / 4) to ((a.second + 2 * b.second + d.second) / 4)
            }
        }
    }

    // ---------------------------------------------------------------- 2) OSRM

    private fun match(chunk: List<HistPoint>): List<Pair<Double, Double>>? {
        val coords = chunk.joinToString(";") { "%.6f,%.6f".format(Locale.US, it.lng, it.lat) }
        val ts = chunk.joinToString(";") { (it.ts / 1000).toString() }
        val rad = chunk.joinToString(";") { (if (it.acc > 0) (it.acc * 1.5f).coerceIn(20f, 60f) else 35f).toInt().toString() }
        val o = get("$OSRM/match/v1/driving/$coords?overview=full&geometries=geojson&timestamps=$ts&radiuses=$rad&gaps=ignore&tidy=true")
            ?: return null
        if (o.optString("code") != "Ok") return null
        val matchings = o.optJSONArray("matchings") ?: return null
        if (matchings.length() == 0) return null
        // reject weak matches — routing between waypoints will do better
        for (m in 0 until matchings.length()) {
            if (matchings.getJSONObject(m).optDouble("confidence", 1.0) < 0.25) return null
        }
        val res = ArrayList<Pair<Double, Double>>()
        for (m in 0 until matchings.length()) res += coordsOf(matchings.getJSONObject(m))
        return res.takeIf { it.size >= 2 }
    }

    /** Fallback: drive along roads between evenly spaced waypoints of the trace. */
    private fun route(chunk: List<HistPoint>): List<Pair<Double, Double>>? {
        val wps = ArrayList<HistPoint>()
        for (p in chunk) {
            val last = wps.lastOrNull()
            if (last == null || dist(last.lat, last.lng, p.lat, p.lng) >= 350f) wps += p
        }
        if (wps.last() != chunk.last()) wps += chunk.last()
        if (wps.size < 2) return null
        // OSRM allows plenty of waypoints, but keep URLs short
        val step = maxOf(1, wps.size / 24)
        val sparse = wps.filterIndexed { i, _ -> i % step == 0 || i == wps.lastIndex }
        val coords = sparse.joinToString(";") { "%.6f,%.6f".format(Locale.US, it.lng, it.lat) }
        val o = get("$OSRM/route/v1/driving/$coords?overview=full&geometries=geojson&continue_straight=true") ?: return null
        if (o.optString("code") != "Ok") return null
        val r = o.optJSONArray("routes")?.optJSONObject(0) ?: return null
        return coordsOf(r).takeIf { it.size >= 2 }
    }

    private fun coordsOf(o: JSONObject): List<Pair<Double, Double>> {
        val g = o.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return emptyList()
        return (0 until g.length()).map { k -> val pt = g.getJSONArray(k); pt.getDouble(1) to pt.getDouble(0) }
    }

    private fun get(url: String): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode !in 200..299) return null
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- 3) post-correction

    /**
     * Removes spurs: the path leaves a point, wanders 25+ m away and comes back within 12 m of
     * where it left (typical of a noisy fix pulling the match into a side street).
     */
    fun removeSpurs(c: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (c.size < 4) return c
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        while (i < c.size) {
            out += c[i]
            var jumpTo = -1
            val limit = minOf(c.size - 1, i + 30)
            var maxAway = 0f
            for (k in i + 2..limit) {
                val away = dist(c[i].first, c[i].second, c[k].first, c[k].second)
                if (away > maxAway) maxAway = away
                if (maxAway >= 25f && away < 12f && maxAway < 400f) jumpTo = k
            }
            i = if (jumpTo > 0) jumpTo else i + 1
        }
        return out
    }

    private fun dist(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Float =
        TripAnalyzer.dist(aLat, aLng, bLat, bLng)

    private fun encode(pts: List<Pair<Double, Double>>): String =
        JSONArray().apply { pts.forEach { put(JSONArray().put(it.first).put(it.second)) } }.toString()

    private fun decode(s: String): List<Pair<Double, Double>>? = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { val p = a.getJSONArray(it); p.getDouble(0) to p.getDouble(1) }
    }.getOrNull()
}
