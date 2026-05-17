package com.danbron.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.ui.components.*
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.util.*

@OptIn(ExperimentalComposeUiApi::class)
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
    var showAddHabit by remember { mutableStateOf(false) }
    var habitName by remember { mutableStateOf("") }

    // Animated progress
    val animPct = animatedProgress(pct)

    val tasks by vm.tasks.collectAsState()
    val notes by vm.notes.collectAsState()
    val doneCount = tasks.count { it.done }
    val habitsDoneToday = habits.count { vm.isHabitDoneToday(it.id) }
    val totalHabitsWeek = habits.sumOf { h -> h.week.count { it } }
    val weekConsistency = if (habits.isNotEmpty()) ((totalHabitsWeek.toFloat() / (habits.size * 7)) * 100).toInt().coerceIn(0, 100) else 0

    Column(
        Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        // Header
        StaggeredEntrance(0) {
            Box(Modifier.padding(start = 24.dp, top = 52.dp, end = 24.dp)) {
                Column {
                    Text("Tu progreso", style = DanbronType.headlineMedium, color = TextPrimary)
                    Text("Resumen de tu actividad y metas", style = DanbronType.bodySmall, color = TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick stats row
        StaggeredEntrance(0) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    StatCard("Tareas", "$doneCount/${tasks.size}", Gold, "completadas hoy",
                        progress = if (tasks.isNotEmpty()) doneCount.toFloat() / tasks.size else 0f,
                        progressColor = Gold)
                }
                Box(Modifier.weight(1f)) {
                    StatCard("Habitos", "$habitsDoneToday/${habits.size}", Teal, "hechos hoy",
                        progress = if (habits.isNotEmpty()) habitsDoneToday.toFloat() / habits.size else 0f,
                        progressColor = Teal)
                }
                Box(Modifier.weight(1f)) {
                    StatCard("Notas", "${notes.size}", Color(0xFF9B59B6), "guardadas")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Weekly consistency
        StaggeredEntrance(1) {
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)).background(BgSecondary)
                    .border(1.dp, Border, RoundedCornerShape(14.dp)).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Consistencia semanal", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W600), color = TextPrimary)
                    Text("$weekConsistency%", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W700),
                        color = when { weekConsistency >= 70 -> Teal; weekConsistency >= 40 -> Gold; else -> Red })
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(BgTertiary)) {
                    val animConsistency = animatedProgress(weekConsistency / 100f)
                    Box(Modifier.fillMaxWidth(animConsistency).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(when { weekConsistency >= 70 -> Teal; weekConsistency >= 40 -> Gold; else -> Red }))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when { weekConsistency >= 70 -> "Excelente! Estas siendo muy constante" ; weekConsistency >= 40 -> "Vas bien, sigue asi"; else -> "Puedes mejorar. Un habito a la vez" },
                    style = DanbronType.caption, color = TextTertiary
                )
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
                        "Meta: $0 en $monthsLeft meses - pagando $${String.format("%,.0f", free * 0.6)}/mes",
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
                        Text("Sin limite", style = DanbronType.displayLarge.copy(fontSize = 38.sp), color = Teal)
                        Text("Sin deudas. Sigue asi", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                    }
                }
            }
        }

        // Habits
        Column(Modifier.padding(horizontal = 20.dp)) {
            StaggeredEntrance(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habitos activos", style = DanbronType.titleMedium, color = TextPrimary)
                    Text("+ Agregar", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                        color = Gold, modifier = Modifier.clickable { showAddHabit = true })
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
                                            "\u2713",
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
                                Text("Racha", modifier = Modifier.scale(fireScale), fontSize = 12.sp, color = Gold)
                            }
                            Text(
                                if (habit.streak > 0) "${habit.streak} dias" else "Empieza hoy",
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
                                        .border(1.dp, if (done) Gold else if (isToday) Gold.copy(alpha = 0.5f) else Border, RoundedCornerShape(8.dp))
                                        .clickable { vm.toggleHabitForWeekday(habit.id, i) },
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

    if (showAddHabit) {
        val habitFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            delay(250)
            habitFocusRequester.requestFocus()
            keyboardController?.show()
        }

        AlertDialog(
            onDismissRequest = { showAddHabit = false },
            containerColor = BgSecondary,
            title = { Text("Nuevo habito", style = DanbronType.titleMedium, color = TextPrimary) },
            text = {
                Column {
                    Text("Escribe el habito que quieres seguir.", style = DanbronType.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = habitName,
                        onValueChange = { habitName = it },
                        placeholder = { Text("Ej: Tomar agua", color = TextTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(habitFocusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = Border,
                            cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.addHabit(habitName)
                        habitName = ""
                        showAddHabit = false
                    },
                    enabled = habitName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabit = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    subtitle: String,
    progress: Float? = null,
    progressColor: Color = Gold
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSecondary)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = DanbronType.headlineSmall.copy(fontWeight = FontWeight.W700), color = accentColor)
        Spacer(Modifier.height(2.dp))
        Text(title, style = DanbronType.caption, color = TextSecondary)
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(BgTertiary)) {
                val animP = animatedProgress(progress)
                Box(Modifier.fillMaxWidth(animP).height(4.dp).clip(RoundedCornerShape(2.dp)).background(progressColor))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = DanbronType.caption.copy(fontSize = 9.sp), color = TextTertiary)
    }
}

