package com.danbron.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.Task
import com.danbron.app.ui.theme.*

@Composable
fun BronCard(message: String, onClick: () -> Unit) {
    val breathAlpha = breathingAlpha()

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(CardGradientStart, CardGradientEnd)))
            .border(1.dp, Gold.copy(alpha = breathAlpha), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Gold, Color(0xFFC97B2E)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", style = DanbronType.titleSmall, color = Color(0xFF0A0A0F))
                    // Green dot
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(10.dp)
                            .clip(CircleShape).background(Green)
                            .border(2.dp, BgSecondary, CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bron", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W600), color = Gold)
                    Text("Tu asistente personal · en línea", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(message, style = DanbronType.bodyLarge.copy(fontWeight = FontWeight.W300), color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            Text("Hablar con Bron →", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500), color = Teal)
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit) {
    val tagInfo = when (task.tag) {
        "money" -> Triple("💰 Dinero", Color(0x26E8B84B), Gold)
        "habit" -> Triple("🔥 Hábito", Color(0x262DD4BF), Teal)
        "work" -> Triple("⚡ Trabajo", Color(0x1AFF5C5C), Red)
        else -> Triple("😴 Descanso", Color(0x12FFFFFF), TextSecondary)
    }

    // Bounce animation on check
    var bouncing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bouncing) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "task_bounce",
        finishedListener = { bouncing = false }
    )

    Row(
        modifier = Modifier.fillMaxWidth().scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSecondary)
            .border(1.dp, if (task.done) Gold.copy(alpha = 0.3f) else Border, RoundedCornerShape(12.dp))
            .clickable { bouncing = true; onToggle() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        CheckmarkBounce(checked = task.done) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(if (task.done) Gold else Color.Transparent)
                    .border(2.dp, if (task.done) Gold else Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (task.done) Text("✓", fontSize = 11.sp, color = Color(0xFF0A0A0F))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.text,
                style = DanbronType.bodyMedium,
                color = if (task.done) TextTertiary else TextPrimary,
                textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(tagInfo.second)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(tagInfo.first, fontSize = 10.sp, fontWeight = FontWeight.W600, color = tagInfo.third, letterSpacing = 0.5.sp)
                }
                Text(task.time, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, sub: String, progress: Float? = null, progressColor: Color = Gold) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(BgSecondary)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(label.uppercase(), style = DanbronType.labelSmall, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = DanbronType.statValue, color = valueColor)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            val animProg = animatedProgress(progress)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Border)) {
                Box(Modifier.fillMaxWidth(animProg).height(4.dp).clip(RoundedCornerShape(2.dp)).background(progressColor))
            }
        }
    }
}
