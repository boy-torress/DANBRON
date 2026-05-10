package com.danbron.app.data.models

// ── User Profile with Life Dimensions ──

data class User(
    val name: String = "",
    val goal: String = "habits",
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val debt: Double = 0.0,
    val workStyle: String = "full_time",
    val joinDate: Long = System.currentTimeMillis(),
    val streak: Int = 0,
    // ── Life Dimensions (Adaptive System) ──
    val age: Int = 0,
    val lifeRole: String = "individual",        // "parent", "student", "retired", "caregiver", "individual"
    val familySize: Int = 1,
    val employmentStatus: String = "employed",   // "employed", "unemployed", "self_employed", "student", "retired"
    val healthFocus: String = "none",            // "weight_loss", "fitness", "mental_health", "none"
    val sleepGoal: String = "23:00",
    val wakeGoal: String = "07:00",
    val stressLevel: Int = 5,                    // 1-10
    val priorities: List<String> = emptyList(),  // ["debt","health","family","career","education"]
    val challenges: List<String> = emptyList(),  // ["no_savings","impulse_spending","sedentary","insomnia","unemployment"]
    val bronMood: String = "neutral",
    val lastCheckIn: Long = 0,
    val consecutiveDaysActive: Int = 0,
    val lifeSummary: String = ""
) {
    val freeFlow: Double get() = income - expenses
    val monthsToDebtFree: Int get() = if (debt > 0 && freeFlow > 0) {
        kotlin.math.ceil(debt / (freeFlow * 0.6)).toInt()
    } else 0
}

// ── Life Segment Classification ──

enum class LifeSegment(val label: String, val icon: String) {
    STUDENT_BROKE("Estudiante", "🎓"),
    YOUNG_DEBT("Joven con deudas", "💸"),
    PARENT_BUSY("Padre/Madre ocupado", "👨‍👧"),
    RETIRED_OPTIMIZE("Jubilado", "👴"),
    UNEMPLOYED_SEEKING("Buscando empleo", "🔍"),
    ENTREPRENEUR("Emprendedor", "🚀"),
    HEALTH_FOCUSED("Enfocado en salud", "💪"),
    GENERAL_OPTIMIZER("Optimizador general", "⚡")
}

data class UserSegment(
    val segment: LifeSegment = LifeSegment.GENERAL_OPTIMIZER,
    val confidence: Float = 0.5f,
    val activeModules: List<String> = emptyList(),
    val bronPersonality: String = "friend"  // "coach", "friend", "mentor", "parent"
)

// ── Bron Mascot States ──

enum class BronMood(val emoji: String, val label: String) {
    HAPPY("😊", "Feliz"),
    PROUD("🤩", "Orgulloso"),
    CONCERNED("😟", "Preocupado"),
    MOTIVATED("💪", "Motivado"),
    SLEEPY("😴", "Soñoliento"),
    CELEBRATING("🎉", "Celebrando"),
    THINKING("🤔", "Pensando"),
    NEUTRAL("😌", "Tranquilo")
}

enum class BronPersonality(val tone: String, val description: String) {
    COACH("coach", "Directo, con metas numéricas, accountability"),
    FRIEND("friend", "Casual, usa humor, celebra logros pequeños"),
    MENTOR("mentor", "Sabio, empático, guía con experiencia"),
    PARENT("parent", "Cariñoso, protector, firme pero amoroso")
}

// ── Dashboard Module Types ──

enum class ModuleType(val title: String, val icon: String) {
    DEBT_TRACKER("Tracker de Deuda", "💰"),
    BUDGET_MICRO("Presupuesto", "📊"),
    INCOME_BOOST("Ingresos Extra", "🚀"),
    HEALTH_WEIGHT("Peso y Ejercicio", "🏋️"),
    HEALTH_SLEEP("Sueño", "😴"),
    HEALTH_MENTAL("Bienestar Mental", "🧠"),
    CAREER_SEARCH("Búsqueda de Empleo", "🔍"),
    CAREER_SKILLS("Desarrollo de Habilidades", "📚"),
    FAMILY_TIME("Tiempo Familiar", "👨‍👩‍👧"),
    FAMILY_SAVINGS("Ahorro Familiar", "🏦"),
    HABIT_STREAKS("Rachas de Hábitos", "🔥"),
    PRODUCTIVITY("Productividad", "⚡"),
    DAILY_PLAN("Plan del Día", "📋")
}

data class DashboardModule(
    val type: ModuleType,
    val priority: Int,
    val isActive: Boolean = true,
    val customTitle: String? = null,
    val customSubtitle: String? = null
) {
    val title: String get() = customTitle ?: type.title
    val icon: String get() = type.icon
}

// ── Existing Models (unchanged) ──

data class Task(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val tag: String = "habit",
    val time: String = "",
    val done: Boolean = false
)

data class Habit(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val streak: Int = 0,
    val week: List<Boolean> = List(7) { false }
)

data class HabitLog(
    val habitId: Long,
    val date: String, // "yyyy-MM-dd"
    val completed: Boolean = true
)

data class Note(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val content: String = "",
    val tag: String = "general", // general, finance, goal, idea
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
