package com.aravind.habitmaker.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.theme.ColorPair
import com.aravind.habitmaker.ui.theme.colorPairs
import com.aravind.habitmaker.ui.theme.colors
import com.aravind.habitmaker.ui.theme.suggestedComponentColors

@Composable
internal fun ColorPairPicker(background: Long?, components: Long?, onPick: (ColorPair) -> Unit) {
    val c = MaterialTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Find your look", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.foreground)
        Text("Choose a complete palette, or personalize each color below.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        colorPairs.chunked(2).forEach { pairRow ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pairRow.forEach { pair ->
                    val isSelected = background == pair.background && components == pair.components
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                        .background(c.surface).border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.primary else c.border, MaterialTheme.shapes.large)
                        .semantics { selected = isSelected }.clickable(role = Role.RadioButton) { onPick(pair) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Color(pair.background)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(pair.components)))
                                Box(Modifier.fillMaxWidth(0.65f).height(5.dp).background(Color(pair.components).copy(alpha = 0.3f)))
                            }
                            Box(Modifier.fillMaxWidth(0.8f).height(5.dp).background(Color(pair.components).copy(alpha = 0.25f)))
                            Box(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).background(Color(pair.components)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pair.name, style = MaterialTheme.typography.labelMedium, color = c.foreground, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected palette", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SecondarySuggestions(selectedColor: Long?, onPick: (Long) -> Unit) {
    val c = MaterialTheme.colors
    AppCard {
        Text("Recommended for you", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.foreground)
        Spacer(Modifier.height(5.dp))
        Text("These shades work with your background.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestedComponentColors(c.background).forEach { color ->
                val isSelected = selectedColor == color
                val name = when (color) {
                    0xFF7DD3FCL -> "Sky blue"; 0xFF6EE7B7L -> "Mint"; 0xFFC4B5FDL -> "Lavender"
                    0xFF1D4ED8L -> "Ocean blue"; 0xFF6D28D9L -> "Violet"; 0xFF166534L -> "Forest"
                    0xFF9F1239L -> "Berry"; 0xFFFFFFFFL -> "White"; else -> "Black"
                }
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) c.primarySoft else c.surface)
                    .semantics { selected = isSelected }.clickable(role = Role.RadioButton) { onPick(color) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, c.border, CircleShape))
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = c.foreground, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected", tint = c.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
