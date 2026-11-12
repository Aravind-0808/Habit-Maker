package com.aravind.habitmaker.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.theme.colors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Equal-spaced daily observations. Day buttons expose every point to accessibility services. */
@Composable
internal fun MomentumGraph(values: List<Float>, days: List<LocalDate>, selectedDay: Int, percent: Boolean, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colors
    val maximum = if (percent) 100f else maxOf(30f, values.maxOrNull() ?: 0f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LAST 7 DAYS", style = MaterialTheme.typography.labelSmall, color = c.muted)
            Text(if (percent) "0–100%" else "0–${maximum.toInt()} min", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        Canvas(
            Modifier.fillMaxWidth().height(if (values.all { it == 0f }) 64.dp else 132.dp)
                .semantics { contentDescription = "${if (percent) "Habit completion" else "Focus time"} trend. Select a date below for its value." }
                .pointerInput(values.size) {
                    detectTapGestures { point ->
                        val edge = 8.dp.toPx()
                        val usable = (size.width - edge * 2).coerceAtLeast(1f)
                        onSelect(((point.x - edge) / usable * values.lastIndex).roundToInt().coerceIn(0, values.lastIndex))
                    }
                },
        ) {
            val edge = 8.dp.toPx()
            val bottom = size.height - edge
            val top = edge
            val chartHeight = bottom - top
            val step = (size.width - edge * 2) / (values.size - 1).coerceAtLeast(1)
            repeat(3) { line ->
                val y = top + chartHeight * line / 2
                drawLine(c.border.copy(alpha = 0.55f), Offset(edge, y), Offset(size.width - edge, y), 1.dp.toPx())
            }
            val points = values.mapIndexed { i, value -> Offset(edge + i * step, bottom - value.coerceIn(0f, maximum) / maximum * chartHeight) }
            val curve = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.zipWithNext().forEach { (a, b) ->
                    val middle = (a.x + b.x) / 2
                    cubicTo(middle, a.y, middle, b.y, b.x, b.y)
                }
            }
            val fill = Path().apply {
                addPath(curve)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.22f), c.primary.copy(alpha = 0.01f)), top, bottom))
            drawPath(curve, c.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val selectedPoint = points[selectedDay]
            drawLine(c.primary.copy(alpha = 0.25f), Offset(selectedPoint.x, top), Offset(selectedPoint.x, bottom), 1.dp.toPx())
            points.forEachIndexed { index, point ->
                if (index == selectedDay) drawCircle(c.primary.copy(alpha = 0.15f), 8.dp.toPx(), point)
                drawCircle(if (index == selectedDay) c.primary else c.surface, if (index == selectedDay) 4.dp.toPx() else 3.dp.toPx(), point)
                if (index != selectedDay) drawCircle(c.primary, 3.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { index, day ->
                val isSelected = index == selectedDay
                val valueLabel = if (percent) "${values[index].toInt()} percent" else "${values[index].toInt()} minutes"
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) c.primarySoft else c.surface)
                        .semantics {
                            selected = isSelected
                            contentDescription = "${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))}, $valueLabel"
                        }
                        .clickable { onSelect(index) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = if (isSelected) c.primary else c.muted)
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isSelected) c.primary else c.foreground)
                }
            }
        }
    }
}
