package com.aravind.habitmaker.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.core.focus.FocusRun
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.ui.components.AppButton

/** High-contrast daily focus anchor; its ring always represents real completed focus time. */
@Composable
internal fun HomeFocusHero(focusMin: Int, goalMin: Int, run: FocusRun?, now: Long, sessionMin: Int, onFocus: () -> Unit) {
    val mint = Color(0xFF91F4BC)
    val progress = (focusMin.toFloat() / goalMin.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF123F34), Color(0xFF082B27))))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(mint.copy(alpha = 0.035f), size.width * 0.48f, Offset(size.width * 0.95f, 0f))
            drawCircle(mint.copy(alpha = 0.07f), size.width * 0.34f, Offset(size.width * 0.95f, 0f), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("TODAY'S FOCUS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = mint)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (run == null) formatMinutes(focusMin) else formatTimer(run.remainingMs(now) / 1000),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (run != null) "${if (run.paused) "Paused" else if (run.isBreak) "Break" else "Focusing"} · ${run.label}" else "of ${formatMinutes(goalMin)} daily goal",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFC6DDD5))
                }
                Box(Modifier.size(82.dp).semantics { contentDescription = "${(progress * 100).toInt()} percent of daily focus goal complete" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        val inset = stroke / 2
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(Color.White.copy(alpha = 0.12f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                        drawArc(mint, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("of goal", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC6DDD5))
                    }
                }
            }
            AppButton(if (run == null) "Start focus · $sessionMin min" else "Return to ${if (run.isBreak) "break" else "focus"}", onFocus, icon = Icons.Filled.PlayArrow)
        }
    }
}
