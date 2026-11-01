@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.shane.tether.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.shane.tether.Avatars
import com.shane.tether.Backup
import com.shane.tether.Sounds
import com.shane.tether.Cloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shane.tether.LivePoint
import com.shane.tether.LocationService
import com.shane.tether.Love
import com.shane.tether.MainActivity
import com.shane.tether.Perms
import com.shane.tether.Place
import com.shane.tether.Prefs
import com.shane.tether.Profile
import com.shane.tether.Trip
import com.shane.tether.TripAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

private enum class HomeTab(val id: String, val label: String, val icon: ImageVector) {
    MAP("map", "Map", Icons.Filled.Map),
    US("us", "Us", Icons.Filled.Favorite),
    CHAT("chat", "Chat", Icons.Filled.ChatBubble),
    PLACES("places", "Places", Icons.Filled.Place),
    HISTORY("history", "History", Icons.Filled.History),
}

@Composable
fun HomeScreen(tabRequest: MutableState<String?>, onPairingChanged: () -> Unit) {
    val ctx = LocalContext.current
    val vm: HomeViewModel = viewModel(key = Prefs.circleId ?: "none")
    var tab by rememberSaveable { mutableStateOf("map") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAlerts by rememberSaveable { mutableStateOf(false) }
    val events by vm.events.collectAsStateWithLifecycle()
    val couple by vm.couple.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val partnerProfileTop by vm.partnerProfile.collectAsStateWithLifecycle()
    var celebration by remember { mutableStateOf<Milestone?>(null) }

    // Milestone day? Open with confetti (once per day).
    LaunchedEffect(couple.anniversary, today) {
        val m = Milestones.today(couple.anniversary) ?: return@LaunchedEffect
        if (Prefs.lastCelebration != today) {
            Prefs.lastCelebration = today
            celebration = m
            Sounds.received()
        }
    }
    LaunchedEffect(Unit) {
        Cloud.watchConnection()
        if (!vm.preview) Avatars.syncToCloud()
    }

    LaunchedEffect(tabRequest.value) {
        tabRequest.value?.let {
            showSettings = false
            if (it == "alerts") showAlerts = true else {
                showAlerts = false
                tab = it
            }
            tabRequest.value = null
        }
    }
    LaunchedEffect(Unit) {
        if (!vm.preview) {
            Prefs.pendingRestore?.let { pending ->
                runCatching { Backup.applyShared(org.json.JSONObject(pending)) }
                Prefs.pendingRestore = null
            }
        }
    }
    LaunchedEffect(Unit) {
        if (vm.preview) {
            currentDeviceLocation(ctx)?.let { vm.setPreviewLocation(it.latitude, it.longitude, it.accuracy) }
        } else if (Perms.hasFine(ctx)) {
            runCatching { LocationService.start(ctx) }
        }
    }
    DisposableEffect(tab, showAlerts, showSettings) {
        MainActivity.chatTabOpen = tab == "chat" && !showAlerts && !showSettings
        onDispose { MainActivity.chatTabOpen = false }
    }

    // Incoming love taps → floating hearts + banner while the app is open.
    var seenKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(events) {
        val newest = events.firstOrNull() ?: return@LaunchedEffect
        val prev = seenKey
        seenKey = newest.key
        val recent = System.currentTimeMillis() - 2 * 60_000L
        events.takeWhile { prev == null || it.key > prev }
            .filter { it.from != Cloud.mySlot && it.ts > recent }
            .take(3)
            .forEach {
                when (it.type) {
                    "love" -> {
                        LoveBus.burst(Love.byId(it.kind)?.emoji ?: "❤️")
                        LoveBus.banner = it.text
                        Sounds.received()
                    }
                    "arrive" -> {
                        LoveBus.banner = "🏠 " + it.text
                        Sounds.arrived()
                    }
                    "battery_dying" -> {
                        LoveBus.banner = it.text
                        Sounds.arrived()
                    }
                    else -> {}
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            showSettings -> {
                BackHandler { showSettings = false }
                SettingsScreen(vm, onClose = { showSettings = false }, onPairingChanged = onPairingChanged)
            }
            showAlerts -> {
                BackHandler { showAlerts = false }
                AlertsScreen(vm, onClose = { showAlerts = false })
            }
            else -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        HomeTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t.id,
                                onClick = { tab = t.id },
                                icon = {
                                    if (t == HomeTab.US && tab == t.id) {
                                        Icon(t.icon, t.label, modifier = Modifier.heartbeat())
                                    } else Icon(t.icon, t.label)
                                },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { pad ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .consumeWindowInsets(pad)
                ) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tabs",
                    ) { t ->
                        when (t) {
                            "us" -> UsTab(vm, onOpenAlerts = { showAlerts = true }, onCelebrate = { celebration = it })
                            "chat" -> ChatTab(vm)
                            "places" -> PlacesTab(vm)
                            "history" -> HistoryTab(vm)
                            else -> MapTab(
                                vm,
                                onOpenSettings = { showSettings = true },
                                onOpenAlerts = { showAlerts = true },
                            )
                        }
                    }
                }
            }
        }
        LoveOverlay()
        celebration?.let { m ->
            CelebrationOverlay(
                m, partnerProfileTop?.name?.ifBlank { null } ?: "your love", couple.anniversary,
                onClose = { celebration = null },
            )
        }
    }
}

// ---------------------------------------------------------------- Map tab

@Composable
private fun MapTab(vm: HomeViewModel, onOpenSettings: () -> Unit, onOpenAlerts: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val partner by vm.partner.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val profile by vm.partnerProfile.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    var fitKey by remember { mutableIntStateOf(0) }
    var addAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var confirmSos by remember { mutableStateOf(false) }
    var mapType by rememberSaveable { mutableIntStateOf(Prefs.mapType) }
    var typeMenu by remember { mutableStateOf(false) }
    val partnerName = profile?.name?.ifBlank { null } ?: "Your love"

    // Opening the map asks the partner's phone for a fresh position, like Life360.
    LaunchedEffect(Unit) { Cloud.ping() }

    // ---- Live driving: show the current drive in speed colours and keep their phone in live mode ----
    val partnerDriving = !vm.preview && partner?.let { it.driving && !it.paused } == true
    var follow by rememberSaveable { mutableStateOf(false) }
    val liveHist by remember(partnerDriving) {
        if (partnerDriving) Cloud.historyFlow(Cloud.partnerSlot, Cloud.dayKey(System.currentTimeMillis()))
        else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val liveDrawing = remember(liveHist, partner, pal.partnerHex) {
        val trip = (TripAnalyzer.analyze(liveHist).lastOrNull() as? Trip)?.takeIf { it.isDrive }
        val p = partner
        if (!partnerDriving || trip == null || p == null) null else {
            val base = buildDrawing(listOf(trip), emptyMap(), pal.partnerHex)
            val last = trip.points.last()
            // connect the recorded path to their live position
            base.copy(
                segs = base.segs + Seg(listOf(last.lat to last.lng, p.lat to p.lng), TripAnalyzer.speedHex(kmh(p.speed))),
                end = null,
            )
        }
    }
    LaunchedEffect(partnerDriving) {
        while (partnerDriving) {
            Cloud.ping() // their phone switches to 5-second updates for 3 minutes
            delay(150_000)
        }
    }

    val myPhoto by Avatars.mine.collectAsStateWithLifecycle()
    val partnerPhoto by vm.partnerAvatar.collectAsStateWithLifecycle()
    val pendingWrites by Cloud.pending.collectAsStateWithLifecycle()
    val online by Cloud.connected.collectAsStateWithLifecycle()
    val people = buildList {
        me?.takeIf { !it.paused }?.let {
            add(
                MapPerson(
                    "me", it.lat, it.lng, pal.meHex, Prefs.myName.take(1).uppercase(), Prefs.myMood,
                    myPhoto, myPhoto?.let { b -> System.identityHashCode(b).toString() } ?: "",
                )
            )
        }
        partner?.takeIf { !it.paused }?.let {
            add(
                MapPerson(
                    "partner", it.lat, it.lng, pal.partnerHex, partnerName.take(1).uppercase(), profile?.mood.orEmpty(),
                    partnerPhoto, partnerPhoto?.let { b -> System.identityHashCode(b).toString() } ?: "",
                )
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        TetherMap(
            Modifier.fillMaxSize(), people, places, fitKey = fitKey,
            linkHex = pal.heartHex,
            placeHex = pal.meHex,
            mapType = mapType,
            dark = pal.dark,
            drawing = liveDrawing,
            followTarget = if (follow && partnerDriving) partner?.let { it.lat to it.lng } else null,
            onLongPress = { lat, lng -> addAt = lat to lng },
        )

        // Top-left: distance pill (+ preview badge)
        Column(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            apartParts(me, partner)?.let { (value, label) ->
                DistancePill(
                    value, label,
                    myName = Prefs.myName.ifBlank { "Me" }, myPhoto = myPhoto,
                    partnerName = partnerName, partnerPhoto = partnerPhoto,
                )
            }
            if (!vm.preview && (!online || pendingWrites > 0)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (online) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Syncing $pendingWrites…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(
                            if (pendingWrites > 0) "☁️ Offline · $pendingWrites update${if (pendingWrites == 1) "" else "s"} waiting"
                            else "☁️ Offline",
                            style = MaterialTheme.typography.labelLarge, color = SosRed,
                        )
                    }
                }
            }
            if (vm.preview) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("👀 Preview · tap to pair", style = MaterialTheme.typography.labelLarge, color = pal.primary)
                }
            }
        }

        // Top-right: controls
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapButton(Icons.Filled.Settings, "Settings", onOpenSettings)
            MapButton(Icons.Filled.Favorite, "Switch pink / blue theme", tint = if (ThemeState.isPink) pal.heart else pal.primary) {
                ThemeState.toggleFamily()
            }
            Box {
                MapButton(Icons.Filled.Layers, "Map style") { typeMenu = true }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    listOf(MAP_STANDARD to "🗺️  Standard", MAP_SATELLITE to "🛰️  Satellite", MAP_TERRAIN to "⛰️  Terrain")
                        .forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    mapType = type
                                    Prefs.mapType = type
                                    typeMenu = false
                                },
                                trailingIcon = {
                                    if (mapType == type) Icon(Icons.Filled.Check, null, tint = pal.primary)
                                },
                            )
                        }
                }
            }
            MapButton(Icons.Filled.Notifications, "Activity", onOpenAlerts)
            MapButton(Icons.Filled.CenterFocusStrong, "Show both of us") { fitKey++; Cloud.ping() }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (partnerDriving) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveBadge(partner?.let { kmh(it.speed) })
                    Spacer(Modifier.width(8.dp))
                    Text("$partnerName is driving", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { follow = !follow }) { Text(if (follow) "Following ✓" else "Follow") }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (Prefs.paused) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Your sharing is paused") },
                        leadingIcon = { Icon(Icons.Filled.PauseCircle, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                } else Spacer(Modifier)
                Button(
                    onClick = { confirmSos = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(60.dp).shadow(8.dp, CircleShape),
                ) { Text("SOS", fontWeight = FontWeight.Black, fontSize = 17.sp) }
            }
            PartnerCard(partner, profile, partnerPhoto, onFocus = { fitKey++ })
        }
    }

    addAt?.let { (lat, lng) ->
        AddPlaceDialog(lat, lng, onDismiss = { addAt = null })
    }

    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = SosRed) },
            title = { Text("Send SOS?") },
            text = { Text("$partnerName's phone will sound a loud alarm and show your exact location.") },
            confirmButton = {
                Button(
                    onClick = { sendSos(ctx, me); confirmSos = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                ) { Text("Send SOS") }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("Cancel") } },
        )
    }
}

/** Glassy pill: both avatars overlapping, a beating heart, and the distance in gradient text. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun DistancePill(
    value: String,
    label: String,
    myName: String,
    myPhoto: android.graphics.Bitmap?,
    partnerName: String,
    partnerPhoto: android.graphics.Bitmap?,
) {
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .shadow(10.dp, shape, ambientColor = pal.primary, spotColor = pal.primary)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.5.dp, pal.brush, shape)
            .padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(58.dp).height(36.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { Avatar(myName, pal.me, null, 30.dp, photo = myPhoto) }
            Box(Modifier.align(Alignment.CenterEnd)) { Avatar(partnerName, pal.partner, null, 30.dp, photo = partnerPhoto) }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.Favorite, null, tint = pal.heart,
            modifier = Modifier.size(18.dp).heartbeat(),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(brush = pal.brush, fontWeight = FontWeight.Black),
                lineHeight = 18.sp,
            )
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp,
            )
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, desc: String, onClick: () -> Unit) =
    MapButton(icon, desc, tint = MaterialTheme.colorScheme.primary, onClick = onClick)

@Composable
private fun MapButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
    ) { Icon(icon, desc, tint = tint) }
}

private fun sendSos(ctx: Context, me: LivePoint?) {
    val loc = LocationService.lastLocation
    val lat = loc?.latitude ?: me?.lat
    val lng = loc?.longitude ?: me?.lng
    val who = Prefs.myName.ifBlank { "Your partner" }
    Cloud.postEvent("sos", "$who sent an SOS! Open Tether to see where they are.", lat, lng)
    runCatching { LocationService.refresh(ctx) }
    Toast.makeText(ctx, "SOS sent", Toast.LENGTH_LONG).show()
}

@Composable
private fun PartnerCard(partner: LivePoint?, profile: Profile?, photo: android.graphics.Bitmap?, onFocus: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val now = rememberNow()
    val name = profile?.name?.ifBlank { null } ?: "Your love"
    var address by remember { mutableStateOf<String?>(null) }
    val p = partner
    val geoKey = p?.let { "%.3f,%.3f".format(it.lat, it.lng) }
    LaunchedEffect(geoKey) {
        if (p != null && !p.paused) address = reverseGeocode(ctx, p.lat, p.lng)
    }

    AnimatedVisibility(visible = true, enter = slideInVertically { it } + fadeIn()) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, pal.partner, profile?.mood?.ifEmpty { null }, 48.dp, photo = photo)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        val (statusIcon, status) = when {
                            p == null -> Icons.Filled.Schedule to "Waiting for first location…"
                            p.paused -> Icons.Filled.PauseCircle to "Location sharing paused"
                            p.driving -> Icons.Filled.DirectionsCar to "Driving · ${kmh(p.speed)} km/h"
                            p.place != null -> Icons.Filled.Place to "At ${p.place}"
                            else -> Icons.Filled.LocationOn to (address ?: "Locating…")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusIcon, null, tint = pal.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (p != null) {
                            val stale = now - p.ts > 15 * 60_000L
                            Text(
                                "Updated ${ago(p.ts, now)}" + if (!p.paused && p.acc > 0) " · ±${p.acc.roundToInt()} m" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (stale) SosRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (p != null && p.battery >= 0) BatteryBadge(p.battery, p.charging)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CardAction(Icons.Filled.Refresh, "Refresh") {
                        Cloud.ping()
                        onFocus()
                        Toast.makeText(ctx, "Asking $name's phone for a fresh location…", Toast.LENGTH_SHORT).show()
                    }
                    CardAction(Icons.Filled.Directions, "Directions", enabled = p != null && !p.paused) {
                        p?.let { openDirections(ctx, it.lat, it.lng, name) }
                    }
                    CardAction(Icons.Filled.Call, "Call", enabled = !profile?.phone.isNullOrBlank()) {
                        profile?.phone?.let { dial(ctx, it) }
                    }
                    CardAction(Icons.Filled.Favorite, "Heart", highlight = true) {
                        Love.byId("heart")?.let { Cloud.sendLove(it) }
                        LoveBus.burst("❤️")
                    }
                }
            }
        }
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick, enabled = enabled,
            colors = if (highlight) IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = pal.heart, contentColor = Color.White,
            ) else IconButtonDefaults.filledTonalIconButtonColors(),
            modifier = if (highlight) Modifier.heartbeat() else Modifier,
        ) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun BatteryBadge(level: Int, charging: Boolean) {
    val color = when {
        charging -> MaterialTheme.colorScheme.primary
        level <= 15 -> SosRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                charging -> Icons.Filled.BatteryChargingFull
                level <= 15 -> Icons.Filled.BatteryAlert
                else -> Icons.Filled.BatteryFull
            },
            "Battery", tint = color, modifier = Modifier.size(20.dp),
        )
        Text("$level%", color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Add a new place at [lat],[lng], or edit [existing] (rename, resize, or move to where you are now). */
@Composable
fun AddPlaceDialog(lat: Double, lng: Double, onDismiss: () -> Unit, existing: Place? = null) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var radius by remember { mutableFloatStateOf(existing?.radius?.toFloat() ?: 150f) }
    var pos by remember { mutableStateOf(lat to lng) }
    var moved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a place 📍" else "Edit place ✏️") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (Home, Work, Gym…)") }, singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("🏠 Home", "💼 Work", "🎓 School", "💪 Gym", "🛒 Shop", "❤️ Bae's").forEach { s ->
                        SuggestionChip(onClick = { name = s }, label = { Text(s, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Alert radius: ${radius.roundToInt()} m")
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..500f, steps = 8)
                if (existing != null) {
                    OutlinedButton(onClick = {
                        val l = LocationService.lastLocation
                        if (l != null) {
                            pos = l.latitude to l.longitude
                            moved = true
                        } else {
                            Toast.makeText(ctx, "Waiting for your location…", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(if (moved) "📍 Moved to where you are ✓" else "📍 Move to where I am now") }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "You'll both get notified when either of you arrives or leaves.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    Cloud.savePlace(Place(existing?.id ?: "", name.trim(), pos.first, pos.second, radius.toDouble()))
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(vm: HomeViewModel, onClose: () -> Unit, onPairingChanged: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val profile by vm.partnerProfile.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(Prefs.myName) }
    var phone by remember { mutableStateOf(Prefs.myPhone) }
    var paused by remember { mutableStateOf(Prefs.paused) }
    var hiAcc by remember { mutableStateOf(Prefs.highAccuracy) }
    var crash by remember { mutableStateOf(Prefs.crashDetection) }
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("Look")
        ThemePicker()

        SectionTitle("Your profile")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            Prefs.myName = name.trim()
            Prefs.myPhone = phone.trim()
            Cloud.saveProfile()
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
        }) { Text("Save profile") }

        PhotoRow()

        SectionTitle("Sounds & decorations")
        var sounds by remember { mutableStateOf(Prefs.sounds) }
        var seasonal by remember { mutableStateOf(Prefs.seasonal) }
        SwitchRow("Soft chimes 🔔", "Little sounds when love is sent or received, and when your love arrives somewhere.", sounds) {
            sounds = it; Prefs.sounds = it
            if (it) Sounds.sent()
        }
        SwitchRow("Seasonal decorations 🎃", "Holiday touches for Christmas, Diwali, Phagwah, Mashramani, Valentine's and more.", seasonal) {
            seasonal = it; Prefs.seasonal = it
        }
        OutlinedButton(onClick = {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            }
        }) { Text("Notification sounds") }

        SectionTitle("Sharing")
        SwitchRow("Pause location sharing", "Your partner sees \"paused\" instead of your location.", paused) {
            paused = it; Prefs.paused = it; LocationService.refresh(ctx)
        }
        SwitchRow("High accuracy", "More precise location when not driving. Uses more battery.", hiAcc) {
            hiAcc = it; Prefs.highAccuracy = it; LocationService.refresh(ctx)
        }
        SwitchRow("Crash detection", "While driving, a hard impact starts a 30-second countdown, then alerts your partner.", crash) {
            crash = it; Prefs.crashDetection = it; LocationService.refresh(ctx)
        }
        OutlinedButton(onClick = {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                )
            }
        }) { Text("App permissions & battery settings") }

        BackupSection(vm, onPairingChanged)

        SectionTitle("Pairing")
        if (vm.preview) {
            Text("You're in preview mode — the partner you see is a demo.")
            Button(onClick = {
                Prefs.skippedPairing = false
                onPairingChanged()
            }) { Text("Pair with my love now 💞") }
        } else {
            Text("Paired with ${profile?.name?.ifBlank { null } ?: "your love"} 💞")
            Text(
                "Locations, places, messages and love notes are encrypted on this phone with AES-256-GCM using a key only your two phones hold. The server only stores scrambled data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmUnpair = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SosRed),
            ) { Text("Unpair this phone") }
        }
        Text("Tether 1.6 · made with 💗", style = MaterialTheme.typography.labelSmall, color = pal.primary)
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("Sharing stops and you'll both need to pair again with a new code.") },
            confirmButton = {
                Button(
                    onClick = {
                        LocationService.stop(ctx)
                        runCatching { Cloud.circle().child("live").child(Cloud.mySlot).removeValue() }
                        Prefs.clearPairing()
                        Prefs.skippedPairing = false
                        confirmUnpair = false
                        onPairingChanged()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                ) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhotoRow() {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    val photo by Avatars.mine.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (!Avatars.setFromUri(ctx, uri)) Toast.makeText(ctx, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(Prefs.myName.ifBlank { "Me" }, pal.me, null, 56.dp, photo = photo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Your photo", fontWeight = FontWeight.SemiBold)
            Text(
                "Shown on your map pin and the Us tab. Stored as a small encrypted thumbnail.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(if (photo == null) "Choose photo 📷" else "Change photo") }
        if (photo != null) TextButton(onClick = { Avatars.clear() }) { Text("Remove") }
    }
}

@Composable
private fun BackupSection(vm: HomeViewModel, onPairingChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by vm.places.collectAsStateWithLifecycle()
    val couple by vm.couple.collectAsStateWithLifecycle()
    var showBackup by remember { mutableStateOf(false) }
    var includePairing by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePw by remember { mutableStateOf("") }
    var askRestorePw by remember { mutableStateOf(false) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        val pw = password
        val withPairing = includePairing
        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    Backup.write(ctx, uri, Backup.build(places, couple, withPairing), pw.ifEmpty { null })
                }
                toast("Backup saved 💾" + if (pw.isNotEmpty()) " (encrypted)" else "")
            } catch (e: Exception) {
                toast("Backup failed: ${e.message}")
            } finally {
                busy = false
                password = ""
            }
        }
    }

    fun doRestore(uri: android.net.Uri, pw: String?) {
        busy = true
        scope.launch {
            try {
                val data = withContext(Dispatchers.Default) { Backup.read(ctx, uri, pw) }
                val r = Backup.apply(data)
                ThemeState.load()
                askRestorePw = false
                restorePw = ""
                val msg = buildString {
                    append("Restored settings and ${r.places} place${if (r.places == 1) "" else "s"}")
                    if (r.queuedForLater) append(" (places will sync once you pair)")
                    if (r.restoredPairing) append(" · pairing restored 💞")
                    r.pairingError?.let { append(" · pairing not restored: $it") }
                }
                toast(msg)
                if (r.restoredPairing) {
                    runCatching { LocationService.start(ctx) }
                    onPairingChanged()
                }
            } catch (e: Backup.NeedsPassword) {
                restoreUri = uri
                askRestorePw = true
            } catch (e: Exception) {
                toast(e.message ?: "Restore failed")
            } finally {
                busy = false
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) doRestore(uri, null)
    }

    SectionTitle("Backup & restore")
    Text(
        "Save your settings, profile, theme, places and special dates to a file. Restore it after a reinstall or on a new phone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { showBackup = true }, enabled = !busy) { Text("Back up 💾") }
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, enabled = !busy) {
            Text("Restore")
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
    }

    if (showBackup) {
        val pwOk = if (includePairing) password.length >= 6 else password.isEmpty() || password.length >= 6
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text("Back up Tether 💾") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Includes your profile, theme & settings, ${places.size} saved place${if (places.size == 1) "" else "s"}, and your anniversary / next date.")
                    if (!vm.preview) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Include pairing key", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Lets you restore without re-pairing. Requires a password.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = includePairing, onCheckedChange = { includePairing = it })
                        }
                    }
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(if (includePairing) "Password (required, 6+ chars)" else "Password (optional, 6+ chars)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Text(
                        "Keep the file somewhere private. Without the password it can't be opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(enabled = pwOk, onClick = {
                    showBackup = false
                    createLauncher.launch("tether-backup-${java.time.LocalDate.now()}.json")
                }) { Text("Choose where to save") }
            },
            dismissButton = { TextButton(onClick = { showBackup = false }) { Text("Cancel") } },
        )
    }

    if (askRestorePw) {
        AlertDialog(
            onDismissRequest = { askRestorePw = false; restorePw = "" },
            title = { Text("Backup password 🔒") },
            text = {
                OutlinedTextField(
                    value = restorePw, onValueChange = { restorePw = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                Button(enabled = restorePw.isNotEmpty() && !busy, onClick = {
                    restoreUri?.let { doRestore(it, restorePw) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { askRestorePw = false; restorePw = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemePicker() {
    val pal = LocalPalette.current
    Text("Appearance", fontWeight = FontWeight.SemiBold)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("System", "Light ☀️", "Dark 🌙").forEachIndexed { i, label ->
            SegmentedButton(
                selected = ThemeState.mode == i,
                onClick = { ThemeState.changeMode(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
            ) { Text(label) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("Pinks 💗", fontWeight = FontWeight.SemiBold)
    SwatchRow(Themes.pinks)
    Text("Blues 💙", fontWeight = FontWeight.SemiBold)
    SwatchRow(Themes.blues)
    // live preview
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pal.brush),
        contentAlignment = Alignment.Center,
    ) {
        Text("${ThemeState.spec.name} 💞", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
private fun SwatchRow(specs: List<ThemeSpec>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        specs.forEach { s ->
            val selected = ThemeState.themeId == s.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { ThemeState.setTheme(s.id) }.padding(4.dp),
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(s.gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
                }
                Text(s.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
