@file:OptIn(ExperimentalMaterial3Api::class)

package com.shane.tether.ui

import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shane.tether.Cloud
import com.shane.tether.LocationService
import com.shane.tether.Love
import com.shane.tether.Prefs
import com.shane.tether.Place
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import com.shane.tether.Avatars
import kotlinx.coroutines.launch
import com.shane.tether.Segment
import com.shane.tether.Snapper
import com.shane.tether.Stay
import com.shane.tether.TEvent
import com.shane.tether.Trip
import com.shane.tether.TripAnalyzer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Places

@Composable
fun PlacesTab(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val places by vm.places.collectAsStateWithLifecycle()
    val partner by vm.partner.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val profile by vm.partnerProfile.collectAsStateWithLifecycle()
    val partnerName = profile?.name?.ifBlank { null } ?: "Partner"
    var adding by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var deleting by remember { mutableStateOf<Place?>(null) }
    var editing by remember { mutableStateOf<Place?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(LocalPalette.current.primaryContainer.copy(alpha = 0.5f), LocalPalette.current.background), endY = 1200f))
                .statusBarsPadding(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Places", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "You'll both be notified when either of you arrives at or leaves these places. Tip: long-press the map to add a place anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (places.isEmpty()) {
                item { EmptyNote(Icons.Filled.Place, "No places yet. Add Home, Work or School.") }
            }
            items(places, key = { it.id }) { p ->
                val here = buildList {
                    if (me?.place == p.name && me?.paused == false) add("You")
                    if (partner?.place == p.name && partner?.paused == false) add(partnerName)
                }
                val dist = me?.takeIf { !it.paused }?.let {
                    val d = FloatArray(1)
                    Location.distanceBetween(it.lat, it.lng, p.lat, p.lng, d)
                    d[0]
                }
                Card(
                    onClick = { editing = p },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name.take(2).trim().let { if (it.isNotEmpty() && Character.isSurrogate(it[0])) it else "📍" }, fontSize = 26.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            val sub = buildString {
                                append("${p.radius.roundToInt()} m radius")
                                if (dist != null) append(" · ${formatDistance(dist)} from you")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (here.isNotEmpty()) {
                                Text("${here.joinToString(" & ")} here now 💞", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { deleting = p }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                val l = LocationService.lastLocation
                val lat = l?.latitude ?: me?.lat
                val lng = l?.longitude ?: me?.lng
                if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) adding = lat to lng
                else Toast.makeText(ctx, "Waiting for your location…", Toast.LENGTH_SHORT).show()
            },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Add where I am") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    adding?.let { (lat, lng) -> AddPlaceDialog(lat, lng, onDismiss = { adding = null }) }
    editing?.let { p -> AddPlaceDialog(p.lat, p.lng, onDismiss = { editing = null }, existing = p) }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            confirmButton = { TextButton(onClick = { Cloud.deletePlace(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun mineColor(who: String) = who == Cloud.mySlot

private fun formatDistance(m: Float): String =
    if (m < 1000) "${m.roundToInt()} m" else "%.1f km".format(m / 1000f)

// ---------------------------------------------------------------- History

@Composable
fun HistoryTab(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val profile by vm.partnerProfile.collectAsStateWithLifecycle()
    val partnerLive by vm.partner.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val partnerName = profile?.name?.ifBlank { null } ?: "Partner"
    var who by rememberSaveable { mutableStateOf(Cloud.partnerSlot) }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    val dayTs = remember(dayOffset) { System.currentTimeMillis() - dayOffset * 86_400_000L }
    val day = remember(dayTs) { Cloud.dayKey(dayTs) }
    val points by remember(who, day) { Cloud.historyFlow(who, day) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val segments = remember(points) { TripAnalyzer.analyze(points) }
    var selected by remember(who, day) { mutableStateOf<Int?>(null) }
    var snapped by remember(who, day) { mutableStateOf<Map<Long, List<LL>>>(emptyMap()) }
    val now = rememberNow()
    val mine = who == Cloud.mySlot
    val personHex = if (mine) pal.meHex else pal.partnerHex
    val myPhoto by Avatars.mine.collectAsStateWithLifecycle()
    val partnerPhoto by vm.partnerAvatar.collectAsStateWithLifecycle()

    // ---- heatmap mode ----
    var heatMode by rememberSaveable { mutableStateOf(false) }
    var heatDays by rememberSaveable { mutableIntStateOf(7) }
    var heatWho by rememberSaveable { mutableIntStateOf(2) } // 0 partner, 1 me, 2 both
    var heat by remember { mutableStateOf<List<com.shane.tether.HistPoint>?>(null) }
    val heatCells = remember(heat) { runCatching { buildHeat(heat.orEmpty()) }.getOrDefault(emptyList()) }
    LaunchedEffect(heatMode, heatDays, heatWho) {
        if (!heatMode) return@LaunchedEffect
        heat = null
        val slots = when (heatWho) {
            0 -> listOf(Cloud.partnerSlot)
            1 -> listOf(Cloud.mySlot)
            else -> listOf(Cloud.mySlot, Cloud.partnerSlot)
        }
        val base = System.currentTimeMillis()
        val days = (0 until heatDays).map { Cloud.dayKey(base - it * 86_400_000L) }
        heat = runCatching {
            slots.flatMap { s ->
                days.flatMap { d -> runCatching { Cloud.fetchHistory(s, d) }.getOrDefault(emptyList()) }
            }
        }.getOrDefault(emptyList())
    }

    // A drive still in progress (partner, today, phone says driving, last point recent)
    val liveTripStart = (segments.lastOrNull() as? Trip)?.takeIf {
        !mine && dayOffset == 0 && it.isDrive && partnerLive?.driving == true && now - it.endTs < 4 * 60_000L
    }?.startTs

    // Snap finished drives onto roads (cached), one at a time.
    LaunchedEffect(segments) {
        for (s in segments) {
            if (s !is Trip || !s.isDrive || s.startTs == liveTripStart || snapped.containsKey(s.startTs)) continue
            val res = Snapper.snap(ctx, "$who-$day-${s.startTs}-${s.points.size}", s.points) ?: continue
            snapped = snapped + (s.startTs to res)
        }
    }

    val shownSegments = selected?.let { i -> segments.getOrNull(i)?.let { listOf(it) } } ?: segments
    val drawing = remember(shownSegments, snapped, personHex) { buildDrawing(shownSegments, snapped, personHex) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- header ----
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !heatMode, onClick = { heatMode = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Timeline") }
                    SegmentedButton(
                        selected = heatMode, onClick = { heatMode = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("🔥 Heat") }
                }
            }
            if (heatMode) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to partnerName, 1 to "Me", 2 to "Both").forEach { (k, label) ->
                        FilterChip(selected = heatWho == k, onClick = { heatWho = k }, label = { Text(label) })
                    }
                    Spacer(Modifier.width(4.dp))
                    listOf(7, 14, 30).forEach { d ->
                        FilterChip(selected = heatDays == d, onClick = { heatDays = d }, label = { Text("${d}d") })
                    }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !mine, onClick = { who = Cloud.partnerSlot }, label = { Text(partnerName) })
                FilterChip(selected = mine, onClick = { who = Cloud.mySlot }, label = { Text("Me") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { dayOffset++ }, enabled = dayOffset < 29) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                Text(
                    when (dayOffset) {
                        0 -> "Today"
                        1 -> "Yesterday"
                        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(dayTs))
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { dayOffset-- }, enabled = dayOffset > 0) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
        }

        // ---- map + swipeable timeline sheet ----
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val peekDp = 150f
            val fullDp = (maxHeight.value * 0.62f).coerceAtLeast(peekDp + 80f)
            var expanded by rememberSaveable { mutableStateOf(true) }
            val sheetH = remember { Animatable(fullDp) }
            LaunchedEffect(fullDp) { sheetH.snapTo(if (expanded) fullDp else peekDp) }

            val livePerson = if (!mine && dayOffset == 0) partnerLive?.takeIf { !it.paused } else null
            val dot = livePerson?.let { it.lat to it.lng } ?: points.lastOrNull()?.let { it.lat to it.lng }
            val photo = if (mine) myPhoto else partnerPhoto
            TetherMap(
                Modifier.fillMaxSize(),
                people = dot?.let {
                    listOf(
                        MapPerson(
                            if (mine) "me" else "partner", it.first, it.second, personHex,
                            (if (mine) Prefs.myName else partnerName).take(1).uppercase(),
                            "", photo, photo?.let { b -> System.identityHashCode(b).toString() } ?: "",
                        )
                    )
                } ?: emptyList(),
                places = places,
                drawing = if (heatMode) null else drawing,
                heatCells = if (heatMode) heatCells else null,
                showLink = false,
                linkHex = pal.heartHex,
                placeHex = pal.meHex,
                bottomPaddingDp = (if (expanded) fullDp else peekDp).toInt() + 16,
                dark = pal.dark,
                fitKey = if (heatMode) "heat$heatWho$heatDays${heatCells.size}$expanded" else "$who$day$selected$expanded",
            )
            Column(
                Modifier.align(Alignment.TopStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!heatMode) SpeedLegend()
                if (selected != null) {
                    AssistChip(
                        onClick = { selected = null },
                        label = { Text("Show whole day") },
                        leadingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
            if (heatMode && heat == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            if (!heatMode && points.isEmpty()) {
                Surface(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) { Text("No movement recorded this day.", Modifier.padding(16.dp)) }
            }

            // ---- the sheet ----
            Surface(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetH.value.dp),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Column {
                    // drag handle: swipe down to collapse, up to expand, or tap
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch {
                                        sheetH.snapTo((sheetH.value - delta / density.density).coerceIn(peekDp, fullDp))
                                    }
                                },
                                onDragStopped = { velocity ->
                                    expanded = when {
                                        velocity > 900f -> false
                                        velocity < -900f -> true
                                        else -> sheetH.value > (peekDp + fullDp) / 2
                                    }
                                    sheetH.animateTo(if (expanded) fullDp else peekDp, spring(dampingRatio = 0.8f))
                                },
                            )
                            .clickable {
                                expanded = !expanded
                                scope.launch { sheetH.animateTo(if (expanded) fullDp else peekDp, spring(dampingRatio = 0.8f)) }
                            }
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                        if (!heatMode) {
                            HistorySummary(segments)
                        } else {
                            Text(
                                "🔥 Hotspots · last $heatDays days",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    if (heatMode) {
                        HeatPanel(heat, places, heatDays, Modifier.weight(1f))
                    } else {
                        Timeline(
                            segments = segments,
                            places = places,
                            selected = selected,
                            liveTripStart = liveTripStart,
                            liveKmh = partnerLive?.let { kmh(it.speed) },
                            onSelect = { i -> selected = if (selected == i) null else i },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Where the time went: top saved places by how often you were there. */
@Composable
private fun HeatPanel(points: List<com.shane.tether.HistPoint>?, places: List<Place>, days: Int, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    // computed once per data change, not on every recomposition
    val placeCounts = remember(points, places) {
        val pts = points.orEmpty()
        places.distinctBy { it.id }.map { p ->
            p to pts.count { TripAnalyzer.dist(it.lat, it.lng, p.lat, p.lng) <= p.radius + 40 }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }
    LazyColumn(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "Green = a little time, orange = some, red = a lot. Pinch and zoom to explore.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        val pts = points
        if (pts == null) {
            item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (pts.isEmpty()) {
            item { Text("No location history in this range yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { HeatLegend() }
            val total = pts.size
            val counts = placeCounts
            val inPlaces = counts.sumOf { it.second }
            items(counts.take(6), key = { "hp-" + it.first.id }) { (p, n) ->
                val share = n.toFloat() / total
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${(share * 100).roundToInt()}%", color = pal.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(pal.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(pal.brush))
                    }
                }
            }
            item {
                val other = ((total - inPlaces).toFloat() / total * 100).roundToInt().coerceIn(0, 100)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Everywhere else: $other% · ${"%,d".format(total)} location points",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (places.isEmpty()) {
                    Text("Tip: save places (Home, Work…) to see a breakdown here.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(
        Modifier.padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0 to "A little", 1 to "Some", 2 to "A lot").forEach { (lvl, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(RoundedCornerShape(50))
                        .background(Color(android.graphics.Color.parseColor(heatHex(lvl))))
                )
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SpeedLegend(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("<15" to 5, "15–40" to 20, "40–70" to 50, "70+" to 90).forEach { (label, kmh) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(50))
                        .background(Color(android.graphics.Color.parseColor(TripAnalyzer.speedHex(kmh))))
                )
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Always-visible stats at the top of the sheet. */
@Composable
private fun HistorySummary(segments: List<Segment>) {
    val trips = segments.filterIsInstance<Trip>()
    val drives = trips.filter { it.isDrive }
    val totalKm = trips.sumOf { it.distanceM } / 1000.0
    val driveMin = drives.sumOf { (it.endTs - it.startTs) / 60_000L }
    val top = drives.maxOfOrNull { it.maxKmh } ?: trips.maxOfOrNull { it.maxKmh } ?: 0
    val stopsCount = segments.count { it is Stay && it.duringDrive }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat(Icons.Filled.Timeline, "%.1f km".format(totalKm), "Travelled", Color(0xFF6366F1))
        Stat(Icons.Filled.DirectionsCar, if (driveMin >= 60) "${driveMin / 60}h ${driveMin % 60}m" else "${driveMin}m", "Driving", Color(0xFF22C55E))
        Stat(Icons.Filled.Speed, "$top km/h", "Top speed", Color(0xFFEF4444))
        Stat(Icons.Filled.LocalCafe, "$stopsCount", "Stops", Color(0xFFF59E0B))
    }
}

@Composable
private fun Timeline(
    segments: List<Segment>,
    places: List<Place>,
    selected: Int?,
    liveTripStart: Long?,
    liveKmh: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (segments.isEmpty()) {
            item {
                Text(
                    "Nothing to show yet. Trips and stops appear here as the day goes on.",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // newest at the top
        val order = segments.indices.reversed().toList()
        var lastPart = ""
        order.forEachIndexed { pos, i ->
            val s = segments[i]
            val hour = java.time.Instant.ofEpochMilli(s.startTs).atZone(java.time.ZoneId.systemDefault()).hour
            val part = TripAnalyzer.partOfDay(hour)
            if (part != lastPart) {
                lastPart = part
                item(key = "h$i") {
                    Text(
                        part, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
            }
            item(key = "s$i") {
                TimelineRow(
                    s, places,
                    selected = selected == i,
                    live = s is Trip && s.startTs == liveTripStart,
                    liveKmh = liveKmh,
                    isLast = pos == order.lastIndex,
                    onClick = { onSelect(i) },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    s: Segment,
    places: List<Place>,
    selected: Boolean,
    live: Boolean,
    liveKmh: Int?,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val (dotColor, icon) = when (s) {
        is Trip -> if (s.isDrive) Color(android.graphics.Color.parseColor(TripAnalyzer.speedHex(s.avgKmh))) to Icons.Filled.DirectionsCar
        else pal.partner to Icons.Filled.DirectionsWalk
        is Stay -> if (s.duringDrive) Color(0xFFF59E0B) to Icons.Filled.LocalCafe else Color(0xFF8B5CF6) to Icons.Filled.Place
    }
    var address by remember(s.startTs) { mutableStateOf<String?>(null) }
    val placeName = (s as? Stay)?.let { st ->
        places.firstOrNull { TripAnalyzer.dist(it.lat, it.lng, st.lat, st.lng) <= it.radius + 40 }?.name
    }
    if (s is Stay && placeName == null) {
        LaunchedEffect(s.startTs) { address = reverseGeocode(ctx, s.lat, s.lng) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) pal.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp),
    ) {
        // time column
        Text(
            clock(s.startTs), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp).padding(top = 14.dp),
        )
        // rail with icon
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
            Box(Modifier.width(2.dp).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(dotColor),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            if (!isLast) Box(Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        // content
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (s) {
                    is Trip -> if (s.isDrive) "Drive" else "On the move"
                    is Stay -> placeName?.let { "At $it" } ?: if (s.duringDrive) "Stop" else (address ?: "Stayed put")
                }
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (live) {
                    Spacer(Modifier.width(8.dp))
                    LiveBadge(liveKmh)
                }
            }
            val range = "${clock(s.startTs)} – ${if (live) "now" else clock(s.endTs)}"
            val detail = when (s) {
                is Trip -> "%.1f km · avg %d · max %d km/h".format(s.distanceM / 1000.0, s.avgKmh, s.maxKmh)
                is Stay -> durationText(s.minutes) + if (s.duringDrive && placeName != null) " stop" else ""
            }
            Text("$range · $detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s is Stay && s.duringDrive && address != null && placeName == null) {
                Text(address ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun durationText(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

@Composable
fun LiveBadge(kmh: Int?) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveA")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEF4444)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(50)).background(Color.White))
        Spacer(Modifier.width(5.dp))
        Text(
            "LIVE" + (kmh?.let { " · $it km/h" } ?: ""),
            color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Chat

@Composable
fun ChatTab(vm: HomeViewModel) {
    val pal = LocalPalette.current
    val msgs by vm.messages.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val now = rememberNow()
    val myPhoto by Avatars.mine.collectAsStateWithLifecycle()
    val partnerPhoto by vm.partnerAvatar.collectAsStateWithLifecycle()
    val partnerProfile by vm.partnerProfile.collectAsStateWithLifecycle()
    val partnerName = partnerProfile?.name?.ifBlank { null } ?: "Love"
    val myName = Prefs.myName.ifBlank { "Me" }

    fun send(t: String) {
        if (t.isBlank()) return
        Cloud.sendMessage(t.trim())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.primaryContainer.copy(alpha = 0.45f), pal.background), endY = 1600f))
            .statusBarsPadding()
            .imePadding()
    ) {
        Text(
            "Chat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 12.dp),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rev = msgs.asReversed()
            itemsIndexed(rev, key = { _, m -> m.key }) { idx, m ->
                val mine = m.from == Cloud.mySlot
                // reverseLayout: idx - 1 is the NEXT (newer) message. Show the avatar on the last of a run.
                val showAvatar = idx == 0 || rev[idx - 1].from != m.from
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (!mine) {
                        Box(Modifier.width(30.dp)) {
                            if (showAvatar) Avatar(partnerName, pal.partner, null, 24.dp, photo = partnerPhoto)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(
                        Modifier
                            .widthIn(max = 290.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp, topEnd = 20.dp,
                                    bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp,
                                )
                            )
                            .then(
                                if (mine) Modifier.background(pal.brush)
                                else Modifier.background(MaterialTheme.colorScheme.surface)
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(m.text, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text(
                            ago(m.ts, now), style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.width(30.dp)) {
                            if (showAvatar) Avatar(myName, pal.me, null, 24.dp, photo = myPhoto)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Love you ❤️", "Miss you 🥺", "On my way 🚗", "Home safe 🏠", "Goodnight 🌙", "Good morning ☀️", "Call me 📞", "Running late ⏰").forEach { q ->
                SuggestionChip(onClick = { send(q) }, label = { Text(q) })
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Send, "Send")
            }
        }
    }
}

// ---------------------------------------------------------------- Alerts

@Composable
fun AlertsScreen(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    val now = rememberNow()

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                Text("Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (events.isEmpty()) item { EmptyNote(Icons.Filled.Notifications, "Arrivals, departures, check-ins and SOS alerts will show up here.") }
        items(events, key = { it.key }) { e ->
            val (icon, tint) = eventIcon(e, LocalPalette.current)
            val loveEmoji = if (e.type == "love") Love.byId(e.kind)?.emoji else null
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (e.type == "sos" || e.type == "crash") { if (LocalPalette.current.dark) Color(0xFF4A1F24) else Color(0xFFFDECEA) } else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth().clickable(enabled = e.lat != null) {
                    val la = e.lat; val ln = e.lng
                    if (la != null && ln != null) openDirections(ctx, la, ln, e.type.uppercase())
                },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (loveEmoji != null) Text(loveEmoji, fontSize = 22.sp) else Icon(icon, null, tint = tint)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(e.text)
                        Text(
                            ago(e.ts, now) + if (e.from == Cloud.mySlot) " · you" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun eventIcon(e: TEvent, pal: Palette): Pair<ImageVector, Color> = when (e.type) {
    "love" -> Icons.Filled.Favorite to pal.heart
    "arrive" -> Icons.Filled.Login to pal.me
    "leave" -> Icons.Filled.Logout to pal.partner
    "sos", "crash" -> Icons.Filled.Warning to SosRed
    "battery", "battery_dying" -> Icons.Filled.BatteryAlert to SosRed
    "checkin" -> Icons.Filled.CheckCircle to pal.me
    else -> Icons.Filled.Info to pal.onSurface
}

@Composable
fun EmptyNote(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

