package com.aravind.habitmaker.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.theme.Accent
import com.aravind.habitmaker.ui.theme.colors
import com.aravind.habitmaker.ui.theme.onAccent
import com.aravind.habitmaker.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColorPicker(title: String, subtitle: String, preset: Accent, custom: Long?, onPreset: (Accent) -> Unit, onCustom: (Long) -> Unit, background: Boolean = false) {
    val c = MaterialTheme.colors
    var showCustom by rememberSaveable { mutableStateOf(false) }
    AppCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.foreground)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(14.dp))
        val currentColor = custom?.let { Color(it) }
            ?: if (background && preset == Accent.ZINC) c.background else if (!background && c.isDark) preset.dark else preset.light
        val options = buildList {
            if (background) {
                add(Triple("White", Color.White, { onCustom(0xFFFFFFFF) }))
                add(Triple("Black", Color.Black, { onCustom(0xFF000000) }))
            }
            Accent.entries.filter { it != Accent.ZINC }.forEach { accent ->
                val color = if (!background && c.isDark) accent.dark else accent.light
                val name = when (accent) { Accent.SLATE -> "Slate"; Accent.EMERALD -> "Green"; Accent.BLUE -> "Blue"; Accent.VIOLET -> "Purple"; Accent.ROSE -> "Rose"; Accent.AMBER -> "Amber"; else -> accent.label }
                add(Triple(name, color, { onPreset(accent) }))
            }
        }
        val isCustom = options.none { it.second.toArgb() == currentColor.toArgb() }
        val withCustom = options + Triple("Custom", currentColor, { showCustom = true })
        withCustom.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, color, onClick) ->
                    val isSelected = if (label == "Custom") isCustom else color.toArgb() == currentColor.toArgb()
                    ColorSwatch(label, color, isSelected, Modifier.weight(1f), onClick)
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("Selected · #${(currentColor.toArgb().toLong() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}",
            style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
    if (showCustom) {
        var hex by remember { mutableStateOf(custom?.let { "#${(it and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}" } ?: "#2563EB") }
        val parsed = parseHexColor(hex)
        AlertDialog(onDismissRequest = { showCustom = false }, containerColor = c.surface,
            title = { Text("Custom ${title.lowercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppTextField(hex, { hex = it }, "Hex color", placeholder = "#2563EB", error = if (parsed == null) "Enter six hex digits, for example #2563EB" else null)
                    if (parsed != null) Box(Modifier.size(56.dp).clip(CircleShape).background(Color(parsed)))
                }
            },
            confirmButton = { TextButton({ parsed?.let { onCustom(it); showCustom = false } }, enabled = parsed != null) { Text("Apply color") } },
            dismissButton = { TextButton({ showCustom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) c.primarySoft else c.surface)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.primary else c.border, MaterialTheme.shapes.medium)
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(42.dp)) {
            Box(Modifier.size(36.dp).align(Alignment.Center).clip(CircleShape).background(color).border(1.dp, c.borderStrong, CircleShape))
            if (isSelected) Box(Modifier.size(19.dp).align(Alignment.BottomEnd).clip(CircleShape).background(c.primary).border(2.dp, c.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Selected", tint = c.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = if (isSelected) c.primary else c.muted)
    }
}
