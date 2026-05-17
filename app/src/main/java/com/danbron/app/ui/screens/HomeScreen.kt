package com.danbron.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.*
import com.danbron.app.ui.components.*
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(vm: MainViewModel, onChatClick: () -> Unit) {
    val user by vm.user.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val bronMsg by vm.bronMsg.collectAsState()
    val allTasksDone by vm.allTasksDone.collectAsState()
    val activeModules by vm.activeModules.collectAsState()
    val bronMood by vm.bronMood.collectAsState()
    val segment by vm.userSegment.collectAsState()
    val u = user ?: return

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 12 -> "Buenos días"; hour < 19 -> "Buenas tardes"; else -> "Buenas noches" }
    val dateFmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date())
    val doneCount = tasks.count { it.done }
    val isDriverProfile = u.workStyle.contains("driver", ignoreCase = true) ||
        u.lifeSummary.contains("uber", ignoreCase = true) ||
        u.lifeSummary.contains("chofer", ignoreCase = true) ||
        u.employmentStatus == "self_employed"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            // Header with segment badge
            StaggeredEntrance(0) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text("$greeting,", style = DanbronType.headlineMedium, color = TextPrimary)
                        Text("${u.name} 👋", style = DanbronType.headlineMedium, color = Gold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("📅", fontSize = 16.sp)
                        Text(dateFmt, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                    }
                }
            }

            // Segment badge
            if (segment.segment != LifeSegment.GENERAL_OPTIMIZER) {
                StaggeredEntrance(0) {
                    Box(Modifier.padding(start = 24.dp, top = 6.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(GoldGlow)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${segment.segment.icon} ${segment.segment.label}",
                                style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W600),
                                color = Gold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bron Card with mascot
            StaggeredEntrance(1) {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    BronCardAdaptive(
                        message = bronMsg.ifBlank { "$greeting, ${u.name}. Cargando tu plan de hoy..." },
                        mood = bronMood,
                        onClick = onChatClick
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            StaggeredEntrance(2) {
                DanbronOsStatusCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Dynamic Stats based on active modules
            StaggeredEntrance(2) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeModules.any { it.type == ModuleType.DEBT_TRACKER } && u.debt > 0) {
                        Box(Modifier.weight(1f)) {
                            StatCard("Deuda total", "$${String.format("%,.0f", u.debt)}", Red,
                                "Meta: $0 en ${u.monthsToDebtFree} meses",
                                progress = 0.02f, progressColor = Red)
                        }
                        Box(Modifier.weight(1f)) {
                            val free = u.freeFlow
                            StatCard("Flujo libre", "$${String.format("%,.0f", kotlin.math.abs(free))}",
                                if (free > 0) Teal else Red,
                                if (free > 0) "disponible/mes" else "déficit/mes")
                        }
                    } else if (activeModules.any { it.type == ModuleType.CAREER_SEARCH }) {
                        Box(Modifier.weight(1f)) {
                            StatCard("Días activos", "${u.consecutiveDaysActive}", Teal, "racha de búsqueda")
                        }
                        Box(Modifier.weight(1f)) {
                            StatCard("Completadas", "$doneCount/${tasks.size}", Gold, "Tareas de hoy",
                                progress = if (tasks.isNotEmpty()) doneCount.toFloat() / tasks.size else 0f,
                                progressColor = Gold)
                        }
                    } else if (activeModules.any { it.type == ModuleType.HEALTH_WEIGHT }) {
                        Box(Modifier.weight(1f)) {
                            StatCard("Estrés", "${u.stressLevel}/10",
                                when { u.stressLevel <= 3 -> Teal; u.stressLevel <= 6 -> Gold; else -> Red },
                                if (u.stressLevel > 6) "Necesitas descansar" else "Vas bien")
                        }
                        Box(Modifier.weight(1f)) {
                            StatCard("Completadas", "$doneCount/${tasks.size}", Gold, "Tareas de hoy",
                                progress = if (tasks.isNotEmpty()) doneCount.toFloat() / tasks.size else 0f,
                                progressColor = Gold)
                        }
                    } else {
                        Box(Modifier.weight(1f)) {
                            StatCard("Racha actual", "${u.streak} días", Teal, "Mejor racha: ${u.streak} días")
                        }
                        Box(Modifier.weight(1f)) {
                            StatCard("Completadas", "$doneCount/${tasks.size}", Gold, "Tareas de hoy",
                                progress = if (tasks.isNotEmpty()) doneCount.toFloat() / tasks.size else 0f,
                                progressColor = Gold)
                        }
                    }
                }
            }

            if (isDriverProfile) {
                StaggeredEntrance(3) {
                    DriverFinanceCard(
                        income = u.income,
                        expenses = u.expenses,
                        debt = u.debt,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            // Active module cards (contextual tips)
            activeModules.filter { it.type != ModuleType.DAILY_PLAN && it.type != ModuleType.HABIT_STREAKS && it.type != ModuleType.DEBT_TRACKER }
                .take(2).forEachIndexed { idx, module ->
                    StaggeredEntrance(3 + idx) {
                        ModuleHintCard(module, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }

            // Tasks
            StaggeredEntrance(5) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Plan de hoy", style = DanbronType.titleMedium, color = TextPrimary)
                    Text("Actualizar ↻", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                        color = Gold, modifier = Modifier.clickable { vm.fetchDailyBronMsg() })
                }
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.take(6).forEachIndexed { index, task ->
                    StaggeredEntrance(6 + index) {
                        TaskItem(task) { vm.toggleTask(task.id) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Habits quick view
            val habits by vm.habits.collectAsState()
            if (habits.isNotEmpty()) {
                StaggeredEntrance(12) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("Habitos de hoy", style = DanbronType.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            habits.take(4).forEach { habit ->
                                val done = vm.isHabitDoneToday(habit.id)
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (done) Gold.copy(alpha = 0.12f) else BgSecondary)
                                        .border(1.dp, if (done) Gold.copy(alpha = 0.3f) else Border, RoundedCornerShape(12.dp))
                                        .clickable { vm.toggleHabitToday(habit.id) }
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (done) "✅" else "⬜", fontSize = 18.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(habit.name.take(10), style = DanbronType.caption, color = if (done) Gold else TextSecondary, maxLines = 1)
                                        if (habit.streak > 0) {
                                            Text("${habit.streak}d", style = DanbronType.caption, color = Gold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Notes quick preview
            val notes by vm.notes.collectAsState()
            if (notes.isNotEmpty()) {
                StaggeredEntrance(13) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("Notas recientes", style = DanbronType.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))
                        notes.take(3).forEach { note ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgSecondary)
                                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(when(note.tag) { "finance" -> "💰"; "goal" -> "🎯"; "idea" -> "💡"; else -> "📝" }, fontSize = 14.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, style = DanbronType.bodySmall.copy(fontWeight = FontWeight.W600), color = TextPrimary, maxLines = 1)
                                    if (note.content.isNotBlank()) {
                                        Text(note.content.take(60), style = DanbronType.caption, color = TextTertiary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sync status indicator
            val isPaired by vm.isPaired.collectAsState()
            val lastSync by vm.lastSyncTime.collectAsState()
            StaggeredEntrance(14) {
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPaired) Teal.copy(alpha = 0.06f) else BgSecondary)
                        .border(1.dp, if (isPaired) Teal.copy(alpha = 0.2f) else Border, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPaired) "🔗" else "📱", fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isPaired) "Sincronizado con tu PC" else "Sin dispositivo vinculado",
                            style = DanbronType.bodySmall.copy(fontWeight = FontWeight.W500),
                            color = if (isPaired) Teal else TextTertiary
                        )
                        if (isPaired && lastSync.isNotBlank()) {
                            Text("Ultima sync: $lastSync", style = DanbronType.caption, color = TextTertiary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Confetti overlay when all tasks done
        ConfettiEffect(
            trigger = allTasksDone,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Adaptive Bron Card with Mascot ──

@Composable
private fun DanbronOsStatusCard(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSecondary)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("Bron esta listo para ayudarte", style = DanbronType.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pídeme algo normal: recordar una idea, mirar la pantalla, preparar un plan, abrir una app o mandar una orden al computador.",
            style = DanbronType.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OsPill("Notas")
            OsPill("Perfil")
            OsPill("Mirar")
            OsPill("Tu PC")
        }
    }
}

@Composable
private fun OsPill(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(GoldGlow)
            .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, style = DanbronType.caption, color = Gold)
    }
}

@Composable
private fun DriverFinanceCard(
    income: Double,
    expenses: Double,
    debt: Double,
    modifier: Modifier = Modifier
) {
    val free = income - expenses
    val dailyTarget = if (income > 0) income / 26.0 else 0.0
    val fuelReserve = if (income > 0) income * 0.18 else 0.0
    val debtPlan = if (debt > 0 && free > 0) kotlin.math.ceil(debt / (free * 0.6)).toInt() else 0

    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSecondary)
            .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text("Driver Finance", style = DanbronType.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Control semanal para ganancias, horas, viajes y combustible.", style = DanbronType.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                StatCard(
                    "Meta diaria",
                    "$${String.format("%,.0f", dailyTarget)}",
                    Gold,
                    "26 dias activos"
                )
            }
            Box(Modifier.weight(1f)) {
                StatCard(
                    "Reserva",
                    "$${String.format("%,.0f", fuelReserve)}",
                    Teal,
                    "bencina/mantencion"
                )
            }
        }
        if (debtPlan > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Si mantienes el flujo libre actual, Danbron estima salida de deuda en $debtPlan meses usando 60% del excedente.",
                style = DanbronType.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun BronCardAdaptive(message: String, mood: BronMood, onClick: () -> Unit) {
    val breathAlpha = breathingAlpha()

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF12121E), Color(0xFF0A0A14))
                )
            )
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Gold.copy(alpha = breathAlpha), Gold.copy(alpha = breathAlpha * 0.3f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BronMascot(mood = mood, size = 44.dp, animated = true)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Bron", style = DanbronType.titleSmall, color = Gold)
                    Text("Tu amigo personal · ${mood.label.lowercase()}", style = DanbronType.caption, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(message, style = DanbronType.bodyLarge.copy(fontWeight = FontWeight.W300, lineHeight = 24.sp), color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Teal.copy(alpha = 0.08f))
                    .border(1.dp, Teal.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💬", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text("Hablar con Bron", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W600), color = Teal)
            }
        }
    }
}

// ── Module Hint Card ──

@Composable
private fun ModuleHintCard(module: DashboardModule, modifier: Modifier = Modifier) {
    val (bgColor, borderColor) = when (module.type) {
        ModuleType.HEALTH_WEIGHT, ModuleType.HEALTH_SLEEP -> Pair(Color(0x0D2DD4BF), Teal.copy(alpha = 0.2f))
        ModuleType.HEALTH_MENTAL -> Pair(Color(0x0D9B59B6), Color(0x339B59B6))
        ModuleType.CAREER_SEARCH, ModuleType.CAREER_SKILLS -> Pair(Color(0x0D3498DB), Color(0x333498DB))
        ModuleType.INCOME_BOOST -> Pair(GoldGlow, Gold.copy(alpha = 0.2f))
        ModuleType.FAMILY_TIME, ModuleType.FAMILY_SAVINGS -> Pair(Color(0x0DFF6B6B), Red.copy(alpha = 0.2f))
        else -> Pair(Color(0x0DE8B84B), Gold.copy(alpha = 0.2f))
    }

    val subtitle = module.customSubtitle ?: when (module.type) {
        ModuleType.HEALTH_WEIGHT -> "Bron monitorea tu actividad y te motiva"
        ModuleType.HEALTH_SLEEP -> "Bron te recuerda cuándo descansar"
        ModuleType.HEALTH_MENTAL -> "Tu bienestar mental importa"
        ModuleType.CAREER_SEARCH -> "Bron te ayuda a encontrar empleo"
        ModuleType.CAREER_SKILLS -> "Aprende algo nuevo cada día"
        ModuleType.INCOME_BOOST -> "Oportunidades para ganar más"
        ModuleType.FAMILY_TIME -> "Equilibra trabajo y familia"
        ModuleType.FAMILY_SAVINGS -> "Ahorra para lo que importa"
        ModuleType.BUDGET_MICRO -> "Control diario de tus gastos"
        ModuleType.PRODUCTIVITY -> "Maximiza tu tiempo"
        else -> "Módulo activo"
    }

    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(module.icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(module.title, style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500), color = TextPrimary)
            Text(subtitle, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextSecondary)
        }
    }
}
