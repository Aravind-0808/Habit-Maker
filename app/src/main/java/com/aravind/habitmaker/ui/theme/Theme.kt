package com.aravind.habitmaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aravind.habitmaker.core.prefs.ThemeMode

/** Redmind design tokens (shared with DataForge): zinc greys, a near-black primary, semantic accents. */
@Immutable
data class AppColors(
    val background: Color,
    val subtle: Color,
    val surface: Color,
    val sunken: Color,
    val foreground: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val isDark: Boolean,
)

val Emerald = Color(0xFF10B981)
val Rose = Color(0xFFF43F5E)
val Amber = Color(0xFFF59E0B)
val Violet = Color(0xFF8B5CF6)
val Blue = Color(0xFF3B82F6)

private val Light = AppColors(
    background = Color(0xFFFFFFFF), subtle = Color(0xFFFAFAFA), surface = Color(0xFFFFFFFF), sunken = Color(0xFFF4F4F5),
    foreground = Color(0xFF09090B), muted = Color(0xFF71717A), faint = Color(0xFFA1A1AA),
    border = Color(0xFFE4E4E7), borderStrong = Color(0xFFD4D4D8),
    primary = Color(0xFF18181B), primarySoft = Color(0xFFF4F4F5), onPrimary = Color(0xFFFAFAFA),
    success = Color(0xFF16A34A), successSoft = Color(0xFFF0FDF4),
    warning = Color(0xFFD97706), warningSoft = Color(0xFFFFFBEB),
    danger = Color(0xFFDC2626), dangerSoft = Color(0xFFFEF2F2),
    info = Color(0xFF2563EB), infoSoft = Color(0xFFEFF6FF),
    heroStart = Color(0xFF18181B), heroEnd = Color(0xFF3F3F46), onHero = Color.White,
    isDark = false,
)

private val Dark = AppColors(
    background = Color(0xFF09090B), subtle = Color(0xFF18181B), surface = Color(0xFF09090B), sunken = Color(0xFF18181B),
    foreground = Color(0xFFFAFAFA), muted = Color(0xFFA1A1AA), faint = Color(0xFF71717A),
    border = Color(0xFF27272A), borderStrong = Color(0xFF3F3F46),
    primary = Color(0xFFFAFAFA), primarySoft = Color(0x1FFAFAFA), onPrimary = Color(0xFF18181B),
    success = Color(0xFF22C55E), successSoft = Color(0x2622C55E),
    warning = Color(0xFFF59E0B), warningSoft = Color(0x26F59E0B),
    danger = Color(0xFFEF4444), dangerSoft = Color(0x26EF4444),
    info = Color(0xFF3B82F6), infoSoft = Color(0x263B82F6),
    heroStart = Color(0xFFF4F4F5), heroEnd = Color(0xFFD4D4D8), onHero = Color(0xFF09090B),
    isDark = true,
)

/** Accent colour themes. ZINC is the original near-black DataForge look. */
enum class Accent(val label: String, val light: Color, val lightEnd: Color, val dark: Color, val darkEnd: Color) {
    ZINC("Classic", Color(0xFF18181B), Color(0xFF3F3F46), Color(0xFFFAFAFA), Color(0xFFD4D4D8)),
    EMERALD("Emerald", Color(0xFF059669), Color(0xFF047857), Color(0xFF34D399), Color(0xFF10B981)),
    BLUE("Ocean", Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFF3B82F6)),
    VIOLET("Violet", Color(0xFF7C3AED), Color(0xFF6D28D9), Color(0xFFA78BFA), Color(0xFF8B5CF6)),
    ROSE("Rose", Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFFFB7185), Color(0xFFF43F5E)),
    AMBER("Sunset", Color(0xFFD97706), Color(0xFFB45309), Color(0xFFFBBF24), Color(0xFFF59E0B)),
}

private fun AppColors.withAccent(a: Accent): AppColors = if (a == Accent.ZINC) this else if (isDark) copy(
    primary = a.dark, primarySoft = a.dark.copy(alpha = 0.15f), onPrimary = Color(0xFF09090B),
    heroStart = a.dark, heroEnd = a.darkEnd, onHero = Color(0xFF09090B),
) else copy(
    primary = a.light, primarySoft = a.light.copy(alpha = 0.1f), onPrimary = Color.White,
    heroStart = a.light, heroEnd = a.lightEnd, onHero = Color.White,
)

val LocalAppColors = staticCompositionLocalOf { Light }

val MaterialTheme.colors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun HabitMakerTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: Accent = Accent.ZINC, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = (if (dark) Dark else Light).withAccent(accent)
    val scheme = if (dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.background, onBackground = c.foreground,
        surface = c.surface, onSurface = c.foreground, surfaceVariant = c.sunken, onSurfaceVariant = c.muted,
        outline = c.border, outlineVariant = c.border, error = c.danger, secondaryContainer = c.sunken,
        onSecondaryContainer = c.foreground, surfaceContainer = c.subtle, surfaceContainerHigh = c.subtle,
        surfaceContainerLow = c.background, surfaceContainerHighest = c.sunken,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.background, onBackground = c.foreground,
        surface = c.surface, onSurface = c.foreground, surfaceVariant = c.sunken, onSurfaceVariant = c.muted,
        outline = c.border, outlineVariant = c.border, error = c.danger, secondaryContainer = c.sunken,
        onSecondaryContainer = c.foreground, surfaceContainer = c.subtle, surfaceContainerHigh = Color.White,
        surfaceContainerLow = c.background, surfaceContainerHighest = c.sunken,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
