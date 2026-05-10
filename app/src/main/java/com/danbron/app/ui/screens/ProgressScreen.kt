package com.danbron.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.ui.components.*
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import java.util.*

@Composable
fun ProgressScreen(vm: MainViewModel) {
    val user by vm.user.collectAsState()
    val habits by vm.habits.collectAsState()
    val u = user ?: return
    val free = u.freeFlow
    val monthsLeft = u.monthsToDebtFree
    val paidOff = u.debt * 0.02
    val pct = if (u.debt > 0) (paidOff / u.debt).toFloat().coerceIn(0f, 1f) else 1f
    val months = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
    val days = listOf("L","M","X","J","V","S","D")
    val todayIdx = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    // Animated progress
    val animPct = animatedProgress(pct)

    Column(
        Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        // Header
        StaggeredEntrance(0) {
            Box(Modifier.padding(start = 24.dp, top = 52.dp)) {
                Text("Tu Progreso 📊", style = DanbronType.headlineMedium, color = TextPrimary)
            }
        }

        if (u.debt > 0) {
            // Debt card
            StaggeredEntrance(1) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                        .fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(BgSecondary).border(1.dp, Border, RoundedCornerShape(22.dp))
                        .padding(22.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("DEUDA TOTAL", style = DanbronType.labelSmall, color = TextTertiary)
                        Text("+ Pago", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                            color = Gold, modifier = Modifier.clickable { vm.toast("Agregar pago de deuda") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("$${String.format("%,.0f", u.debt)}", style = DanbronType.displayLarge.copy(fontSize = 38.sp), color = Red)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Meta: $0 en $monthsLeft meses · pagando $${String.format("%,.0f", free * 0.6)}/mes",
                        style = DanbronType.bodySmall, color = TextTertiary
                    )
                    Spacer(Modifier.height(16.dp))
                    // Animated progress bar
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Border)) {
                        Box(Modifier.fillMaxWidth(animPct).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Teal))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pagado: $${String.format("%,.0f", paidOff)}", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                        Text("${(animPct * 100).toInt()}%", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("PROGRESO ANUAL", style = DanbronType.labelSmall, color = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        months.forEachIndexed { i, m ->
                            val targetH = when { i < currentMonth -> 1f; i == currentMonth -> 0.15f; else -> 0f }
                            val animH = animatedProgress(targetH, durationMs = 800 + i * 100)
                            val color = if (i == currentMonth) Teal else Gold
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(BgTertiary),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(Modifier.fillMaxWidth().fillMaxHeight(animH).clip(RoundedCornerShape(6.dp))
                                    .background(color.copy(alpha = if (i < currentMonth) 0.8f else if (i == currentMonth) 1f else 0.3f)))
                                Text(m, fontSize = 8.sp, color = TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                    }
                }
            }
        } else {
            StaggeredEntrance(1) {
                Box(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgSecondary)
                    .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Column {
                        Text("RACHA SIN DEUDAS", style = DanbronType.labelSmall, color = TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("∞", style = DanbronType.displayLarge.copy(fontSize = 38.sp), color = Teal)
                        Text("¡Sin deudas! Sigue así 🎯", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                    }
                }
            }
        }

        // Habits
        Column(Modifier.padding(horizontal = 20.dp)) {
            StaggeredEntrance(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hábitos activos", style = DanbronType.titleMedium, color = TextPrimary)
                    Text("+ Añadir", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                        color = Gold, modifier = Modifier.clickable { vm.addHabit("Nuevo hábito") })
                }
            }
            Spacer(Modifier.height(14.dp))

            habits.forEachIndexed { index, habit ->
                val isDoneToday = vm.isHabitDoneToday(habit.id)

                StaggeredEntrance(3 + index) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp)).background(BgSecondary)
                            .border(1.dp, if (isDoneToday) Gold.copy(alpha = 0.3f) else Border, RoundedCornerShape(12.dp))
                            .clickable { vm.toggleHabitToday(habit.id) }
                            .padding(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Checkmark for today
                                CheckmarkBounce(checked = isDoneToday) {
                                    Box(
                                        Modifier.size(18.dp).clip(
                                            RoundedCornerShape(5.dp)
                                        )
                                            .background(if (isDoneToday) Gold else Color.Transparent)
                                            .border(
                                                2.dp,
                                                if (isDoneToday) Gold else Border,
                                                RoundedCornerShape(5.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDoneToday) Text(
                                            "✓",
                                            fontSize = 10.sp,
                                            color = Color(0xFF0A0A0F)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(habit.name, style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500),
                                    color = if (isDoneToday) TextTertiary else TextPrimary)
                            }
                            // Streak with fire animation
                            if (habit.streak > 0) {
                                val fireTransition = rememberInfiniteTransition(label = "fire_${habit.id}")
                                val fireScale by fireTransition.animateFloat(
                                    initialValue = 1f, targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                                    label = "fire_scale_${habit.id}"
                                )
                                Text("🔥", modifier = Modifier.scale(fireScale), fontSize = 14.sp)
                            }
                            Text(
                                if (habit.streak > 0) "${habit.streak} días" else "Empieza hoy",
                                style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W600),
                                color = if (habit.streak > 0) Gold else TextTertiary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            days.forEachIndexed { i, d ->
                                val done = habit.week.getOrNull(i) ?: false
                                val isToday = i == todayIdx
                                Box(
                                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (done) Gold else BgTertiary)
                                        .border(1.dp, if (done) Gold else if (isToday) Gold.copy(alpha = 0.5f) else Border, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(d, fontSize = 10.sp, fontWeight = if (done) FontWeight.W700 else FontWeight.W400,
                                        color = if (done) Color(0xFF0A0A0F) else if (isToday) Gold else TextTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
