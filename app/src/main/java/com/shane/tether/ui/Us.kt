@file:OptIn(ExperimentalMaterial3Api::class)

package com.shane.tether.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shane.tether.Cloud
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.shane.tether.Avatars
import com.shane.tether.Couple
import kotlinx.coroutines.launch
import com.shane.tether.DayItem
import com.shane.tether.GyHoliday
import com.shane.tether.Holidays
import com.shane.tether.NationalDays
import com.shane.tether.LocationService
import com.shane.tether.Love
import com.shane.tether.Prefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UsTab(vm: HomeViewModel, onOpenAlerts: () -> Unit, onCelebrate: (Milestone) -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val partner by vm.partner.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val profile by vm.partnerProfile.collectAsStateWithLifecycle()
    val couple by vm.couple.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val now = rememberNow()
    val partnerName = profile?.name?.ifBlank { null } ?: "Your love"
    val myName = Prefs.myName.ifBlank { "Me" }

    var pickAnniversary by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf(false) }
    var myMood by remember { mutableStateOf(Prefs.myMood) }
    val myPhoto by Avatars.mine.collectAsStateWithLifecycle()
    val partnerPhoto by vm.partnerAvatar.collectAsStateWithLifecycle()
    val recap by vm.recap.collectAsStateWithLifecycle()
    val recapLoading by vm.recapLoading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (!Avatars.setFromUri(ctx, uri)) Toast.makeText(ctx, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
        }
    }
    fun choosePhoto() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Seasonal decorations (fixed dates + Guyana's moveable holidays)
    var gy by remember { mutableStateOf<List<GyHoliday>>(emptyList()) }
    LaunchedEffect(Unit) {
        gy = runCatching { Holidays.guyana(ctx, LocalDate.now().year) }.getOrDefault(emptyList())
        vm.loadRecap()
    }
    val season = remember(gy, today, Prefs.seasonal) { Seasons.current(LocalDate.now(), gy) }
    val heroBrush = season?.let { Brush.linearGradient(it.gradient) } ?: pal.brush

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.primaryContainer.copy(alpha = 0.55f), pal.background), endY = 1400f))
    ) {
    season?.let { SeasonalAmbient(it) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (vm.preview) PreviewNote()

        // ---------- Greeting ----------
        GreetingCard(myName, partnerName, profile?.mood.orEmpty(), apartText(me, partner), couple, now, season)

        // ---------- Hero: names + days together ----------
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(heroBrush)
                .padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(myName, pal.me, myMood.ifEmpty { null }, 60.dp, photo = myPhoto, onClick = { choosePhoto() })
                    Text("💞", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 14.dp).heartbeat())
                    Avatar(partnerName, pal.partner, profile?.mood?.ifEmpty { null }, 60.dp, photo = partnerPhoto)
                }
                if (myPhoto == null) {
                    Text("Tap your circle to add a photo", color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "$myName & $partnerName", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                )
                val c = couple
                if (c.anniversary > 0L) {
                    val days = (todayEpochDay() - pickerEpochDay(c.anniversary)).coerceAtLeast(0L).toInt()
                    val shownDays by animateIntAsState(days, tween(1400), label = "days")
                    Text(
                        "$shownDays", color = Color.White, fontSize = 64.sp,
                        fontWeight = FontWeight.Black, lineHeight = 66.sp,
                        modifier = Modifier.clickable { onCelebrate(Milestones.preview(c.anniversary)) },
                    )
                    Text("days together", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    MilestonePill(c.anniversary, days)
                    TextButton(onClick = { pickAnniversary = true }) {
                        Text("Since ${prettyDate(c.anniversary)} · edit", color = Color.White.copy(alpha = 0.85f))
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { pickAnniversary = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = pal.primary),
                    ) { Text("Set our anniversary 💍") }
                }
                apartText(me, partner)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("📍 $it", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---------- Send love ----------
        SectionCard("Send some love 💌") {
            Love.taps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tap ->
                        BouncyEmoji(tap.emoji, tap.label, Modifier.weight(1f)) {
                            Cloud.sendLove(tap)
                            LoveBus.burst(tap.emoji)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = {
                    val l = LocationService.lastLocation
                    Cloud.postEvent("checkin", "$myName checked in: I'm safe ✅", l?.latitude, l?.longitude)
                    Toast.makeText(ctx, "Check-in sent", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("✅  I'm safe — check in") }
        }

        // ---------- Moods ----------
        SectionCard("How are you feeling?") {
            var showAll by rememberSaveable { mutableStateOf(false) }
            val shown = if (showAll) Love.moods else Love.moods.take(Love.MAIN_MOODS)
            shown.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (emoji, label) ->
                        BouncyEmoji(emoji, label, Modifier.weight(1f), selected = myMood == emoji) {
                            // Tap the selected mood again to clear it.
                            val next = if (myMood == emoji) "" else emoji
                            myMood = next
                            Cloud.setMood(next)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Fewer moods" else "More moods")
                    Icon(if (showAll) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
                Spacer(Modifier.weight(1f))
                if (myMood.isNotEmpty()) {
                    TextButton(onClick = { myMood = ""; Cloud.setMood("") }) { Text("Clear mood") }
                }
            }
            val pm = profile?.mood.orEmpty()
            if (pm.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "$partnerName is feeling $pm ${Love.moodLabel(pm)}" +
                        (profile?.moodTs?.takeIf { it > 0 }?.let { " · ${ago(it, now)}" } ?: ""),
                    color = pal.primary, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ---------- Next date ----------
        SectionCard("Next date 🗓️") {
            val c = couple
            val diff = if (c.nextDate > 0) pickerEpochDay(c.nextDate) - todayEpochDay() else -1L
            if (diff >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.nextLabel.ifBlank { "Date night" }, style = MaterialTheme.typography.titleMedium)
                        Text(prettyDate(c.nextDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        when (diff) {
                            0L -> "Today! 🥳"
                            1L -> "Tomorrow"
                            else -> "in $diff days"
                        },
                        style = MaterialTheme.typography.headlineSmall, color = pal.primary,
                    )
                }
                TextButton(onClick = { editDate = true }) { Text("Change") }
            } else {
                Text("Plan something to look forward to together.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { editDate = true }) { Text("Plan our next date 💑") }
            }
        }

        // ---------- Weekly recap ----------
        WeeklyRecapCard(recap, recapLoading, vm.preview) { vm.loadRecap(force = true) }

        // ---------- Question of the day ----------
        DailyQuestionCard(today, daily, partnerName)

        // ---------- National days ----------
        NationalDaysCard()

        // ---------- Love feed ----------
        SectionCard("Love notes 💗") {
            val love = events.filter { it.type == "love" || it.type == "checkin" }.take(8)
            if (love.isEmpty()) {
                Text("Send a heart to start your love feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            love.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Love.byId(e.kind)?.emoji ?: "✅", fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (e.from == Cloud.mySlot) "You: ${e.text}" else e.text)
                        Text(ago(e.ts, now), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onOpenAlerts) { Text("See all activity") }
        }
        Spacer(Modifier.height(8.dp))
    }
    }

    if (pickAnniversary) {
        DatePick(
            initial = couple.anniversary.takeIf { it > 0 },
            onDismiss = { pickAnniversary = false },
        ) { millis ->
            Cloud.saveCouple(couple.copy(anniversary = millis))
            pickAnniversary = false
            LoveBus.burst("💍")
        }
    }
    if (editDate) {
        NextDateDialog(couple, onDismiss = { editDate = false })
    }
}

@Composable
private fun MilestonePill(anniversary: Long, days: Int) {
    val today = LocalDate.now()
    val start = LocalDate.ofEpochDay(pickerEpochDay(anniversary))
    var next = start.withYear(today.year)
    if (next.isBefore(today)) next = next.plusYears(1)
    val toAnniv = next.toEpochDay() - today.toEpochDay()
    val years = next.year - start.year
    val next100 = (days / 100 + 1) * 100
    val to100 = next100 - days
    val text = when {
        toAnniv == 0L && years > 0 -> "🎉 Happy $years-year anniversary! 🎉"
        days > 0 && days % 100 == 0 -> "🎉 $days days today! 🎉"
        years > 0 && toAnniv < to100 -> "🎂 $years-year anniversary in $toAnniv days"
        else -> "💯 $next100 days in $to100 days"
    }
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun DailyQuestionCard(day: String, answers: Map<String, String>, partnerName: String) {
    val pal = LocalPalette.current
    val epochDay = remember(day) {
        runCatching { LocalDate.parse(day, DateTimeFormatter.BASIC_ISO_DATE).toEpochDay() }.getOrDefault(todayEpochDay())
    }
    val question = remember(epochDay) { Love.questionFor(epochDay) }
    val mine = answers[Cloud.mySlot]
    val theirs = answers[Cloud.partnerSlot]
    var draft by remember(day) { mutableStateOf("") }

    SectionCard("Question of the day ✨") {
        Text(question, style = MaterialTheme.typography.titleMedium, color = pal.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(targetState = mine, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "answer") { m ->
            if (m == null) {
                Column {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        placeholder = { Text("Your answer…") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        Cloud.saveDailyAnswer(day, draft.trim())
                        LoveBus.burst("💌")
                    }, enabled = draft.isNotBlank()) { Text("Send 💌") }
                }
            } else {
                AnswerBubble("You", m, pal.primaryContainer)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            theirs == null -> Text("Waiting for $partnerName to answer… ⏳", color = MaterialTheme.colorScheme.onSurfaceVariant)
            mine == null -> Text("🔒 $partnerName answered! Answer to unlock theirs.", color = pal.primary, fontWeight = FontWeight.SemiBold)
            else -> AnswerBubble(partnerName, theirs, pal.secondaryContainer)
        }
    }
}

@Composable
private fun AnswerBubble(who: String, text: String, bg: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp)
    ) {
        Text(who, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun DatePick(initial: Long?, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onPicked) }, enabled = state.selectedDateMillis != null) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun NextDateDialog(couple: Couple, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(couple.nextLabel) }
    var date by remember { mutableStateOf(couple.nextDate.takeIf { it > 0 }) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Our next date 💑") },
        text = {
            Column {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("What are we doing?") }, singleLine = true,
                    placeholder = { Text("Dinner at the seawall 🌅") },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date?.let { prettyDate(it) } ?: "Pick a day")
                }
            }
        },
        confirmButton = {
            Button(enabled = date != null, onClick = {
                Cloud.saveCouple(couple.copy(nextDate = date ?: 0L, nextLabel = label.trim()))
                LoveBus.burst("🥂")
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (couple.nextDate > 0) {
                    TextButton(onClick = {
                        Cloud.saveCouple(couple.copy(nextDate = 0L, nextLabel = ""))
                        onDismiss()
                    }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (picking) {
        DatePick(date, onDismiss = { picking = false }) {
            date = it
            picking = false
        }
    }
}

@Composable
fun PreviewNote() {
    val pal = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(pal.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("👀", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Preview mode — your partner here is a demo. Pair from Settings to make it real.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** National Day Calendar (bundled) + worldwide observances (Wikipedia) + Guyana public holidays. */
@Composable
private fun NationalDaysCard() {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val date = remember(offset) { LocalDate.now().plusDays(offset.toLong()) }
    var national by remember { mutableStateOf<List<DayItem>?>(null) }
    var wiki by remember { mutableStateOf<List<DayItem>?>(null) }
    var wikiError by remember { mutableStateOf(false) }
    var gy by remember { mutableStateOf<List<GyHoliday>>(emptyList()) }
    var showAllNational by remember { mutableStateOf(false) }
    var wikiOpen by rememberSaveable { mutableStateOf(false) }
    var openKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date) {
        national = null
        showAllNational = false
        openKey = null
        national = runCatching { NationalDays.on(ctx, date).map { NationalDays.toItem(it) } }.getOrDefault(emptyList())
    }
    LaunchedEffect(date) {
        wiki = null
        wikiError = false
        try {
            wiki = Holidays.forDay(ctx, date)
        } catch (e: Exception) {
            wikiError = true
        }
    }
    LaunchedEffect(date.year) {
        val thisYear = runCatching { Holidays.guyana(ctx, date.year) }.getOrDefault(emptyList())
        val nextYear = runCatching { Holidays.guyana(ctx, date.year + 1) }.getOrDefault(emptyList())
        gy = thisYear + nextYear
    }

    fun openUrl(url: String?) {
        url ?: return
        runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    SectionCard("Celebrate today 🎉") {
        // Day switcher
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (offset) {
                        0 -> "Today"
                        1 -> "Tomorrow"
                        -1 -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                    },
                    style = MaterialTheme.typography.titleMedium, color = pal.primary,
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { offset++ }) { Icon(Icons.Filled.ChevronRight, "Next day") }
        }
        if (offset != 0) {
            TextButton(onClick = { offset = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to today")
            }
        }

        // Guyana holiday banner
        val todayGy = gy.firstOrNull { it.date == date }
        val nextGy = gy.filter { it.date.isAfter(date) }.minByOrNull { it.date }
        if (todayGy != null || nextGy != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.brush)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    if (todayGy != null) "🇬🇾 ${todayGy.name} — public holiday in Guyana!"
                    else {
                        val d = nextGy!!.date.toEpochDay() - date.toEpochDay()
                        "🇬🇾 Next holiday: ${nextGy.name} · in $d day${if (d == 1L) "" else "s"}"
                    },
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ---- National days (bundled list) ----
        Spacer(Modifier.height(12.dp))
        val nat = national
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎈 National days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (nat != null) CountBadge(nat.size)
        }
        Spacer(Modifier.height(4.dp))
        when {
            nat == null -> SmallLoading("Unwrapping today's national days…")
            nat.isEmpty() -> Text("Nothing official today — invent your own holiday! 💫",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val visible = if (showAllNational) nat else nat.take(6)
                visible.forEach { item ->
                    val key = "n:" + item.title
                    DayRow(item, open = openKey == key, onToggle = { openKey = if (openKey == key) null else key }) {
                        openUrl(item.url)
                    }
                }
                if (nat.size > 6) {
                    TextButton(onClick = { showAllNational = !showAllNational }) {
                        Text(if (showAllNational) "Show less" else "Show all ${nat.size} national days")
                        Icon(if (showAllNational) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    }
                }
            }
        }

        // ---- Worldwide observances (Wikipedia), collapsible ----
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { wikiOpen = !wikiOpen }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌍 Around the world", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            wiki?.let { CountBadge(it.size) }
            Spacer(Modifier.weight(1f))
            Icon(
                if (wikiOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (wikiOpen) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = wikiOpen) {
            Column {
                val list = wiki
                when {
                    wikiError -> Text("Couldn't load worldwide observances. Check your internet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list == null -> SmallLoading("Checking the world's calendar…")
                    list.isEmpty() -> Text("No observances listed for this day.")
                    else -> list.forEach { item ->
                        val key = "w:" + item.title
                        DayRow(item, open = openKey == key, onToggle = { openKey = if (openKey == key) null else key }) {
                            openUrl(item.url)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Sources: National Day Calendar · Wikipedia · Nager.Date",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountBadge(n: Int) {
    val pal = LocalPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(pal.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text("$n", style = MaterialTheme.typography.labelSmall, color = pal.onPrimaryContainer, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SmallLoading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayRow(item: DayItem, open: Boolean, onToggle: () -> Unit, onRead: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) pal.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(10.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            item.extract?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text("💡 Celebrate together: ${item.idea}", color = pal.primary, fontWeight = FontWeight.SemiBold)
            if (item.url != null) {
                TextButton(onClick = onRead, contentPadding = PaddingValues(0.dp)) { Text("Read more →") }
            }
        }
    }
}

private val sweetLines = listOf(
    "You're my favourite notification 💌",
    "Home is wherever you are 🏡",
    "Still choosing you, every single day 💞",
    "I'd pick you in every universe 🌌",
    "My heart does a little skip when you text 💓",
    "Lucky me, I get to love you 🍀",
    "You make ordinary days feel special ✨",
    "Every road leads back to you 🛣️",
    "You're the best part of my day ☀️",
    "Can't wait for our next adventure 🗺️",
    "Thank you for being you 🌷",
    "Distance means so little when you mean so much 💫",
    "You're my person 🫶",
    "Falling for you all over again 🍂",
    "Us is my favourite place to be 💗",
)

/** "Good evening, Shane 🌆" + live date/time + a few little tidbits for today. */
@Composable
private fun GreetingCard(
    myName: String,
    partnerName: String,
    partnerMood: String,
    apart: String?,
    couple: Couple,
    now: Long,
    season: Season? = null,
) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val dt = remember(now) {
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
    }
    val hour = dt.hour
    val name = myName.takeIf { it != "Me" }?.let { ", $it" } ?: ""
    val greeting = when (hour) {
        in 0..4 -> "Up late$name? 🌙"
        in 5..11 -> "Good morning$name ☀️"
        in 12..16 -> "Good afternoon$name 🌤️"
        in 17..20 -> "Good evening$name 🌆"
        else -> "Good night$name 🌙"
    }

    var todayNational by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(dt.toLocalDate()) {
        todayNational = runCatching { NationalDays.on(ctx, dt.toLocalDate()).map { it.name } }.getOrDefault(emptyList())
    }

    val tidbits = buildList {
        season?.let { add(it.particles.first() to it.greeting.substringBeforeLast(" ") + "!") }
        if (todayNational.isNotEmpty()) {
            val first = todayNational.first()
            add("🎈" to if (todayNational.size > 1) "It's $first + ${todayNational.size - 1} more today" else "It's $first today")
        }
        if (couple.nextDate > 0) {
            val d = pickerEpochDay(couple.nextDate) - todayEpochDay()
            when {
                d == 0L -> add("🥳" to "${couple.nextLabel.ifBlank { "Date" }} is today!")
                d == 1L -> add("💑" to "${couple.nextLabel.ifBlank { "Your date" }} is tomorrow")
                d in 2L..14L -> add("💑" to "${couple.nextLabel.ifBlank { "Next date" }} in $d days")
            }
        }
        if (couple.anniversary > 0) {
            val days = todayEpochDay() - pickerEpochDay(couple.anniversary)
            if (days > 0 && days % 100 == 0L) add("🎉" to "$days days together today!")
        }
        if (partnerMood.isNotEmpty()) add(partnerMood to "$partnerName is feeling ${Love.moodLabel(partnerMood).lowercase()}")
        apart?.let { add("📍" to it) }
        when (dt.dayOfWeek) {
            java.time.DayOfWeek.FRIDAY -> add("🥂" to "It's Friday — date night?")
            java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY -> add("🌴" to "Weekend vibes — make a memory together")
            java.time.DayOfWeek.MONDAY -> add("💪" to "New week — send a little encouragement")
            else -> {}
        }
        if (hour >= 22 || hour < 5) add("😴" to "Don't forget to say goodnight to $partnerName")
        else if (hour in 5..9) add("☕" to "A good-morning text goes a long way")
    }.take(4)

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, color = pal.primary)
            Text(
                dt.format(DateTimeFormatter.ofPattern("EEEE, MMMM d · h:mm a", Locale.getDefault())),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tidbits.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                tidbits.forEach { (emoji, text) ->
                    Row(
                        Modifier
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pal.primaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "“${sweetLines[(todayEpochDay() % sweetLines.size).toInt()]}”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = pal.primary,
            )
        }
    }
}
