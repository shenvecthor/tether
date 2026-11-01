package com.shane.tether.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.shane.tether.GyHoliday
import com.shane.tether.Prefs
import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random

data class Season(
    val id: String,
    val greeting: String,
    val particles: List<String>,
    val gradient: List<Color>,
)

/** Automatic decorations for holidays — fixed dates plus Guyana's moveable holidays from Nager.Date. */
object Seasons {
    private val guyanaFlag = listOf(Color(0xFF009E49), Color(0xFFFCD116), Color(0xFFCE1126))

    fun current(date: LocalDate, gy: List<GyHoliday>): Season? {
        if (!Prefs.seasonal) return null
        val md = date.monthValue * 100 + date.dayOfMonth
        fun near(vararg names: String, before: Long = 1, after: Long = 0) = gy.any { h ->
            names.any { h.name.contains(it, ignoreCase = true) } &&
                !date.isBefore(h.date.minusDays(before)) && !date.isAfter(h.date.plusDays(after))
        }
        return when {
            md == 1231 || md == 101 -> Season("newyear", "Happy New Year 🎆", listOf("🎆", "✨", "🥂"),
                listOf(Color(0xFF1E3A8A), Color(0xFFA855F7)))
            md in 1215..1226 -> Season("christmas", "Merry Christmas 🎄", listOf("❄️", "🎄", "⛄", "✨"),
                listOf(Color(0xFF15803D), Color(0xFFDC2626)))
            near("Deepavali", "Diwali", before = 2) -> Season("diwali", "Happy Diwali 🪔", listOf("🪔", "✨", "🌟"),
                listOf(Color(0xFFF59E0B), Color(0xFFB45309)))
            near("Phagwah", "Holi", before = 1) -> Season("phagwah", "Happy Phagwah 🌈", listOf("🌈", "💜", "💛", "💗", "💙"),
                listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFFF59E0B)))
            near("Eid") -> Season("eid", "Eid Mubarak 🌙", listOf("🌙", "⭐", "✨"),
                listOf(Color(0xFF0F766E), Color(0xFF1E3A8A)))
            near("Good Friday", before = 0, after = 3) -> Season("easter", "Happy Easter 🐣", listOf("🐣", "🌷", "🥚", "🪁"),
                listOf(Color(0xFFF472B6), Color(0xFF34D399)))
            md in 213..214 -> Season("valentine", "Happy Valentine's Day 💘", listOf("💖", "💘", "🌹", "💕"),
                listOf(Color(0xFFFF4D8D), Color(0xFFC2185B)))
            md in 220..223 -> Season("mash", "Happy Mashramani 🎉", listOf("🎉", "💛", "💚", "❤️", "🎊"), guyanaFlag)
            md == 505 -> Season("arrival", "Happy Arrival Day 🇬🇾", listOf("🇬🇾", "🌺", "✨"), guyanaFlag)
            md in 524..526 -> Season("independence", "Happy Independence Day 🇬🇾", listOf("🇬🇾", "💛", "💚", "❤️"), guyanaFlag)
            md == 801 -> Season("emancipation", "Happy Emancipation Day 🇬🇾", listOf("🌺", "💛", "💚"), guyanaFlag)
            md in 1029..1031 -> Season("halloween", "Happy Halloween 🎃", listOf("🎃", "🦇", "👻", "🍬"),
                listOf(Color(0xFFFF8C1A), Color(0xFF6B21A8)))
            else -> null
        }
    }
}

/** Slow, faint emoji drifting down behind the content. */
@Composable
fun SeasonalAmbient(season: Season, count: Int = 14) {
    val items = remember(season.id) {
        List(count) {
            Triple(Random.nextFloat(), Random.nextInt(9_000, 16_000), Random.nextInt(0, 9_000)) to season.particles.random()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val t = rememberInfiniteTransition(label = "season")
        items.forEachIndexed { i, (spec, emoji) ->
            val (x, dur, delay) = spec
            val p by t.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(dur, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = StartOffset(delay),
                ),
                label = "p$i",
            )
            Text(
                emoji,
                fontSize = (16 + (i % 4) * 4).sp,
                modifier = Modifier.graphicsLayer {
                    translationX = x * w + sin(p * 6.28f + i) * 24f
                    translationY = -60f + p * (h + 120f)
                    alpha = 0.32f
                    rotationZ = sin(p * 3.14f + i) * 25f
                },
            )
        }
    }
}
