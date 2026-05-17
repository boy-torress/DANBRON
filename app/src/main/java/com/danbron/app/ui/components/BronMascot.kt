package com.danbron.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danbron.app.R
import com.danbron.app.data.models.BronMood
import kotlin.math.sin

@Composable
fun BronMascot(
    mood: BronMood = BronMood.NEUTRAL,
    size: Dp = 100.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bron_mascot_anim")
    val breath by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bron_breath"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(0.5f)
    }

    val bounce = if (animated) sin(breath * Math.PI).toFloat() * 3f else 0f
    val scale = if (animated && mood == BronMood.CELEBRATING) 1f + sin(breath * Math.PI).toFloat() * 0.04f else 1f
    val image = if (size <= 64.dp) R.drawable.bron_avatar else R.drawable.bron_mascot

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = "Bron",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bounce
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}
