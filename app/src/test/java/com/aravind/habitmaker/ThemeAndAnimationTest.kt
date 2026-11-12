package com.aravind.habitmaker

import androidx.compose.ui.graphics.Color
import com.aravind.habitmaker.ui.theme.Accent
import com.aravind.habitmaker.ui.theme.Amber
import com.aravind.habitmaker.ui.theme.Emerald
import com.aravind.habitmaker.ui.theme.Rose
import com.aravind.habitmaker.ui.theme.SageSoft
import com.aravind.habitmaker.ui.theme.Violet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndAnimationTest {

    @Test
    fun testSereneFocusColorTokens() {
        // Serene Focus primary emerald token
        assertEquals(Color(0xFF10B981), Emerald)
        // Block rose token
        assertEquals(Color(0xFFF43F5E), Rose)
        // Streak amber token
        assertEquals(Color(0xFFF59E0B), Amber)
        // Deep focus indigo token
        assertEquals(Color(0xFF818CF8), Violet)
        // Sage soft secondary token
        assertEquals(Color(0xFFA7F3D0), SageSoft)
    }

    @Test
    fun testAccentPaletteDefinitions() {
        // Every accent enum entry must have valid light/dark gradient colors
        Accent.values().forEach { accent ->
            assertNotNull(accent.label)
            assertTrue(accent.light.value != 0UL)
            assertTrue(accent.dark.value != 0UL)
        }
    }

    @Test
    fun testZincDefaultsToSereneEmerald() {
        val zinc = Accent.ZINC
        assertEquals("Serene Emerald", zinc.label)
        assertEquals(Color(0xFF10B981), zinc.dark)
    }
}

