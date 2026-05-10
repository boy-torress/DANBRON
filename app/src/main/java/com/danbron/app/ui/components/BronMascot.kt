package com.danbron.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danbron.app.data.models.BronMood
import com.danbron.app.ui.theme.Gold
import kotlin.math.sin

@Composable
fun BronMascot(
    mood: BronMood = BronMood.NEUTRAL,
    size: Dp = 100.dp,
    animated: Boolean = true
) {
    // Breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val breath by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(0.5f)
    }

    // Blink animation
    val blink by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4000
                    1f at 0
                    1f at 3800
                    0.1f at 3900
                    1f at 4000
                }
            ),
            label = "blink"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(1f)
    }

    // Ear twitch animation
    val earTwitch by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 5000
                    0f at 0
                    0f at 4500
                    10f at 4600
                    -5f at 4700
                    0f at 4800
                }
            ),
            label = "ear"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(0f)
    }

    val bounceOffset = if (animated) sin(breath * Math.PI).toFloat() * 4f else 0f

    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val center = Offset(s / 2, s / 2)
        val foxColor = Color(0xFFE8B84B) // Gold
        val foxDark = Color(0xFFC97B2E)
        val eyeColor = Color(0xFF0A0A0F)

        translate(top = bounceOffset) {
            // Draw Ears
            drawEar(
                center = center, size = s,
                isLeft = true, color = foxColor, innerColor = foxDark,
                twitch = earTwitch
            )
            drawEar(
                center = center, size = s,
                isLeft = false, color = foxColor, innerColor = foxDark,
                twitch = -earTwitch
            )

            // Draw Head Shape (soft polygon/circle)
            val headPath = Path().apply {
                val top = center.y - s * 0.25f
                val bottom = center.y + s * 0.35f
                val left = center.x - s * 0.4f
                val right = center.x + s * 0.4f
                
                moveTo(center.x, bottom) // chin
                quadraticBezierTo(left, bottom - s * 0.1f, left, center.y) // left cheek
                quadraticBezierTo(left + s * 0.1f, top, center.x, top) // left top
                quadraticBezierTo(right - s * 0.1f, top, right, center.y) // right top
                quadraticBezierTo(right, bottom - s * 0.1f, center.x, bottom) // right cheek
            }
            
            drawPath(
                path = headPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD97D), foxColor, foxDark),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                )
            )

            // Draw White Muzzle
            val muzzlePath = Path().apply {
                moveTo(center.x, center.y + s * 0.35f) // chin
                quadraticBezierTo(center.x - s * 0.25f, center.y + s * 0.1f, center.x, center.y - s * 0.05f) // left curve
                quadraticBezierTo(center.x + s * 0.25f, center.y + s * 0.1f, center.x, center.y + s * 0.35f) // right curve
            }
            drawPath(path = muzzlePath, color = Color(0xFFFFF6E5))

            // Nose
            drawCircle(
                color = eyeColor,
                radius = s * 0.05f,
                center = Offset(center.x, center.y + s * 0.15f)
            )

            // Eyes
            val eyeRadius = s * 0.06f
            val eyeY = center.y - s * 0.05f
            val leftEyeX = center.x - s * 0.18f
            val rightEyeX = center.x + s * 0.18f
            
            val actualBlink = if (mood == BronMood.SLEEPY) 0.1f else blink

            if (mood == BronMood.HAPPY || mood == BronMood.CELEBRATING) {
                // Happy eyes (curved lines)
                drawArc(
                    color = eyeColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius),
                    size = Size(eyeRadius * 2, eyeRadius * 2), style = Stroke(width = s * 0.03f)
                )
                drawArc(
                    color = eyeColor, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius),
                    size = Size(eyeRadius * 2, eyeRadius * 2), style = Stroke(width = s * 0.03f)
                )
            } else if (mood == BronMood.THINKING) {
                // Thinking eyes (one squint, one open)
                drawCircle(color = eyeColor, radius = eyeRadius * 0.8f, center = Offset(leftEyeX, eyeY))
                drawLine(
                    color = eyeColor,
                    start = Offset(rightEyeX - eyeRadius, eyeY),
                    end = Offset(rightEyeX + eyeRadius, eyeY),
                    strokeWidth = s * 0.03f
                )
            } else {
                // Normal eyes
                drawOval(
                    color = eyeColor,
                    topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * actualBlink),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * actualBlink)
                )
                drawOval(
                    color = eyeColor,
                    topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * actualBlink),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * actualBlink)
                )
            }

            // Optional: sleeping Zzz
            if (mood == BronMood.SLEEPY) {
                drawPath(
                    path = Path().apply {
                        moveTo(center.x + s * 0.2f, center.y - s * 0.3f)
                        lineTo(center.x + s * 0.3f, center.y - s * 0.3f)
                        lineTo(center.x + s * 0.2f, center.y - s * 0.2f)
                        lineTo(center.x + s * 0.3f, center.y - s * 0.2f)
                    },
                    color = Color.White,
                    style = Stroke(width = s * 0.02f)
                )
            }
            
            // Celebration effects
            if (mood == BronMood.CELEBRATING) {
                drawCircle(color = Color(0xFF2DD4BF), radius = s * 0.04f, center = Offset(center.x - s * 0.4f, center.y - s * 0.2f))
                drawCircle(color = Color(0xFFFF5C5C), radius = s * 0.03f, center = Offset(center.x + s * 0.4f, center.y - s * 0.1f))
            }
        }
    }
}

private fun DrawScope.drawEar(
    center: Offset, size: Float, isLeft: Boolean, color: Color, innerColor: Color, twitch: Float
) {
    val dir = if (isLeft) -1f else 1f
    
    // Save rotation state for twitch
    translate(center.x + size * 0.25f * dir, center.y - size * 0.2f) {
        val earPath = Path().apply {
            moveTo(0f, 0f)
            quadraticBezierTo(size * 0.1f * dir, -size * 0.3f, size * 0.2f * dir, -size * 0.35f) // outer edge
            quadraticBezierTo(size * 0.05f * dir, -size * 0.2f, -size * 0.15f * dir, -size * 0.05f) // inner edge
            close()
        }
        
        drawPath(path = earPath, color = color)
        
        // Inner ear
        val innerEarPath = Path().apply {
            moveTo(0f, -size * 0.05f)
            quadraticBezierTo(size * 0.05f * dir, -size * 0.2f, size * 0.15f * dir, -size * 0.25f)
            quadraticBezierTo(size * 0.02f * dir, -size * 0.15f, -size * 0.05f * dir, -size * 0.05f)
            close()
        }
        drawPath(path = innerEarPath, color = innerColor)
    }
}


