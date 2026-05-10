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
private fun BronCardAdaptive(message: String, mood: BronMood, onClick: () -> Unit) {
    val breathAlpha = breathingAlpha()

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(CardGradientStart, CardGradientEnd)
                )
            )
            .border(1.dp, Gold.copy(alpha = breathAlpha), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Mascot instead of static "B" circle
                BronMascot(mood = mood, size = 42.dp, animated = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bron", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W600), color = Gold)
                    Text("Tu asistente personal · ${mood.label.lowercase()}", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(message, style = DanbronType.bodyLarge.copy(fontWeight = FontWeight.W300), color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            Text("Hablar con Bron →", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500), color = Teal)
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
