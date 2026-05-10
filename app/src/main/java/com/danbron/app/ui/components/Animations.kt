package com.danbron.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danbron.app.ui.theme.Gold
import com.danbron.app.ui.theme.Gold2
import com.danbron.app.ui.theme.Teal
import com.danbron.app.ui.theme.Red
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confetti burst — triggered when all daily tasks are completed.
 * Particles rain down with random colors, sizes, and speeds.
 */
@Composable
fun ConfettiEffect(trigger: Boolean, modifier: Modifier = Modifier) {
    if (!trigger) return

    val particles = remember {
        List(40) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -0.5f,
                speedX = Random.nextFloat() * 2f - 1f,
                speedY = Random.nextFloat() * 3f + 2f,
                size = Random.nextFloat() * 8f + 4f,
                color = listOf(Gold, Gold2, Teal, Color(0xFFFF6B6B), Color(0xFF845EF7), Color(0xFF22B8CF)).random(),
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    val progress by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "confetti_progress"
    )

    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        delay(4000)
        visible = false
    }

    if (visible) {
        Canvas(modifier.fillMaxSize()) {
            particles.forEach { p ->
                val x = (p.x + p.speedX * progress * 0.3f) * size.width
                val y = (p.y + p.speedY * progress * 0.4f) * size.height
                if (y < size.height) {
                    drawCircle(
                        color = p.color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                        radius = p.size,
                        center = Offset(x % size.width, y)
                    )
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float, val y: Float,
    val speedX: Float, val speedY: Float,
    val size: Float, val color: Color,
    val rotation: Float
)

/**
 * Pulse ring effect — gold ring that expands and fades.
 * Used when completing a task.
 */
@Composable
fun PulseEffect(trigger: Boolean, modifier: Modifier = Modifier) {
    if (!trigger) return

    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        scale.animateTo(2.5f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(trigger) {
        alpha.animateTo(0f, animationSpec = tween(600))
    }

    Box(
        modifier.size(40.dp).scale(scale.value).alpha(alpha.value)
            .clip(CircleShape)
            .background(Gold.copy(alpha = 0.3f))
    )
}

/**
 * Animated checkmark — scales up with a bounce when a task is completed.
 */
@Composable
fun CheckmarkBounce(
    checked: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "checkmark_bounce"
    )
    Box(Modifier.scale(scale)) { content() }
}

/**
 * Streak fire — pulsing flame emoji when streak > 0
 */
@Composable
fun StreakFire(streak: Int, modifier: Modifier = Modifier) {
    if (streak <= 0) return

    val infiniteTransition = rememberInfiniteTransition(label = "fire")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fire_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fire_alpha"
    )

    Box(modifier.scale(scale).alpha(alpha)) {
        // Content provided externally
    }
}

/**
 * Count up animation — numbers that animate from 0 to target.
 */
@Composable
fun animatedCount(target: Float, durationMs: Int = 1000): Float {
    var value by remember { mutableFloatStateOf(0f) }
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(target) {
        animatable.animateTo(target, animationSpec = tween(durationMs, easing = FastOutSlowInEasing))
    }

    return animatable.value
}

/**
 * Staggered entrance — items appear one by one with a delay.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    delayPerItem: Int = 80,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        delay(index * delayPerItem.toLong())
        alpha.animateTo(1f, animationSpec = tween(300))
    }
    LaunchedEffect(Unit) {
        delay(index * delayPerItem.toLong())
        offsetY.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier.alpha(alpha.value).offset(y = offsetY.value.dp)
    ) { content() }
}

/**
 * Breathing glow — subtle pulsing border glow effect for the Bron card.
 */
@Composable
fun breathingAlpha(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )
    return alpha
}

/**
 * Animated progress bar fill — used for debt progress.
 */
@Composable
fun animatedProgress(target: Float, durationMs: Int = 1200): Float {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(target, animationSpec = tween(durationMs, easing = FastOutSlowInEasing))
    }
    return animatable.value
}
