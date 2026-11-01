package com.shane.tether.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shane.tether.Cloud
import com.shane.tether.Love
import com.shane.tether.Prefs
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

data class Milestone(val emoji: String, val title: String, val days: Long, val preview: Boolean = false)

object Milestones {
    /** A celebration for today, if today is special: yearly anniversary, every 100 days, or monthly (first 2 years). */
    fun today(anniversaryMillis: Long, today: LocalDate = LocalDate.now()): Milestone? {
        if (anniversaryMillis <= 0L) return null
        val start = LocalDate.ofEpochDay(pickerEpochDay(anniversaryMillis))
        val days = today.toEpochDay() - start.toEpochDay()
        if (days <= 0) return null
        val period = Period.between(start, today)
        val sameDay = today.dayOfMonth == start.dayOfMonth
        return when {
            sameDay && today.monthValue == start.monthValue && period.years >= 1 ->
                Milestone("🎂", "Happy ${period.years}-year anniversary!", days)
            days % 100 == 0L -> Milestone("💯", "$days days together!", days)
            sameDay && period.toTotalMonths() in 1..24 -> {
                val m = period.toTotalMonths()
                Milestone("🗓️", "Happy $m-month anniversary!", days)
            }
            else -> null
        }
    }

    fun preview(anniversaryMillis: Long): Milestone {
        val days = if (anniversaryMillis > 0) todayEpochDay() - pickerEpochDay(anniversaryMillis) else 0L
        return Milestone("💞", "Look back on us", days, preview = true)
    }
}

/** Full-screen confetti + look-back card. */
@Composable
fun CelebrationOverlay(m: Milestone, partnerName: String, anniversary: Long, onClose: () -> Unit) {
    val pal = LocalPalette.current
    var mine by remember { mutableStateOf<Cloud.Stats?>(null) }
    var theirs by remember { mutableStateOf<Cloud.Stats?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Cloud.publishStats()
        mine = runCatching { Cloud.fetchStats(Cloud.mySlot) }.getOrNull()
        theirs = runCatching { Cloud.fetchStats(Cloud.partnerSlot) }.getOrNull()
        loaded = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Confetti(pal.gradient + listOf(pal.heart, Color(0xFFFFD166), Color.White))
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.emoji, fontSize = 64.sp, modifier = Modifier.heartbeat())
                Text(m.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = pal.primary)
                if (anniversary > 0) {
                    Text(
                        "${m.days} days together · since " +
                            LocalDate.ofEpochDay(pickerEpochDay(anniversary))
                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("Look back 💫", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val a = mine
                val b = theirs
                when {
                    !Prefs.isPaired -> Text("Pair with your love to see your look-back stats.", textAlign = TextAlign.Center)
                    !loaded -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> {
                        val first = listOfNotNull(a?.first, b?.first).filter { it > 0 }.minOrNull()
                        first?.let {
                            LookBackRow(Icons.Filled.Place, "First shared location",
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())))
                        }
                        LookBackRow(Icons.Filled.Timeline, "Distance travelled (both of you)", "%,.0f km".format((a?.km ?: 0.0) + (b?.km ?: 0.0)))
                        LookBackRow(Icons.Filled.Favorite, "Love taps sent", "%,d".format((a?.taps ?: 0) + (b?.taps ?: 0)))
                        LookBackRow(Icons.Filled.ChatBubble, "Messages", "%,d".format((a?.msgs ?: 0) + (b?.msgs ?: 0)))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose) { Text("Close") }
                    Button(onClick = {
                        Love.byId("kiss")?.let { Cloud.sendLove(it) }
                        LoveBus.burst("😘")
                        onClose()
                    }) { Text("Send $partnerName a kiss 😘") }
                }
            }
        }
    }
}

@Composable
private fun LookBackRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(pal.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = pal.primary, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private class Piece(
    val x0: Float, val vx: Float, val vy: Float, val w: Float, val h: Float,
    val spin: Float, val wobble: Float, val color: Color, val delay: Float,
)

/** Simple physics confetti: bursts up from the middle, then drifts down. */
@Composable
fun Confetti(colors: List<Color>, count: Int = 140) {
    val pieces = remember {
        List(count) {
            Piece(
                x0 = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 0.7f,
                vy = -(0.45f + Random.nextFloat() * 0.6f),
                w = 6f + Random.nextFloat() * 8f,
                h = 10f + Random.nextFloat() * 10f,
                spin = (Random.nextFloat() - 0.5f) * 720f,
                wobble = Random.nextFloat() * 6f,
                color = colors.random(),
                delay = Random.nextFloat() * 0.6f,
            )
        }
    }
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) { prog.animateTo(1f, tween(5_500, easing = LinearEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val total = prog.value * 5.5f
        pieces.forEach { p ->
            val t = (total - p.delay).coerceAtLeast(0f)
            if (t <= 0f) return@forEach
            val x = (p.x0 + p.vx * t + sin(t * p.wobble) * 0.02f) * size.width
            val y = (0.45f + p.vy * t + 0.42f * t * t) * size.height
            val alpha = (1f - ((total - 4.3f) / 1.2f)).coerceIn(0f, 1f)
            if (y > size.height + 40f || alpha <= 0f) return@forEach
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - p.w / 2, y - p.h / 2), size = Size(p.w, p.h))
            }
        }
    }
}
