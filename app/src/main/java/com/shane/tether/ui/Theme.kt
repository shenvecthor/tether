package com.shane.tether.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.shane.tether.Prefs

val Ink = Color(0xFF2A1F24)
val SosRed = Color(0xFFC62828)

/** A theme option the user can pick. Everything else is derived from these few colours. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val family: String, // "pink" or "blue"
    val primary: Color,
    val primaryDark: Color,
    val partner: Color,
    val heart: Color,
    val gradient: List<Color>,
    val bgLight: Color,
)

object Themes {
    val all = listOf(
        // ---- Pinks ----
        ThemeSpec("blush", "Blush", "pink", Color(0xFFD63D74), Color(0xFFFF7AA8), Color(0xFF9B5DE5), Color(0xFFFF4D8D),
            listOf(Color(0xFFFF8FB1), Color(0xFFD63D74)), Color(0xFFFFF4F7)),
        ThemeSpec("bubblegum", "Bubblegum", "pink", Color(0xFFF0287F), Color(0xFFFF6FB5), Color(0xFF7B61FF), Color(0xFFFF2E93),
            listOf(Color(0xFFFF6FB5), Color(0xFFB23CFD)), Color(0xFFFFF2F8)),
        ThemeSpec("sakura", "Sakura", "pink", Color(0xFFDB5C8A), Color(0xFFF7A8C4), Color(0xFFF28C6B), Color(0xFFFF7EA6),
            listOf(Color(0xFFF7A1BB), Color(0xFFDB5C8A)), Color(0xFFFFF6F8)),
        ThemeSpec("rosegold", "Rose Gold", "pink", Color(0xFFB0646F), Color(0xFFE8A3AC), Color(0xFFC08A3E), Color(0xFFE07A8A),
            listOf(Color(0xFFE3A99A), Color(0xFFB0646F)), Color(0xFFFBF3F1)),
        ThemeSpec("peachy", "Peachy Keen", "pink", Color(0xFFEC4F72), Color(0xFFFF8FA3), Color(0xFF6C5CE7), Color(0xFFFF5E7E),
            listOf(Color(0xFFFFA07A), Color(0xFFFF6A88), Color(0xFFE94E9B)), Color(0xFFFFF5F2)),
        ThemeSpec("berry", "Berry Kiss", "pink", Color(0xFFA4135A), Color(0xFFE0569B), Color(0xFFF08C3A), Color(0xFFE91E78),
            listOf(Color(0xFFD6336C), Color(0xFF6A0D52)), Color(0xFFFBF1F5)),
        // ---- Blues ----
        ThemeSpec("ocean", "Ocean", "blue", Color(0xFF0F5257), Color(0xFF4FC3B8), Color(0xFFE59A3C), Color(0xFFE0457B),
            listOf(Color(0xFF0F5257), Color(0xFF2E8C85)), Color(0xFFF4F8F8)),
        ThemeSpec("sky", "Sky", "blue", Color(0xFF2F80ED), Color(0xFF6FA8FF), Color(0xFFF2994A), Color(0xFFFF5C8A),
            listOf(Color(0xFF56CCF2), Color(0xFF2F80ED)), Color(0xFFF3F7FD)),
        ThemeSpec("midnight", "Midnight", "blue", Color(0xFF4F46E5), Color(0xFF8B85FF), Color(0xFFF472B6), Color(0xFFF472B6),
            listOf(Color(0xFF4F46E5), Color(0xFF9333EA)), Color(0xFFF4F4FC)),
    )

    fun byId(id: String): ThemeSpec = all.firstOrNull { it.id == id } ?: all.first()
    val pinks get() = all.filter { it.family == "pink" }
    val blues get() = all.filter { it.family == "blue" }
}

/** Colours resolved for the current theme + light/dark mode. */
data class Palette(
    val spec: ThemeSpec,
    val dark: Boolean,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val me: Color,
    val partner: Color,
    val heart: Color,
    val gradient: List<Color>,
) {
    val brush: Brush get() = Brush.linearGradient(gradient)
    val meHex: String get() = me.hex()
    val partnerHex: String get() = partner.hex()
    val heartHex: String get() = heart.hex()

    companion object {
        fun resolve(s: ThemeSpec, dark: Boolean): Palette {
            val black = Color(0xFF121014)
            return if (!dark) Palette(
                spec = s, dark = false,
                primary = s.primary,
                primaryContainer = lerp(Color.White, s.primary, 0.18f),
                onPrimaryContainer = lerp(s.primary, Color.Black, 0.35f),
                secondary = s.gradient.first(),
                secondaryContainer = lerp(Color.White, s.partner, 0.16f),
                background = s.bgLight,
                surface = Color.White,
                surfaceVariant = lerp(Color.White, s.primary, 0.10f),
                onSurface = Ink,
                me = s.primary,
                partner = s.partner,
                heart = s.heart,
                gradient = s.gradient,
            ) else Palette(
                spec = s, dark = true,
                primary = s.primaryDark,
                primaryContainer = lerp(Color(0xFF1E1B20), s.primaryDark, 0.32f),
                onPrimaryContainer = lerp(s.primaryDark, Color.White, 0.55f),
                secondary = s.gradient.first(),
                secondaryContainer = lerp(Color(0xFF1E1B20), s.partner, 0.30f),
                background = lerp(black, s.primary, 0.07f),
                surface = lerp(Color(0xFF1C1A1E), s.primary, 0.10f),
                surfaceVariant = lerp(Color(0xFF29262B), s.primaryDark, 0.16f),
                onSurface = Color(0xFFF3ECEF),
                me = s.primaryDark,
                partner = lerp(s.partner, Color.White, 0.15f),
                heart = s.heart,
                gradient = s.gradient.map { lerp(it, Color.Black, 0.18f) },
            )
        }
    }
}

fun Color.hex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

/** App-wide theme switches. */
object ThemeState {
    var themeId by mutableStateOf("blush")
        private set

    /** 0 = follow system, 1 = light, 2 = dark */
    var mode by mutableIntStateOf(0)
        private set

    val spec: ThemeSpec get() = Themes.byId(themeId)
    val isPink: Boolean get() = spec.family == "pink"

    fun load() {
        themeId = Prefs.themeId
        mode = Prefs.darkMode
    }

    fun setTheme(id: String) {
        themeId = id
        Prefs.themeId = id
        if (Themes.byId(id).family == "pink") Prefs.lastPink = id else Prefs.lastBlue = id
    }

    fun changeMode(m: Int) {
        mode = m
        Prefs.darkMode = m
    }

    /** Heart button on the map: hop between your favourite pink and favourite blue. */
    fun toggleFamily() = setTheme(if (isPink) Prefs.lastBlue else Prefs.lastPink)
}

val LocalPalette = staticCompositionLocalOf { Palette.resolve(Themes.all.first(), false) }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun TetherTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = Palette.resolve(ThemeState.spec, dark)
    val anim = tween<Color>(600)
    val primary by animateColorAsState(p.primary, anim, label = "primary")
    val primaryContainer by animateColorAsState(p.primaryContainer, anim, label = "pc")
    val onPrimaryContainer by animateColorAsState(p.onPrimaryContainer, anim, label = "opc")
    val secondaryContainer by animateColorAsState(p.secondaryContainer, anim, label = "sc")
    val background by animateColorAsState(p.background, anim, label = "bg")
    val surface by animateColorAsState(p.surface, anim, label = "sf")
    val surfaceVariant by animateColorAsState(p.surfaceVariant, anim, label = "sv")
    val onSurface by animateColorAsState(p.onSurface, anim, label = "os")
    val onSurfaceVariant = if (dark) Color(0xFFC9BCC2) else Color(0xFF6B5A61)

    // Status / navigation bar icons follow the app's mode, not just the system's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    val scheme = if (dark) darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF1A0E13),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = p.secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSurface,
        tertiary = p.partner,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        error = Color(0xFFFF6B6B),
    ) else lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = p.secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSurface,
        tertiary = p.partner,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surface,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = surfaceVariant,
        error = SosRed,
    )

    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
