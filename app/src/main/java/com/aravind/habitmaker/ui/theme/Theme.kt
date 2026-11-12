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

/** Serene Focus design tokens: deep forest-slate neutral base, primary emerald, indigo focus accents. */
@Immutable
data class AppColors(
    val background: Color,
    val subtle: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val sunken: Color,
    val foreground: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
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
val Violet = Color(0xFF818CF8)
val Blue = Color(0xFF38BDF8)
val SageSoft = Color(0xFFA7F3D0)

private val Light = AppColors(
    background = Color(0xFFF6F8F6), subtle = Color(0xFFF0F4F1), surface = Color(0xFFFFFFFF), surfaceElevated = Color(0xFFF0F4F1), sunken = Color(0xFFE5ECE7),
    foreground = Color(0xFF0E1511), muted = Color(0xFF526157), faint = Color(0xFF718277),
    border = Color(0xFFD8E1DA), borderStrong = Color(0xFFB5C3B8),
    primary = Color(0xFF059669), primarySoft = Color(0x1F10B981), onPrimary = Color.White,
    secondary = Color(0xFF4F46E5), onSecondary = Color.White,
    success = Color(0xFF059669), successSoft = Color(0x1F059669),
    warning = Color(0xFFD97706), warningSoft = Color(0x1FD97706),
    danger = Color(0xFFE11D48), dangerSoft = Color(0x1FE11D48),
    info = Color(0xFF4F46E5), infoSoft = Color(0x1F4F46E5),
    heroStart = Color(0xFF0E1511), heroEnd = Color(0xFF1B2820), onHero = Color.White,
    isDark = false,
)

private val Dark = AppColors(
    background = Color(0xFF0B1313), subtle = Color(0xFF161D19), surface = Color(0xFF111C1C), surfaceElevated = Color(0xFF1A2727), sunken = Color(0xFF09100C),
    foreground = Color(0xFFDDE4DD), muted = Color(0xFFBBCABF), faint = Color(0xFF86948A),
    border = Color(0xFF27332C), borderStrong = Color(0xFF3C4A42),
    primary = Color(0xFF10B981), primarySoft = Color(0x2610B981), onPrimary = Color(0xFF003824),
    secondary = Color(0xFF818CF8), onSecondary = Color(0xFF0F172A),
    success = Color(0xFF10B981), successSoft = Color(0x2610B981),
    warning = Color(0xFFF59E0B), warningSoft = Color(0x26F59E0B),
    danger = Color(0xFFF43F5E), dangerSoft = Color(0x26F43F5E),
    info = Color(0xFF818CF8), infoSoft = Color(0x26818CF8),
    heroStart = Color(0xFF1A2727), heroEnd = Color(0xFF111C1C), onHero = Color(0xFFDDE4DD),
    isDark = true,
)

/** Accent colour themes. ZINC defaults to Serene Focus Emerald. */
enum class Accent(val label: String, val light: Color, val lightEnd: Color, val dark: Color, val darkEnd: Color) {
    ZINC("Serene Emerald", Color(0xFF059669), Color(0xFF047857), Color(0xFF10B981), Color(0xFF4EDEA3)),
    SLATE("Slate Classic", Color(0xFF18181B), Color(0xFF3F3F46), Color(0xFFFAFAFA), Color(0xFFD4D4D8)),
    EMERALD("Emerald", Color(0xFF059669), Color(0xFF047857), Color(0xFF10B981), Color(0xFF4EDEA3)),
    BLUE("Ocean", Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF38BDF8), Color(0xFF0284C7)),
    VIOLET("Indigo Focus", Color(0xFF4F46E5), Color(0xFF4338CA), Color(0xFF818CF8), Color(0xFF6366F1)),
    ROSE("Rose Shield", Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFFFB7185), Color(0xFFF43F5E)),
    AMBER("Streak Amber", Color(0xFFD97706), Color(0xFFB45309), Color(0xFFFBBF24), Color(0xFFF59E0B)),
}

private fun AppColors.withAccent(a: Accent): AppColors = if (a == Accent.ZINC || a == Accent.EMERALD) this else if (isDark) copy(
    primary = a.dark, primarySoft = a.dark.copy(alpha = 0.15f), onPrimary = if (a == Accent.SLATE) Color(0xFF09090B) else Color(0xFF003824),
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
    extraLarge = RoundedCornerShape(24.dp),
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
