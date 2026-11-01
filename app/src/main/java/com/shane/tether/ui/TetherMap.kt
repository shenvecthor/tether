package com.shane.tether.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shane.tether.Place
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Free OpenStreetMap vector tiles — no API key or billing. */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

const val MAP_STANDARD = 0
const val MAP_SATELLITE = 1
const val MAP_TERRAIN = 2

private fun rasterStyle(vararg layers: Pair<String, String>, attribution: String): String {
    val sources = layers.joinToString(",") { (id, url) ->
        """"$id":{"type":"raster","tiles":["$url"],"tileSize":256,"maxzoom":19,"attribution":"$attribution"}"""
    }
    val lyr = layers.joinToString(",") { (id, _) -> """{"id":"$id","type":"raster","source":"$id"}""" }
    return """{"version":8,"sources":{$sources},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#1b1b1b"}},$lyr]}"""
}

private fun styleFor(type: Int, dark: Boolean): Style.Builder = when (type) {
    MAP_SATELLITE -> Style.Builder().fromJson(
        rasterStyle(
            "sat" to "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "labels" to "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
            attribution = "Imagery © Esri, Maxar, Earthstar Geographics",
        )
    )
    MAP_TERRAIN -> Style.Builder().fromJson(
        rasterStyle(
            "topo" to "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
            attribution = "© Esri, HERE, Garmin, USGS, OpenStreetMap contributors",
        )
    )
    else -> Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)
}

/** Max zoom used when auto-framing, so the view never feels cramped. */
private const val MAX_FIT_ZOOM = 14.0
private const val SINGLE_ZOOM = 13.5

data class MapPerson(
    val id: String,
    val lat: Double,
    val lng: Double,
    val colorHex: String,
    val initial: String,
    val mood: String = "",
    val photo: Bitmap? = null,
    val photoKey: String = "",
)

@Composable
fun TetherMap(
    modifier: Modifier,
    people: List<MapPerson>,
    places: List<Place> = emptyList(),
    drawing: TrackDrawing? = null,
    fitKey: Any? = null,
    followTarget: LL? = null,
    heatCells: List<HeatCell>? = null,
    showLink: Boolean = true,
    linkHex: String = "#FF4D8D",
    placeHex: String = "#D63D74",
    bottomPaddingDp: Int = 260,
    mapType: Int = MAP_STANDARD,
    dark: Boolean = false,
    onLongPress: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val longPress by rememberUpdatedState(onLongPress)

    DisposableEffect(lifecycle) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) {
                    destroyed = true
                    mapView.onDestroy()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                val state = lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
                destroyed = true
            }
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            // Start over Georgetown instead of a whole-world view while locations load.
            m.cameraPosition = CameraPosition.Builder().target(LatLng(6.8013, -58.1551)).zoom(11.0).build()
            m.uiSettings.isRotateGesturesEnabled = false
            // If a remote style ever fails to load, fall back to the default light map.
            var fellBack = false
            mapView.addOnDidFailLoadingMapListener {
                if (fellBack) return@addOnDidFailLoadingMapListener
                fellBack = true
                m.setStyle(Style.Builder().fromUri(STYLE_LIGHT)) { s ->
                    setupLayers(s, density, linkHex, placeHex)
                    style = s
                }
            }
            m.uiSettings.isTiltGesturesEnabled = false
            m.addOnMapLongClickListener { p ->
                val cb = longPress
                cb?.invoke(p.latitude, p.longitude)
                cb != null
            }
        }
    }

    // (Re)load the base map whenever the map type or light/dark mode changes.
    LaunchedEffect(map, mapType, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(styleFor(mapType, dark)) { s ->
            setupLayers(s, density, linkHex, placeHex)
            style = s
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    // Re-colour layers when the theme changes.
    LaunchedEffect(style, linkHex, placeHex) {
        val s = style ?: return@LaunchedEffect
        runCatching {
            s.getLayer("places-fill")?.setProperties(PropertyFactory.fillColor(placeHex))
            s.getLayer("places-line")?.setProperties(PropertyFactory.lineColor(placeHex))
            s.getLayer("link-line")?.setProperties(PropertyFactory.lineColor(linkHex))
        }
    }

    // Pulsing halo under each person.
    LaunchedEffect(style) {
        val s = style ?: return@LaunchedEffect
        while (true) {
            val t = (System.currentTimeMillis() % 1800L) / 1800f
            // runCatching: the style may be swapped out (map type / dark mode) mid-animation
            val ok = runCatching {
                s.getLayer("people-pulse")?.setProperties(
                    PropertyFactory.circleRadius(18f + 26f * t),
                    PropertyFactory.circleOpacity(0.38f * (1f - t)),
                )
                s.getLayer("link-heart")?.setProperties(
                    PropertyFactory.iconSize(0.9f + 0.18f * sin(t * 2f * Math.PI.toFloat()))
                )
            }.isSuccess
            if (!ok) break
            delay(33)
        }
    }

    // Avatars + smooth gliding between position updates.
    var shown by remember { mutableStateOf<List<MapPerson>>(emptyList()) }
    LaunchedEffect(style, people) {
        val s = style ?: return@LaunchedEffect
        runCatching { people.forEach { p -> s.addImage(iconName(p), avatarBitmap(p, density)) } }
        val from = shown.associateBy { it.id }
        val start = System.currentTimeMillis()
        val dur = 900f
        while (true) {
            val raw = ((System.currentTimeMillis() - start) / dur).coerceIn(0f, 1f)
            val e = 1f - (1f - raw) * (1f - raw) * (1f - raw) // ease-out cubic
            val frame = people.map { p ->
                val f = from[p.id]
                if (f == null) p else p.copy(lat = f.lat + (p.lat - f.lat) * e, lng = f.lng + (p.lng - f.lng) * e)
            }
            if (runCatching { renderPeople(s, frame, showLink) }.isFailure) break
            if (raw >= 1f) break
            delay(16)
        }
        shown = people
    }

    LaunchedEffect(style, places) {
        val s = style ?: return@LaunchedEffect
        val features = places.map { Feature.fromGeometry(circlePolygon(it.lat, it.lng, it.radius)) }
        runCatching { s.getSourceAs<GeoJsonSource>("places")?.setGeoJson(FeatureCollection.fromFeatures(features)) }
    }

    LaunchedEffect(style, drawing, people.map { it.lat to it.lng }) {
        val s = style ?: return@LaunchedEffect
        val d = drawing
        runCatching {
            val lines = d?.segs.orEmpty().filter { it.coords.size >= 2 }.map { seg ->
                Feature.fromGeometry(LineString.fromLngLats(seg.coords.map { Point.fromLngLat(it.second, it.first) }))
                    .also { it.addStringProperty("color", seg.colorHex) }
            }
            s.getSourceAs<GeoJsonSource>("track")?.setGeoJson(FeatureCollection.fromFeatures(lines))

            val near = ArrayList<Feature>()
            val far = ArrayList<Feature>()
            d?.stops.orEmpty().forEach { st ->
                val name = "stop-${st.minutes}-${st.duringDrive}"
                s.addImage(name, stopBitmap(st, density))
                val f = Feature.fromGeometry(Point.fromLngLat(st.lng, st.lat)).also { it.addStringProperty("icon", name) }
                val underSomeone = people.any { p -> distanceMeters(p.lat, p.lng, st.lat, st.lng) < 90f }
                if (underSomeone) near += f else far += f
            }
            s.getSourceAs<GeoJsonSource>("stops")?.setGeoJson(FeatureCollection.fromFeatures(far))
            s.getSourceAs<GeoJsonSource>("stops-near")?.setGeoJson(FeatureCollection.fromFeatures(near))

            val ends = listOfNotNull(
                d?.start?.let { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)).also { f -> f.addStringProperty("color", "#22C55E") } },
                d?.end?.let { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)).also { f -> f.addStringProperty("color", "#EF4444") } },
            )
            s.getSourceAs<GeoJsonSource>("ends")?.setGeoJson(FeatureCollection.fromFeatures(ends))
        }
    }

    LaunchedEffect(style, heatCells) {
        val s = style ?: return@LaunchedEffect
        val zones = heatCells.orEmpty().map { c ->
            Feature.fromGeometry(circlePolygon(c.lat, c.lng, HEAT_CELL_M * 0.62, 28))
                .also { it.addStringProperty("color", heatHex(c.level)) }
        }
        runCatching { s.getSourceAs<GeoJsonSource>("heat")?.setGeoJson(FeatureCollection.fromFeatures(zones)) }
    }

    // Follow a moving person (live driving).
    LaunchedEffect(map, followTarget) {
        val m = map ?: return@LaunchedEffect
        val t = followTarget ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(t.first, t.second), 15.5), 900)
    }

    // Frame everyone comfortably (never closer than MAX_FIT_ZOOM).
    val trackPts = drawing?.allPoints.orEmpty() + heatCells.orEmpty().map { it.lat to it.lng }
    val hasData = trackPts.isNotEmpty() || people.isNotEmpty()
    LaunchedEffect(map, fitKey, hasData, trackPts.firstOrNull()) {
        if (followTarget != null) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val pts = trackPts.map { LatLng(it.first, it.second) } + people.map { LatLng(it.lat, it.lng) }
        if (pts.isEmpty()) return@LaunchedEffect
        val single = pts.size < 2 || pts.all { it == pts[0] }
        val position: CameraPosition? = if (single) null else runCatching {
            val bounds = LatLngBounds.Builder().includes(pts).build()
            val pad = intArrayOf(
                (64 * density).toInt(), (120 * density).toInt(),
                (64 * density).toInt(), (bottomPaddingDp * density).toInt(),
            )
            m.getCameraForLatLngBounds(bounds, pad)
        }.getOrNull()
        val target = if (position != null) {
            CameraPosition.Builder(position).zoom(min(position.zoom, MAX_FIT_ZOOM)).build()
        } else {
            CameraPosition.Builder().target(pts.first()).zoom(SINGLE_ZOOM).build()
        }
        m.animateCamera(CameraUpdateFactory.newCameraPosition(target), 1200)
    }
}


private fun setupLayers(s: Style, density: Float, linkHex: String, placeHex: String) {
    s.addImage("heart", emojiBitmap("💗", (30 * density).toInt()))
    listOf("heat", "places", "track", "ends", "stops", "link", "link-mid", "people", "stops-near").forEach { s.addSource(GeoJsonSource(it)) }
    // Heat zones: plain translucent circles (no native heatmap layer — more stable on all phones).
    s.addLayer(
        FillLayer("heat-fill", "heat").withProperties(
            PropertyFactory.fillColor(Expression.get("color")),
            PropertyFactory.fillOpacity(0.42f),
        )
    )
    s.addLayer(
        LineLayer("heat-line", "heat").withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineOpacity(0.7f),
            PropertyFactory.lineWidth(1f),
        )
    )
    s.addLayer(
        FillLayer("places-fill", "places").withProperties(
            PropertyFactory.fillColor(placeHex),
            PropertyFactory.fillOpacity(0.14f),
        )
    )
    s.addLayer(
        LineLayer("places-line", "places").withProperties(
            PropertyFactory.lineColor(placeHex),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        )
    )
    s.addLayer(
        LineLayer("track-casing", "track").withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        LineLayer("track-line", "track").withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        CircleLayer("ends-dot", "ends").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    s.addLayer(
        SymbolLayer("stops-icon", "stops").withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        )
    )
    s.addLayer(
        LineLayer("link-line", "link").withProperties(
            PropertyFactory.lineColor(linkHex),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(0.8f),
            PropertyFactory.lineDasharray(arrayOf(1f, 2f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        )
    )
    s.addLayer(
        SymbolLayer("link-heart", "link-mid").withProperties(
            PropertyFactory.iconImage("heart"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    )
    s.addLayer(
        CircleLayer("people-pulse", "people").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(20f),
            PropertyFactory.circleOpacity(0.3f),
        )
    )
    s.addLayer(
        SymbolLayer("people-avatar", "people").withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
        )
    )
    // Stops right where someone is standing: lift the label above their pin so it isn't hidden.
    s.addLayer(
        SymbolLayer("stops-near-icon", "stops-near").withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOffset(arrayOf(0f, -30f)),
        )
    )
}

private fun iconName(p: MapPerson) = "av-${p.id}-${p.colorHex}-${p.initial}-${p.mood}-${p.photoKey}"

private fun renderPeople(s: Style, people: List<MapPerson>, showLink: Boolean) {
    val features = people.map { p ->
        Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).also {
            it.addStringProperty("color", p.colorHex)
            it.addStringProperty("icon", iconName(p))
        }
    }
    s.getSourceAs<GeoJsonSource>("people")?.setGeoJson(FeatureCollection.fromFeatures(features))

    val link = if (showLink && people.size == 2) people else emptyList()
    val line = if (link.size == 2) {
        listOf(Feature.fromGeometry(LineString.fromLngLats(link.map { Point.fromLngLat(it.lng, it.lat) })))
    } else emptyList()
    s.getSourceAs<GeoJsonSource>("link")?.setGeoJson(FeatureCollection.fromFeatures(line))
    val mid = if (link.size == 2) {
        listOf(
            Feature.fromGeometry(
                Point.fromLngLat((link[0].lng + link[1].lng) / 2, (link[0].lat + link[1].lat) / 2)
            )
        )
    } else emptyList()
    s.getSourceAs<GeoJsonSource>("link-mid")?.setGeoJson(FeatureCollection.fromFeatures(mid))
}

/** Draws a round avatar pin: white ring, coloured disc, initial, mood badge. */
private fun avatarBitmap(p: MapPerson, density: Float): Bitmap {
    val size = (54 * density).toInt()
    val pad = (6 * density)
    val bmp = Bitmap.createBitmap(size + pad.toInt(), size + pad.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2 * density

    paint.color = 0x33000000
    c.drawCircle(cx, cy + 2 * density, r, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, r, paint)
    paint.color = android.graphics.Color.parseColor(p.colorHex)
    c.drawCircle(cx, cy, r - 2.5f * density, paint)

    val photo = p.photo
    if (photo != null) {
        // circular photo inside a thin coloured ring
        val inner = r - 5 * density
        val shader = android.graphics.BitmapShader(photo, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val m = android.graphics.Matrix()
        val scale = (inner * 2) / minOf(photo.width, photo.height)
        m.setScale(scale, scale)
        m.postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
        shader.setLocalMatrix(m)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        c.drawCircle(cx, cy, inner, pp)
    } else {
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 0.95f
        c.drawText(p.initial.ifEmpty { "♥" }, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
    }
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER

    if (p.mood.isNotEmpty()) {
        val br = size * 0.22f
        val bx = size - br + pad / 2
        val by = size - br + pad / 2
        paint.color = android.graphics.Color.WHITE
        c.drawCircle(bx, by, br, paint)
        paint.textSize = br * 1.25f
        c.drawText(p.mood, bx, by - (paint.descent() + paint.ascent()) / 2, paint)
    }
    return bmp
}

/** Little pill marker for a stop: "☕ 25m" (amber, during a drive) or "📍 2h 5m" (purple). */
private fun stopBitmap(st: StopMark, density: Float): Bitmap {
    val label = (if (st.duringDrive) "☕ " else "📍 ") + when {
        st.minutes >= 60 -> "${st.minutes / 60}h ${st.minutes % 60}m"
        else -> "${st.minutes}m"
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }
    val padH = 10 * density
    val h = (28 * density).toInt()
    val tail = 7 * density
    val w = (paint.measureText(label) + padH * 2).toInt()
    val bmp = Bitmap.createBitmap(w, (h + tail).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(if (st.duringDrive) "#F59E0B" else "#8B5CF6")
    }
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, bg)
    val path = android.graphics.Path().apply {
        moveTo(w / 2f - tail, h.toFloat() - 1)
        lineTo(w / 2f + tail, h.toFloat() - 1)
        lineTo(w / 2f, h + tail)
        close()
    }
    c.drawPath(path, bg)
    c.drawText(label, padH, h / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    return bmp
}

private fun emojiBitmap(emoji: String, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx * 0.8f
        textAlign = Paint.Align.CENTER
    }
    c.drawText(emoji, sizePx / 2f, sizePx / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    return bmp
}

private fun circlePolygon(lat: Double, lng: Double, radiusM: Double, n: Int = 64): Polygon {
    val dLatPerM = 1.0 / 111_320.0
    val dLngPerM = 1.0 / (111_320.0 * cos(Math.toRadians(lat)))
    val ring = (0..n).map { i ->
        val a = 2 * Math.PI * i / n
        Point.fromLngLat(lng + radiusM * dLngPerM * sin(a), lat + radiusM * dLatPerM * cos(a))
    }
    return Polygon.fromLngLats(listOf(ring))
}
