package com.aravind.habitmaker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.theme.colors

/** One subtle pulse for the whole placeholder, with no per-frame recomposition. */
@Composable
fun SkeletonLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Loading placeholder")
    val alpha by transition.animateFloat(0.45f, 0.85f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "Skeleton pulse")
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Loading content" }
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colors.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(Modifier.size(40.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(28.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonBlock(Modifier.weight(1f).height(96.dp)) }
        }
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.size(42.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colors.border))
}

@Composable
fun SkeletonScreen(title: String, onBack: (() -> Unit)? = null) {
    ScrollScreen(title, onBack = onBack) { SkeletonLoading() }
}
