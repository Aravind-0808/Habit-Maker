package com.aravind.habitmaker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aravind.habitmaker.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemeColorTest {
    @Test fun acceptsOpaqueHexOnly() {
        assertEquals(0xFF2563EBL, parseHexColor(" #2563eb "))
        assertEquals(0xFF000000L, parseHexColor("000000"))
        listOf("#123", "#FF123456", "blue", "GG0000", "").forEach { assertNull(parseHexColor(it)) }
    }
    @Test fun backgroundAndComponentColorsAreIndependent() {
        val a = resolveAppColors(false, Accent.ZINC, primaryColor = 0xFF000000, secondaryColor = 0xFF7DD3FC)
        val b = resolveAppColors(false, Accent.ZINC, primaryColor = 0xFF000000, secondaryColor = 0xFFFDA4AF)
        assertEquals(Color.Black, a.background)
        assertEquals(a.background, b.background)
        assertNotEquals(a.primary, b.primary)
        assertNotEquals(a.surface, b.surface)
    }
    @Test fun primaryIsAlwaysTheExactBackground() {
        listOf(false, true).forEach { dark ->
            listOf(0xFF000000L, 0xFFFFFFFFL, 0xFF234567L).forEach { background ->
                val c = resolveAppColors(dark, Accent.ZINC, primaryColor = background)
                assertEquals(Color(background), c.background)
            }
        }
    }
    @Test fun componentColorsAdjustForReadability() {
        listOf(0xFF000000L, 0xFFFFFFFFL).forEach { background ->
            listOf(0xFF000000L, 0xFFFFFFFFL, 0xFFFFFF00L).forEach { component ->
                val c = resolveAppColors(false, Accent.ZINC, primaryColor = background, secondaryColor = component)
                assertTrue(contrast(c.primary, c.surface) >= 4.5f)
                assertTrue(contrast(c.onPrimary, c.primary) >= 4.5f)
            }
        }
    }
    @Test fun backgroundsSelectReadableTextAndSystemBarMode() {
        val black = resolveAppColors(false, Accent.ZINC, primaryColor = 0xFF000000)
        val white = resolveAppColors(true, Accent.ZINC, primaryColor = 0xFFFFFFFF)
        assertTrue(black.isDark)
        assertEquals(Color.White, black.foreground)
        assertFalse(white.isDark)
        assertEquals(Color.Black, white.foreground)
    }
    @Test fun recommendationsMatchChosenBackground() {
        listOf(Color.Black, Color.White, Color(0xFF234567), Color(0xFF888888)).forEach { background ->
            val matches = suggestedComponentColors(background)
            assertTrue(matches.isNotEmpty())
            matches.forEach { assertTrue(contrast(background, Color(it)) >= 4.5f) }
        }
    }
    @Test fun readyMadePairsHaveReadableComponents() {
        colorPairs.forEach { pair -> assertTrue(contrast(Color(pair.background), Color(pair.components)) >= 4.5f) }
    }
    @Test fun statusIndicatorsUseChosenComponentsInsteadOfDefaultGreenAndAmber() {
        val c = resolveAppColors(false, Accent.ZINC, primaryColor = 0xFF080D16, secondaryColor = 0xFF7DD3FC)
        assertEquals(c.primary, c.success)
        assertEquals(c.primary, c.warning)
        assertEquals(c.primary, c.danger)
        assertEquals(c.primary, c.info)
        assertEquals(c.primary.copy(alpha = 0.12f), c.successSoft)
    }
    @Test fun foregroundContrastChoosesBlackOrWhite() {
        assertEquals(Color.White, onAccent(Color.Black))
        assertEquals(Color.Black, onAccent(Color.White))
    }
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x,y) + 0.05f) / (minOf(x,y) + 0.05f)
    }
}
