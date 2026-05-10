package com.danbron.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danbron.app.api.*
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.models.*
import com.danbron.app.engine.AdaptiveEngine
import com.danbron.app.util.HapticManager
import com.danbron.app.util.ReferralManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = UserPreferences(app)
    private val ctx = app

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits

    private val _habitLogs = MutableStateFlow<List<HabitLog>>(emptyList())
    val habitLogs: StateFlow<List<HabitLog>> = _habitLogs

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded

    private val _bronMsg = MutableStateFlow("")
    val bronMsg: StateFlow<String> = _bronMsg

    private val _usageMinutes = MutableStateFlow(0)
    val usageMinutes: StateFlow<Int> = _usageMinutes

    private val _toastMsg = MutableSharedFlow<String>()
    val toastMsg: SharedFlow<String> = _toastMsg

    private val _referralCode = MutableStateFlow("")
    val referralCode: StateFlow<String> = _referralCode

    private val _referralCount = MutableStateFlow(0)
    val referralCount: StateFlow<Int> = _referralCount

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    // Celebration trigger
    private val _allTasksDone = MutableStateFlow(false)
    val allTasksDone: StateFlow<Boolean> = _allTasksDone

    // ── Sync Code ──
    private val _syncCode = MutableStateFlow("")
    val syncCode: StateFlow<String> = _syncCode

    // ── Adaptive System ──
    private val _userSegment = MutableStateFlow(UserSegment())
    val userSegment: StateFlow<UserSegment> = _userSegment

    private val _activeModules = MutableStateFlow<List<DashboardModule>>(emptyList())
    val activeModules: StateFlow<List<DashboardModule>> = _activeModules

    private val _bronMood = MutableStateFlow(BronMood.NEUTRAL)
    val bronMood: StateFlow<BronMood> = _bronMood

    private val todayStr: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        viewModelScope.launch { prefs.user.collect { _user.value = it; it?.let { u -> updateAdaptiveState(u) } } }
        viewModelScope.launch { prefs.tasks.collect { _tasks.value = it } }
        viewModelScope.launch { prefs.habits.collect { _habits.value = it } }
        viewModelScope.launch { prefs.habitLogs.collect { _habitLogs.value = it } }
        viewModelScope.launch { prefs.notes.collect { _notes.value = it } }
        viewModelScope.launch { prefs.apiKey.collect { _apiKey.value = it } }
        viewModelScope.launch { prefs.onboarded.collect { _onboarded.value = it } }
        viewModelScope.launch { prefs.lastBronMsg.collect { _bronMsg.value = it } }
        viewModelScope.launch { prefs.usageMinutes.collect { _usageMinutes.value = it } }
        viewModelScope.launch { prefs.referralCode.collect { _referralCode.value = it } }
        viewModelScope.launch { prefs.referralCount.collect { _referralCount.value = it } }
        viewModelScope.launch { prefs.isPro.collect { _isPro.value = it } }
        viewModelScope.launch { prefs.syncCode.collect { _syncCode.value = it } }
        // Track usage
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                _usageMinutes.value++
                prefs.saveUsage(_usageMinutes.value)
            }
        }
    }

    private fun updateAdaptiveState(user: User) {
        val segment = AdaptiveEngine.classifyUser(user)
        _userSegment.value = segment
        _activeModules.value = AdaptiveEngine.getActiveModules(user)
        val done = _tasks.value.count { it.done }
        _bronMood.value = AdaptiveEngine.calculateBronMood(user, done, _tasks.value.size)
    }

    // ── Sync Code Generation ──

    fun generateSyncCode(): String {
        val code = (100000..999999).random().toString()
        _syncCode.value = code
        viewModelScope.launch { 
            prefs.saveSyncCode(code) 
            _user.value?.let { user ->
                com.danbron.app.data.SyncManager.pushProfile(code, user)
            }
        }
        return code
    }

    // ── Complete onboarding from conversational flow ──

    fun completeConversationalOnboarding(user: User) {
        _user.value = user

        // Classify and generate adaptive content
        val segment = AdaptiveEngine.classifyUser(user)
        _userSegment.value = segment
        _activeModules.value = AdaptiveEngine.getActiveModules(user)
        _tasks.value = AdaptiveEngine.generateAdaptiveTasks(user, segment)
        _habits.value = generateAdaptiveHabits(user, segment)

        // Generate referral code
        val code = ReferralManager.generateCode(user.name)
        _referralCode.value = code

        viewModelScope.launch {
            prefs.saveUser(user)
            prefs.saveTasks(_tasks.value)
            prefs.saveHabits(_habits.value)
            prefs.saveReferralCode(code)
            prefs.setOnboarded()
        }
    }

    private fun generateAdaptiveHabits(user: User, segment: UserSegment): List<Habit> {
        val habits = mutableListOf<Habit>()
        var id = System.currentTimeMillis()

        // Universal habits
        habits.add(Habit(id = id++, name = "Revisar mi plan del día", streak = 0))

        if (segment.activeModules.contains("DEBT_TRACKER") || segment.activeModules.contains("BUDGET_MICRO")) {
            habits.add(Habit(id = id++, name = "Sin gastos innecesarios", streak = 0))
        }

        if (segment.activeModules.contains("HEALTH_WEIGHT") || user.healthFocus != "none") {
            habits.add(Habit(id = id++, name = "Ejercicio 30 min", streak = 0))
        }

        if (user.challenges.contains("bad_diet") || user.healthFocus == "weight_loss") {
            habits.add(Habit(id = id++, name = "Comer saludable hoy", streak = 0))
        }

        if (user.challenges.contains("insomnia") || user.challenges.contains("screen_time")) {
            habits.add(Habit(id = id++, name = "Pantallas off a las 22:00", streak = 0))
        }

        if (segment.activeModules.contains("CAREER_SEARCH")) {
            habits.add(Habit(id = id++, name = "Buscar 1 oportunidad laboral", streak = 0))
        }

        if (segment.activeModules.contains("HEALTH_MENTAL") || user.challenges.contains("anxiety")) {
            habits.add(Habit(id = id++, name = "10 min de calma (respirar/meditar)", streak = 0))
        }

        return habits.take(5) // Max 5 habits to start
    }

    // ── Task management ──

    fun toggleTask(id: Long) {
        val wasDone = _tasks.value.find { it.id == id }?.done ?: false
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(done = !it.done) else it
        }

        // Haptic feedback
        if (!wasDone) {
            HapticManager.taskComplete(ctx)
        } else {
            HapticManager.tap(ctx)
        }

        // Update Bron mood based on task completion
        _user.value?.let { u ->
            val done = _tasks.value.count { it.done }
            _bronMood.value = AdaptiveEngine.calculateBronMood(u, done, _tasks.value.size)
        }

        // Check if all tasks done → celebration
        val allDone = _tasks.value.all { it.done }
        if (allDone && _tasks.value.isNotEmpty() && !wasDone) {
            _allTasksDone.value = true
            _bronMood.value = BronMood.CELEBRATING
            HapticManager.celebration(ctx)
            viewModelScope.launch {
                _toastMsg.emit("🎉 ¡Todas las tareas del día completadas!")
                delay(5000)
                _allTasksDone.value = false
            }
        }

        viewModelScope.launch { prefs.saveTasks(_tasks.value) }
    }

    // ── Habit management ──

    fun toggleHabitToday(habitId: Long) {
        val today = todayStr
        val existing = _habitLogs.value.find { it.habitId == habitId && it.date == today }

        if (existing != null) {
            _habitLogs.value = _habitLogs.value.filter { !(it.habitId == habitId && it.date == today) }
            HapticManager.tap(ctx)
        } else {
            _habitLogs.value = _habitLogs.value + HabitLog(habitId, today)
            HapticManager.taskComplete(ctx)
        }

        updateHabitWeekAndStreak(habitId)

        viewModelScope.launch {
            prefs.saveHabitLogs(_habitLogs.value)
            prefs.saveHabits(_habits.value)
        }
    }

    private fun updateHabitWeekAndStreak(habitId: Long) {
        val logs = _habitLogs.value.filter { it.habitId == habitId }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        val todayDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val week = MutableList(7) { false }
        for (i in 0..6) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -(todayDow - i))
            val dateStr = fmt.format(c.time)
            week[i] = logs.any { it.date == dateStr }
        }

        var streak = 0
        val c2 = Calendar.getInstance()
        while (true) {
            val dateStr = fmt.format(c2.time)
            if (logs.any { it.date == dateStr }) {
                streak++
                c2.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }

        _habits.value = _habits.value.map {
            if (it.id == habitId) it.copy(week = week, streak = streak) else it
        }
    }

    fun isHabitDoneToday(habitId: Long): Boolean {
        return _habitLogs.value.any { it.habitId == habitId && it.date == todayStr }
    }

    fun addHabit(name: String) {
        _habits.value = _habits.value + Habit(name = name)
        HapticManager.tap(ctx)
        viewModelScope.launch { prefs.saveHabits(_habits.value) }
    }

    // ── Notes management ──

    fun addNote(title: String, content: String = "", tag: String = "general") {
        val note = Note(title = title, content = content, tag = tag)
        _notes.value = listOf(note) + _notes.value
        HapticManager.tap(ctx)
        viewModelScope.launch { prefs.saveNotes(_notes.value) }
    }

    fun updateNote(id: Long, title: String, content: String, tag: String) {
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content, tag = tag, updatedAt = System.currentTimeMillis())
            else it
        }
        viewModelScope.launch { prefs.saveNotes(_notes.value) }
    }

    fun deleteNote(id: Long) {
        _notes.value = _notes.value.filter { it.id != id }
        HapticManager.tap(ctx)
        viewModelScope.launch { prefs.saveNotes(_notes.value) }
    }

    // ── Bron Messages ──

    fun toast(msg: String) {
        viewModelScope.launch { _toastMsg.emit(msg) }
    }

    // ── Referrals ──

    fun getShareIntent(): android.content.Intent {
        val name = _user.value?.name ?: "Usuario"
        val code = _referralCode.value.ifBlank { ReferralManager.generateCode(name) }
        return ReferralManager.createShareIntent(name, code)
    }

    fun resetApp() {
        viewModelScope.launch {
            prefs.clearAll()
            _user.value = null
            _onboarded.value = false
            _tasks.value = emptyList()
            _habits.value = emptyList()
            _habitLogs.value = emptyList()
            _notes.value = emptyList()
            _apiKey.value = ""
            _bronMsg.value = ""
            _referralCode.value = ""
            _referralCount.value = 0
            _isPro.value = false
            _syncCode.value = ""
            _userSegment.value = UserSegment()
            _activeModules.value = emptyList()
            _bronMood.value = BronMood.NEUTRAL
        }
    }

    fun fetchDailyBronMsg() {
        val key = _apiKey.value
        val u = _user.value ?: return
        if (key.isBlank()) return
        viewModelScope.launch {
            try {
                val segment = _userSegment.value
                val done = _tasks.value.count { it.done }
                val systemPrompt = AdaptiveEngine.buildAdaptiveSystemPrompt(u, segment)
                val msg = AiProvider.sendChat(
                    apiKey = key,
                    systemPrompt = systemPrompt,
                    messages = listOf(ApiMessage("user",
                        "Es un nuevo día. Tengo ${_tasks.value.size} tareas pendientes y he completado $done hoy. Dame tu mensaje diario: qué debo priorizar hoy y una acción concreta basada en mi perfil."
                    )),
                    maxTokens = 250
                )
                _bronMsg.value = msg
                prefs.saveLastBronMsg(msg)
            } catch (_: Exception) {}
        }
    }

    fun editProfile() {
        toast("Función de edición de perfil - Próximamente")
    }

    fun updateUserProfile(user: User) {
        _user.value = user
        viewModelScope.launch {
            prefs.saveUser(user)
            updateAdaptiveState(user)
        }
    }
}
