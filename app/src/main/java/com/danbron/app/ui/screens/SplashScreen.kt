package com.danbron.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.BronMood
import com.danbron.app.ui.components.BronMascot
import com.danbron.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val tagAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(800))
    }
    LaunchedEffect(Unit) {
        delay(300)
        tagAlpha.animateTo(1f, animationSpec = tween(800))
    }
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x14E8B84B), Color.Transparent),
                    radius = 600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BronMascot(
                mood = BronMood.HAPPY,
                size = 178.dp,
                animated = true
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "danbron",
                style = DanbronType.displayLarge,
                color = Gold,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "TU VIDA, OPTIMIZADA",
                style = DanbronType.labelSmall.copy(fontSize = 14.sp, letterSpacing = 2.sp),
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(tagAlpha.value)
            )
        }
    }
}
