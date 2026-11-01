package com.shane.tether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

data class Burst(val id: Long, val emoji: String)

/** Fire-and-forget hearts / kisses that float up over the whole app. */
object LoveBus {
    val bursts = mutableStateListOf<Burst>()
    var banner by mutableStateOf<String?>(null)
    private var counter = 0L

    fun burst(emoji: String) {
        bursts.add(Burst(++counter, emoji))
    }

    fun done(id: Long) {
        bursts.removeAll { it.id == id }
    }
}

private class Particle(
    val x: Float,
    val drift: Float,
    val phase: Float,
    val delayMs: Int,
    val durMs: Int,
    val sizeSp: Int,
    val rot: Float,
)

@Composable
fun LoveOverlay() {
    Box(Modifier.fillMaxSize()) {
        LoveBus.bursts.toList().forEach { b -> key(b.id) { BurstView(b) } }

        var lastText by remember { mutableStateOf("") }
        val banner = LoveBus.banner
        if (banner != null) lastText = banner
        LaunchedEffect(banner) {
            if (banner != null) {
                delay(3_500)
                LoveBus.banner = null
            }
        }
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
        ) {
            val pal = LocalPalette.current
            Box(
                Modifier
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(pal.brush)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text(lastText, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BurstView(b: Burst) {
    val particles = remember {
        List(18) {
            Particle(
                x = Random.nextFloat() * 0.8f + 0.1f,
                drift = Random.nextFloat() * 2f - 1f,
                phase = Random.nextFloat() * 6.28f,
                delayMs = Random.nextInt(0, 600),
                durMs = Random.nextInt(1_800, 2_800),
                sizeSp = Random.nextInt(22, 46),
                rot = Random.nextFloat() * 60f - 30f,
            )
        }
    }
    LaunchedEffect(Unit) {
        delay(3_600)
        LoveBus.done(b.id)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        particles.forEach { p ->
            val prog = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(p.delayMs.toLong())
                prog.animateTo(1f, tween(p.durMs, easing = LinearOutSlowInEasing))
            }
            Text(
                b.emoji,
                fontSize = p.sizeSp.sp,
                modifier = Modifier.graphicsLayer {
                    val v = prog.value
                    translationX = p.x * w + sin(v * 7f + p.phase) * 60f * p.drift
                    translationY = h * 0.92f - v * h * 0.85f
                    alpha = when {
                        v == 0f -> 0f
                        v < 0.1f -> v * 10f
                        else -> 1f - (v - 0.1f) / 0.9f
                    }
                    val s = 0.5f + v * 0.9f
                    scaleX = s
                    scaleY = s
                    rotationZ = p.rot * v
                },
            )
        }
    }
}

/** Gently beating modifier for hearts. */
@Composable
fun Modifier.heartbeat(): Modifier {
    val t = rememberInfiniteTransition(label = "beat")
    val s by t.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beatScale",
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Big bouncy emoji button used for love taps and moods. */
@Composable
fun BouncyEmoji(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val pal = LocalPalette.current
    Column(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) pal.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) pal.primary else pal.surfaceVariant,
                RoundedCornerShape(20.dp),
            )
            .clickable {
                scope.launch {
                    scale.animateTo(1.22f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh))
                    scale.animateTo(1f, spring(dampingRatio = 0.45f))
                }
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 28.sp)
        Text(
            label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Round avatar: photo if set, otherwise a gradient with the initial; optional mood badge. */
@Composable
fun Avatar(
    name: String,
    color: Color,
    mood: String?,
    size: Dp = 48.dp,
    photo: android.graphics.Bitmap? = null,
    onClick: (() -> Unit)? = null,
) {
    val img = remember(photo) { photo?.asImageBitmap() }
    Box(Modifier.size(size + 6.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color)))
                .border(2.5.dp, Color.White, CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) {
                Image(
                    img, contentDescription = name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    name.take(1).uppercase().ifEmpty { "♥" },
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp,
                )
            }
        }
        if (!mood.isNullOrEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.48f)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text(mood, fontSize = (size.value * 0.27f).sp) }
        }
    }
}
