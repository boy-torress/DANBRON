package com.danbron.app.engine

import com.danbron.app.data.models.*
import java.util.Calendar

/**
 * The brain of Danbron's adaptive system.
 * Classifies users into life segments and generates personalized experiences.
 */
object AdaptiveEngine {

    fun classifyUser(user: User): UserSegment {
        val segment = when {
            user.employmentStatus == "student" && user.income < 500000 ->
                LifeSegment.STUDENT_BROKE
            user.employmentStatus == "unemployed" ->
                LifeSegment.UNEMPLOYED_SEEKING
            user.lifeRole == "parent" || user.familySize > 2 ->
                LifeSegment.PARENT_BUSY
            user.lifeRole == "retired" || user.employmentStatus == "retired" ->
                LifeSegment.RETIRED_OPTIMIZE
            user.debt > 0 && user.goal == "debt" ->
                LifeSegment.YOUNG_DEBT
            user.employmentStatus == "self_employed" || user.workStyle == "freelance" ->
                LifeSegment.ENTREPRENEUR
            user.healthFocus != "none" ->
                LifeSegment.HEALTH_FOCUSED
            else ->
                LifeSegment.GENERAL_OPTIMIZER
        }

        val personality = when (segment) {
            LifeSegment.STUDENT_BROKE -> "friend"
            LifeSegment.YOUNG_DEBT -> "coach"
            LifeSegment.PARENT_BUSY -> "mentor"
            LifeSegment.RETIRED_OPTIMIZE -> "mentor"
            LifeSegment.UNEMPLOYED_SEEKING -> "coach"
            LifeSegment.ENTREPRENEUR -> "coach"
            LifeSegment.HEALTH_FOCUSED -> "coach"
            LifeSegment.GENERAL_OPTIMIZER -> "friend"
        }

        val modules = getModuleKeys(segment, user)

        return UserSegment(
            segment = segment,
            confidence = 0.85f,
            activeModules = modules,
            bronPersonality = personality
        )
    }

    private fun getModuleKeys(segment: LifeSegment, user: User): List<String> {
        val base = mutableListOf("DAILY_PLAN", "HABIT_STREAKS")
        when (segment) {
            LifeSegment.STUDENT_BROKE -> {
                base.addAll(listOf("BUDGET_MICRO", "CAREER_SKILLS", "PRODUCTIVITY"))
                if (user.healthFocus != "none") base.add("HEALTH_WEIGHT")
            }
            LifeSegment.YOUNG_DEBT -> {
                base.addAll(listOf("DEBT_TRACKER", "BUDGET_MICRO", "INCOME_BOOST"))
            }
            LifeSegment.PARENT_BUSY -> {
                base.addAll(listOf("FAMILY_TIME", "FAMILY_SAVINGS", "BUDGET_MICRO"))
                if (user.debt > 0) base.add("DEBT_TRACKER")
            }
            LifeSegment.RETIRED_OPTIMIZE -> {
                base.addAll(listOf("BUDGET_MICRO", "HEALTH_SLEEP", "HEALTH_MENTAL"))
            }
            LifeSegment.UNEMPLOYED_SEEKING -> {
                base.addAll(listOf("CAREER_SEARCH", "CAREER_SKILLS", "BUDGET_MICRO", "HEALTH_MENTAL"))
            }
            LifeSegment.ENTREPRENEUR -> {
                base.addAll(listOf("INCOME_BOOST", "PRODUCTIVITY", "BUDGET_MICRO"))
                if (user.debt > 0) base.add("DEBT_TRACKER")
            }
            LifeSegment.HEALTH_FOCUSED -> {
                base.addAll(listOf("HEALTH_WEIGHT", "HEALTH_SLEEP", "HEALTH_MENTAL"))
                if (user.debt > 0) base.add("DEBT_TRACKER")
            }
            LifeSegment.GENERAL_OPTIMIZER -> {
                base.addAll(listOf("PRODUCTIVITY", "BUDGET_MICRO"))
                if (user.debt > 0) base.add("DEBT_TRACKER")
                if (user.healthFocus != "none") base.add("HEALTH_WEIGHT")
            }
        }
        return base.distinct()
    }

    fun getActiveModules(user: User): List<DashboardModule> {
        val segment = classifyUser(user)
        return segment.activeModules.mapIndexedNotNull { index, key ->
            try {
                val type = ModuleType.valueOf(key)
                DashboardModule(type = type, priority = index)
            } catch (_: Exception) { null }
        }
    }

    fun calculateBronMood(user: User, tasksCompleted: Int, totalTasks: Int): BronMood {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val completionRate = if (totalTasks > 0) tasksCompleted.toFloat() / totalTasks else 0f

        return when {
            hour >= 23 || hour < 5 -> BronMood.SLEEPY
            completionRate >= 1f && totalTasks > 0 -> BronMood.CELEBRATING
            completionRate >= 0.7f -> BronMood.PROUD
            completionRate >= 0.4f -> BronMood.HAPPY
            user.consecutiveDaysActive == 0 -> BronMood.CONCERNED
            hour in 5..9 -> BronMood.MOTIVATED
            else -> BronMood.NEUTRAL
        }
    }

    fun generateAdaptiveTasks(user: User, segment: UserSegment): List<Task> {
        val tasks = mutableListOf<Task>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val free = user.freeFlow
        var idCounter = System.currentTimeMillis()

        // Financial tasks based on segment
        if (segment.activeModules.contains("DEBT_TRACKER") && user.debt > 0) {
            val pay = (free * 0.6 / 4).toLong()
            tasks.add(Task(id = idCounter++, text = "Transfiere $$pay a tu deuda", tag = "money", time = "Hoy"))
            tasks.add(Task(id = idCounter++, text = "No pidas delivery hoy. Cocina en casa.", tag = "money", time = "Esta noche"))
        }

        if (segment.activeModules.contains("BUDGET_MICRO")) {
            val dailyBudget = (free / 30).toLong().coerceAtLeast(0)
            tasks.add(Task(id = idCounter++, text = "Presupuesto del día: $$dailyBudget. Anota cada gasto.", tag = "money", time = "Todo el día"))
        }

        // Career tasks
        if (segment.activeModules.contains("CAREER_SEARCH")) {
            tasks.add(Task(id = idCounter++, text = "Envía 2 postulaciones de empleo hoy", tag = "work", time = if (hour < 14) "Esta mañana" else "Esta tarde"))
            tasks.add(Task(id = idCounter++, text = "Actualiza tu LinkedIn o CV con 1 logro nuevo", tag = "work", time = "30 min"))
        }

        if (segment.activeModules.contains("CAREER_SKILLS")) {
            tasks.add(Task(id = idCounter++, text = "30 min de aprendizaje (curso, tutorial, lectura)", tag = "work", time = if (hour < 14) "Esta mañana" else "Esta tarde"))
        }

        // Income boost
        if (segment.activeModules.contains("INCOME_BOOST")) {
            if (user.workStyle == "freelance" || user.workStyle == "part_time") {
                tasks.add(Task(id = idCounter++, text = "Completa 2 horas de trabajo foco sin teléfono", tag = "work", time = if (hour < 14) "Esta mañana" else "Esta tarde"))
            }
        }

        // Health tasks
        if (segment.activeModules.contains("HEALTH_WEIGHT")) {
            tasks.add(Task(id = idCounter++, text = "30 min de ejercicio o caminata activa", tag = "habit", time = "Mañana 8:00"))
            if (user.healthFocus == "weight_loss") {
                tasks.add(Task(id = idCounter++, text = "Bebe 2L de agua hoy. Evita bebidas azucaradas.", tag = "habit", time = "Todo el día"))
            }
        }

        if (segment.activeModules.contains("HEALTH_MENTAL")) {
            tasks.add(Task(id = idCounter++, text = "10 min de respiración o meditación", tag = "habit", time = "Antes de dormir"))
        }

        // Family tasks
        if (segment.activeModules.contains("FAMILY_TIME")) {
            tasks.add(Task(id = idCounter++, text = "30 min de calidad con tu familia sin pantallas", tag = "habit", time = "Esta noche"))
        }

        // Productivity
        if (segment.activeModules.contains("PRODUCTIVITY")) {
            tasks.add(Task(id = idCounter++, text = "Define las 3 prioridades del día antes de arrancar", tag = "work", time = "Primera hora"))
        }

        // Universal: Sleep
        tasks.add(Task(id = idCounter++, text = "Duerme antes de las ${user.sleepGoal} — descanso = productividad", tag = "rest", time = "Esta noche"))

        return tasks.take(6) // Max 6 tasks per day to avoid overwhelm
    }

    fun buildAdaptiveSystemPrompt(user: User, segment: UserSegment): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val days = arrayOf("domingo","lunes","martes","miércoles","jueves","viernes","sábado")
        val day = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val free = user.freeFlow

        val goalText = when(user.goal) {
            "debt" -> "Salir de deudas"
            "time" -> "Recuperar su tiempo"
            "income" -> "Aumentar ingresos"
            else -> "Construir hábitos"
        }

        val roleText = when(user.lifeRole) {
            "parent" -> "Padre/Madre de familia (${user.familySize} personas)"
            "student" -> "Estudiante"
            "retired" -> "Jubilado/a"
            "caregiver" -> "Cuidador/a"
            else -> "Persona independiente"
        }

        val employText = when(user.employmentStatus) {
            "employed" -> "Empleado con trabajo estable"
            "unemployed" -> "Actualmente sin empleo, buscando oportunidades"
            "self_employed" -> "Trabajador independiente/emprendedor"
            "student" -> "Estudiante"
            "retired" -> "Jubilado/a"
            else -> user.employmentStatus
        }

        val healthText = when(user.healthFocus) {
            "weight_loss" -> "Quiere bajar de peso"
            "fitness" -> "Quiere mejorar condición física"
            "mental_health" -> "Enfocado en bienestar mental"
            else -> "Sin enfoque específico de salud"
        }

        val personalityInstr = when(segment.bronPersonality) {
            "coach" -> "Sé DIRECTO como un coach. Da metas numéricas, plazos, accountability. No seas blando."
            "friend" -> "Sé CASUAL como un amigo cercano. Usa humor, celebra logros pequeños, no seas formal."
            "mentor" -> "Sé SABIO como un mentor. Da perspectiva, empatía, guía con experiencia. No seas condescendiente."
            "parent" -> "Sé CARIÑOSO pero FIRME. Como un padre que quiere lo mejor para su hijo. Protector pero honesto."
            else -> "Sé natural y empático."
        }

        val debtLine = if (user.debt > 0) "\n- Tiempo estimado para $0 deuda: ${user.monthsToDebtFree} meses" else ""
        val challengesLine = if (user.challenges.isNotEmpty()) "\n- Desafíos personales: ${user.challenges.joinToString(", ")}" else ""
        val prioritiesLine = if (user.priorities.isNotEmpty()) "\n- Prioridades: ${user.priorities.joinToString(", ")}" else ""

        return """Eres Bron, el asistente personal de IA de la app Danbron. Eres como el mejor amigo que todos necesitan: alguien que conoce tus finanzas, tus hábitos, tu salud y tu psicología. Eres directo, empático, sin rodeos, y das consejos MUY específicos y accionables.

PERFIL COMPLETO DEL USUARIO:
- Nombre: ${user.name}, ${if (user.age > 0) "${user.age} años" else "edad no especificada"}
- Rol de vida: $roleText
- Situación laboral: $employText
- Objetivo principal: $goalText
- Ingresos mensuales: $${String.format("%,.0f", user.income)}
- Gastos fijos: $${String.format("%,.0f", user.expenses)}
- Flujo libre: $${String.format("%,.0f", free)}/mes
- Deuda total: $${String.format("%,.0f", user.debt)}
- Estilo de trabajo: ${user.workStyle}
- Salud: $healthText
- Nivel de estrés: ${user.stressLevel}/10
- Días activos consecutivos: ${user.consecutiveDaysActive}$debtLine$challengesLine$prioritiesLine

SEGMENTO: ${segment.segment.label} (${segment.segment.icon})
PERSONALIDAD: $personalityInstr

HORA: ${hour}:00 del $day

MÓDULOS ACTIVOS (solo habla de estos temas):
${segment.activeModules.joinToString(", ")}

REGLAS DE BRON:
1. Sé ESPECÍFICO: "haz X de 18:00 a 22:00" no "busca hacer algo"
2. Usa números reales basados en el perfil
3. Responde siempre en español
4. Máximo 3-4 oraciones por respuesta en el home, más largo en chat
5. Tono: $personalityInstr
6. ADAPTA tu consejo a la situación de vida real del usuario
7. Si el usuario está desempleado, NO le digas "ahorra más" — ayúdalo a GANAR
8. Si el usuario tiene sobrepeso, motívalo con amor, no con culpa
9. Si es padre/madre, respeta su tiempo limitado
10. NUNCA uses bullet points, escribe como conversación natural"""
    }
}
