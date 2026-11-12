package com.aravind.habitmaker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tactile spring press feedback for buttons, tiles, and cards.
 * Compresses slightly (0.96x) on touch and springs back with natural damping.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.98f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncy_scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Ambient breathing glow pulse for the focus timer ring and active status shields.
 */
@Composable
fun rememberBreathingGlow(
    minAlpha: Float = 0.4f,
    maxAlpha: Float = 0.9f,
    durationMs: Int = 2400,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe_alpha",
    )
    return alpha
}

/**
 * Animated numeric display that slides digits vertically when numbers increment or change.
 */
@Composable
fun AnimatedNumber(
    targetNumber: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = targetNumber,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(220)) { height -> height / 2 } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically(animationSpec = tween(220)) { height -> -height / 2 } + fadeOut(tween(220)))
        },
        label = "animated_number",
        modifier = modifier,
    ) { num ->
        content(num)
    }
}

private data class SparkParticle(
    val id: Long,
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val color: Color,
)

/**
 * Clean, disciplined geometric micro-sparks on habit completion.
 * Avoids carnival confetti in favor of precise, elegant particle emission matching Serene Focus.
 */
@Composable
fun GeometricSparkBurst(
    triggerKey: Any?,
    color: Color,
    modifier: Modifier = Modifier,
    particleCount: Int = 12,
    radius: Dp = 32.dp,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "spark_progress",
    )

    val particles = remember(triggerKey) {
        if (triggerKey == null) emptyList()
        else {
            val list = mutableListOf<SparkParticle>()
            val rand = Random(triggerKey.hashCode().toLong())
            for (i in 0 until particleCount) {
                val baseAngle = (i.toFloat() / particleCount) * (2 * Math.PI.toFloat())
                val angleJitter = (rand.nextFloat() - 0.5f) * 0.35f
                val distance = radius.value * (0.8f + rand.nextFloat() * 0.5f)
                val dotSize = 2f + rand.nextFloat() * 2.5f
                list.add(
                    SparkParticle(
                        id = i.toLong(),
                        angle = baseAngle + angleJitter,
                        distance = distance,
                        radius = dotSize,
                        color = if (rand.nextBoolean()) color else color.copy(alpha = 0.7f),
                    )
                )
            }
            list
        }
    }

    LaunchedEffect(triggerKey) {
        if (triggerKey != null) {
            progress = 0f
            progress = 1f
        }
    }

    if (animatedProgress in 0.01f..0.99f && particles.isNotEmpty()) {
        Canvas(modifier = modifier) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val alpha = (1f - animatedProgress).coerceIn(0f, 1f)
            val currentDistanceFactor = animatedProgress

            particles.forEach { p ->
                val x = center.x + cos(p.angle) * p.distance * currentDistanceFactor * density
                val y = center.y + sin(p.angle) * p.distance * currentDistanceFactor * density
                drawCircle(
                    color = p.color.copy(alpha = alpha * p.color.alpha),
                    radius = p.radius * (1f - animatedProgress * 0.3f) * density,
                    center = Offset(x, y),
                )
            }
        }
    }
}

