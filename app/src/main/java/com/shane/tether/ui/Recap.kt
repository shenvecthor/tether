package com.shane.tether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shane.tether.Cloud
import com.shane.tether.HistPoint
import com.shane.tether.Place
import com.shane.tether.Prefs
import com.shane.tether.Stay
import com.shane.tether.TEvent
import com.shane.tether.Trip
import com.shane.tether.TripAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class Recap(
    val from: LocalDate,
    val to: LocalDate,
    val togetherMin: Int,
    val km: Double,
    val drives: Int,
    val topKmh: Int,
    val placesVisited: Int,
    val favouritePlace: String?,
    val tapsSent: Int,
    val tapsReceived: Int,
)

object Recaps {
    /** Last 7 days (including today) for both of you. */
    suspend fun compute(places: List<Place>, events: List<TEvent>): Recap {
        val now = System.currentTimeMillis()
        val days = (0..6).map { Cloud.dayKey(now - it * 86_400_000L) }
        val mine = days.flatMap { runCatching { Cloud.fetchHistory(Cloud.mySlot, it) }.getOrDefault(emptyList()) }
        val theirs = days.flatMap { runCatching { Cloud.fetchHistory(Cloud.partnerSlot, it) }.getOrDefault(emptyList()) }
        val pl = places.ifEmpty { runCatching { Cloud.fetchPlaces() }.getOrDefault(emptyList()) }
        return withContext(Dispatchers.Default) {
            val segA = TripAnalyzer.analyze(mine)
            val segB = TripAnalyzer.analyze(theirs)
            val trips = (segA + segB).filterIsInstance<Trip>()
            val stays = (segA + segB).filterIsInstance<Stay>()
            val placeMinutes = HashMap<String, Int>()
            for (s in stays) {
                val p = pl.firstOrNull { TripAnalyzer.dist(it.lat, it.lng, s.lat, s.lng) <= it.radius + 40 } ?: continue
                placeMinutes[p.name] = (placeMinutes[p.name] ?: 0) + s.minutes
            }
            val weekAgo = now - 7 * 86_400_000L
            val love = events.filter { it.type == "love" && it.ts >= weekAgo }
            Recap(
                from = LocalDate.now().minusDays(6),
                to = LocalDate.now(),
                togetherMin = togetherMinutes(mine.sortedBy { it.ts }, theirs.sortedBy { it.ts }),
                km = trips.sumOf { it.distanceM } / 1000.0,
                drives = trips.count { it.isDrive },
                topKmh = trips.filter { it.isDrive }.maxOfOrNull { it.maxKmh } ?: 0,
                placesVisited = placeMinutes.size,
                favouritePlace = placeMinutes.maxByOrNull { it.value }?.key,
                tapsSent = love.count { it.from == Cloud.mySlot },
                tapsReceived = love.count { it.from != Cloud.mySlot },
            )
        }
    }

    /** Minutes where both phones were within 150 m of each other (matched within ±6 min). */
    private fun togetherMinutes(a: List<HistPoint>, b: List<HistPoint>): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var j = 0
        var totalMs = 0L
        for (i in a.indices) {
            val p = a[i]
            while (j + 1 < b.size && abs(b[j + 1].ts - p.ts) <= abs(b[j].ts - p.ts)) j++
            val q = b[j]
            if (abs(q.ts - p.ts) > 6 * 60_000L) continue
            if (TripAnalyzer.dist(p.lat, p.lng, q.lat, q.lng) > 150f) continue
            val next = a.getOrNull(i + 1)?.ts ?: (p.ts + 5 * 60_000L)
            totalMs += (next - p.ts).coerceIn(0L, 10 * 60_000L)
        }
        return (totalMs / 60_000L).toInt()
    }
}

@Composable
fun WeeklyRecapCard(recap: Recap?, loading: Boolean, preview: Boolean, onRefresh: () -> Unit) {
    val pal = LocalPalette.current
    val sunday = LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY
    SectionCard(if (sunday) "Your week together 🎁" else "This week 💞") {
        val r = recap
        when {
            preview -> Text("Pair with your love to see your weekly recap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            r == null && loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Putting your week together…")
            }
            r == null -> TextButton(onClick = onRefresh) { Text("Load our week") }
            else -> {
                val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                Text(
                    "${r.from.format(fmt)} – ${r.to.format(fmt)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                data class Tile(val icon: ImageVector, val value: String, val label: String, val tint: Color)
                val tiles = listOf(
                    Tile(Icons.Filled.People, durationLabel(r.togetherMin), "together", pal.heart),
                    Tile(Icons.Filled.Timeline, "%.0f km".format(r.km), "travelled", Color(0xFF6366F1)),
                    Tile(Icons.Filled.Place, "${r.placesVisited}", "places", Color(0xFF8B5CF6)),
                    Tile(Icons.Filled.Favorite, "${r.tapsSent + r.tapsReceived}", "love taps", Color(0xFFFF4D8D)),
                    Tile(Icons.Filled.DirectionsCar, "${r.drives}", "drives", Color(0xFF22C55E)),
                    Tile(Icons.Filled.Speed, "${r.topKmh} km/h", "top speed", Color(0xFFEF4444)),
                )
                tiles.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pal.primaryContainer.copy(alpha = 0.55f))
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(tile.tint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(tile.icon, tile.label, tint = tile.tint, modifier = Modifier.size(17.dp)) }
                                Spacer(Modifier.height(4.dp))
                                Text(tile.value, fontWeight = FontWeight.Bold)
                                Text(tile.label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                r.favouritePlace?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Favourite spot: $it", color = pal.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    when {
                        r.tapsSent > r.tapsReceived + 3 -> "You sent the most love this week — ${Prefs.myName.ifBlank { "you" }} wins 💘"
                        r.tapsReceived > r.tapsSent + 3 -> "You got spoiled with love this week 🥰"
                        r.togetherMin >= 600 -> "So much time together this week 💞"
                        else -> "Here's to an even sweeter week ahead ✨"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Filled.Refresh, "Refresh recap") }
                }
            }
        }
    }
}

fun durationLabel(min: Int): String = when {
    min >= 60 -> "${min / 60}h ${min % 60}m"
    else -> "${min}m"
}
