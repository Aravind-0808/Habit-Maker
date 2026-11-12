package com.aravind.habitmaker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class ColorPair(val name: String, val background: Long, val components: Long)

internal val colorPairs = listOf(
    ColorPair("Midnight blue", 0xFF080D16, 0xFF7DD3FC),
    ColorPair("Black & mint", 0xFF090B0A, 0xFF6EE7B7),
    ColorPair("Soft lavender", 0xFFF6F3FC, 0xFF6D28D9),
    ColorPair("Warm forest", 0xFFF6F5EF, 0xFF166534),
    ColorPair("Rose dusk", 0xFF1B1018, 0xFFFDA4AF),
    ColorPair("Clean blue", 0xFFF7FAFF, 0xFF1D4ED8),
)

internal fun suggestedComponentColors(background: Color): List<Long> {
    val candidates = listOf(0xFF7DD3FCL, 0xFF6EE7B7L, 0xFFC4B5FDL, 0xFF1D4ED8L, 0xFF6D28D9L, 0xFF166534L, 0xFF9F1239L, 0xFFFFFFFFL, 0xFF000000L)
    val light = background.luminance()
    return candidates.filter { raw ->
        val other = Color(raw).luminance()
        (maxOf(light, other) + 0.05f) / (minOf(light, other) + 0.05f) >= 4.5f
    }.take(3)
}
