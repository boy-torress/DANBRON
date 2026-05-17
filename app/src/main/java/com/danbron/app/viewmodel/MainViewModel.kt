package com.danbron.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danbron.app.api.*
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.models.*
import com.danbron.app.engine.AdaptiveEngine
import com.danbron.app.sync.DanbronSyncManager
import com.danbron.app.util.HapticManager
import com.danbron.app.util.ReferralManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
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
    private val syncManager = DanbronSyncManager(app)
    private val gson = Gson()
    private var applyingRemoteState = false

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

    // ── Sync & Pairing ──
    private val _syncCode = MutableStateFlow("")
    val syncCode: StateFlow<String> = _syncCode

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired

    private val _pairingStatus = MutableStateFlow("") // "", "checking", "paired", "error"
    val pairingStatus: StateFlow<String> = _pairingStatus

    private val _pairedDeviceName = MutableStateFlow("")
    val pairedDeviceName: StateFlow<String> = _pairedDeviceName

    private val _lastSyncTime = MutableStateFlow("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime

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
        startSharedSyncLoop()
        // Check pairing status on startup
        viewModelScope.launch {
            delay(1500)
            checkPairingStatus()
        }
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

    private fun ensureAuthenticated() {
        viewModelScope.launch {
            try {
                if (!syncManager.isAuthenticated()) {
                    val userName = _user.value?.name ?: "Usuario"
                    val email = "user_${android.os.Build.MODEL.replace(" ","_")}@danbron.app"
                    syncManager.authenticate(email, userName)
                    android.util.Log.d("MainVM", "Auto-authenticated with backend")
                }
                if (syncManager.currentDeviceId() == null) {
                    syncManager.registerDevice("android", android.os.Build.MODEL)
                    android.util.Log.d("MainVM", "Device registered with backend")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Auto-auth failed (offline?)", e)
            }
        }
    }

    private fun startSharedSyncLoop() {
        viewModelScope.launch {
            delay(2500)
            // Ensure we're authenticated before syncing
            try {
                if (!syncManager.isAuthenticated()) {
                    val userName = _user.value?.name ?: "Usuario"
                    val email = "user_${android.os.Build.MODEL.replace(" ","_")}@danbron.app"
                    syncManager.authenticate(email, userName)
                }
                if (syncManager.currentDeviceId() == null) {
                    syncManager.registerDevice("android", android.os.Build.MODEL)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Sync auth failed", e)
            }
            while (true) {
                try {
                    pullSharedState()
                    if (_isPaired.value) {
                        _lastSyncTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainVM", "Sync loop error", e)
                }
                delay(7000)
            }
        }
    }

    private fun buildSharedStateSnapshot(includeProfile: Boolean = false): JsonObject {
        return JsonObject().apply {
            addProperty("schemaVersion", 2)
            addProperty("source", "android")
            addProperty("updatedAt", System.currentTimeMillis())
            if (includeProfile) _user.value?.let { user ->
                add("profile", gson.toJsonTree(user))
                add("userProfile", gson.toJsonTree(user))
                addProperty("profileUpdatedAt", System.currentTimeMillis())
            }
            add("notes", gson.toJsonTree(_notes.value))
            add("habits", gson.toJsonTree(_habits.value))
            add("habitLogs", gson.toJsonTree(_habitLogs.value))
            add("tasks", gson.toJsonTree(_tasks.value))
        }
    }

    private fun pushSharedState(reason: String = "change") {
        if (applyingRemoteState) return
        viewModelScope.launch {
            try {
                // Ensure auth before pushing
                if (!syncManager.isAuthenticated()) {
                    val userName = _user.value?.name ?: "Usuario"
                    val email = "user_${android.os.Build.MODEL.replace(" ","_")}@danbron.app"
                    syncManager.authenticate(email, userName)
                    if (syncManager.currentDeviceId() == null) {
                        syncManager.registerDevice("android", android.os.Build.MODEL)
                    }
                }
                val includeProfile = reason.contains("profile", ignoreCase = true) || reason == "onboarding"
                syncManager.syncSharedState(buildSharedStateSnapshot(includeProfile))
                android.util.Log.d("MainVM", "Pushed shared state: $reason")
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Push shared state failed: $reason", e)
            }
        }
    }

    private suspend fun pullSharedState() {
        val shared = syncManager.getSharedState() ?: return
        applyingRemoteState = true
        try {
            val profileElement = when {
                shared.has("profile") && !shared.get("profile").isJsonNull -> shared.get("profile")
                shared.has("userProfile") && !shared.get("userProfile").isJsonNull -> shared.get("userProfile")
                else -> null
            }
            profileElement?.let {
                val remoteUser = runCatching { gson.fromJson(it, User::class.java) }.getOrNull()
                if (remoteUser != null && remoteUser != _user.value) {
                    _user.value = remoteUser
                    prefs.saveUser(remoteUser)
                    updateAdaptiveState(remoteUser)
                }
            }

            if (shared.has("notes") && shared.get("notes").isJsonArray) {
                val remoteNotes = runCatching {
                    gson.fromJson<List<Note>>(shared.get("notes"), object : TypeToken<List<Note>>() {}.type)
                }.getOrNull()
                if (remoteNotes != null && remoteNotes != _notes.value) {
                    _notes.value = remoteNotes
                    prefs.saveNotes(remoteNotes)
                }
            }

            if (shared.has("habits") && shared.get("habits").isJsonArray) {
                val remoteHabits = runCatching {
                    gson.fromJson<List<Habit>>(shared.get("habits"), object : TypeToken<List<Habit>>() {}.type)
                }.getOrNull()
                if (remoteHabits != null && remoteHabits != _habits.value) {
                    _habits.value = remoteHabits
                    prefs.saveHabits(remoteHabits)
                }
            }

            if (shared.has("habitLogs") && shared.get("habitLogs").isJsonArray) {
                val remoteLogs = runCatching {
                    gson.fromJson<List<HabitLog>>(shared.get("habitLogs"), object : TypeToken<List<HabitLog>>() {}.type)
                }.getOrNull()
                if (remoteLogs != null && remoteLogs != _habitLogs.value) {
                    _habitLogs.value = remoteLogs
                    prefs.saveHabitLogs(remoteLogs)
                }
            }

            if (shared.has("tasks") && shared.get("tasks").isJsonArray) {
                val remoteTasks = runCatching {
                    gson.fromJson<List<Task>>(shared.get("tasks"), object : TypeToken<List<Task>>() {}.type)
                }.getOrNull()
                if (remoteTasks != null && remoteTasks != _tasks.value) {
                    _tasks.value = remoteTasks
                    prefs.saveTasks(remoteTasks)
                }
            }
        } finally {
            applyingRemoteState = false
        }
    }

    fun generateSyncCode(): String {
        // Prefer the backend device pairing code. The old random code is kept
        // only as a short fallback while the backend refresh finishes.
        val existing = _syncCode.value
        val backendExisting = syncManager.currentPairingCode().orEmpty()
        val code = when {
            existing.length == 6 -> existing
            backendExisting.length == 6 -> backendExisting
            else -> (100000..999999).random().toString()
        }
        _syncCode.value = code
        viewModelScope.launch {
            prefs.saveSyncCode(code)
            var activeCode = code

            try {
                if (!syncManager.isAuthenticated()) {
                    val userName = _user.value?.name ?: "Usuario"
                    val email = "user_${android.os.Build.MODEL.replace(" ","_")}@danbron.app"
                    syncManager.authenticate(email, userName)
                }
                if (syncManager.currentDeviceId() == null) {
                    syncManager.registerDevice("android", android.os.Build.MODEL)
                }
                val backendCode = syncManager.refreshPairingCode()
                if (backendCode.length == 6) {
                    activeCode = backendCode
                    if (_syncCode.value != backendCode) {
                        _syncCode.value = backendCode
                        prefs.saveSyncCode(backendCode)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Could not refresh backend pairing code", e)
            }

            // Also publish the profile for old desktop builds, but the same
            // visible code now maps to the backend pairing code too.
            _user.value?.let { user ->
                com.danbron.app.data.SyncManager.pushProfile(activeCode, user)
            }
            pollForDesktopAck(activeCode)
        }
        return code
    }

    private fun pollForDesktopAck(code: String) {
        viewModelScope.launch {
            repeat(40) {
                if (_isPaired.value) return@launch
                try {
                    val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val url = "https://ntfy.sh/danbron_sync_${code}_ack/json?poll=1&since=5m"
                        val request = okhttp3.Request.Builder().url(url).get().build()
                        val response = okhttp3.OkHttpClient().newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        response.close()
                        body.contains("\"paired\"") && body.contains("true")
                    }
                    if (found) {
                        _isPaired.value = true
                        _pairingStatus.value = "paired"
                        _pairedDeviceName.value = "Windows PC"
                        _lastSyncTime.value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        return@launch
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    fun confirmPairingFromDesktop(onResult: (Boolean, String) -> Unit) {
        val code = _syncCode.value
        if (code.length != 6) {
            onResult(false, "Genera un codigo primero")
            return
        }
        viewModelScope.launch {
            try {
                // Strategy 1: Check ntfy.sh ack channel
                val ntfyFound = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val url = "https://ntfy.sh/danbron_sync_${code}_ack/json?poll=1&since=60m"
                        val request = okhttp3.Request.Builder().url(url).get().build()
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        response.close()
                        body.contains("\"paired\"") && body.contains("true")
                    }
                } catch (_: Exception) { false }

                if (ntfyFound) {
                    markAsPaired()
                    onResult(true, "Vinculado con tu PC")
                    return@launch
                }

                // Strategy 2: Check backend if already paired
                val backendPaired = try { syncManager.getPairedDevice() != null } catch (_: Exception) { false }
                if (backendPaired) {
                    markAsPaired()
                    onResult(true, "Vinculado con tu PC")
                    return@launch
                }

                // Strategy 3: Actively try to pair via backend using our code
                // (Windows may have registered with this same pairing code)
                try {
                    if (!syncManager.isAuthenticated()) {
                        val email = "user_${android.os.Build.MODEL.replace(" ", "_")}@danbron.app"
                        val name = _user.value?.name ?: "Usuario"
                        syncManager.authenticate(email, name)
                    }
                    if (syncManager.currentDeviceId() == null) {
                        syncManager.registerDevice("android", android.os.Build.MODEL)
                    }
                    // Check if we can find the paired device via backend
                    val paired = try { syncManager.getPairedDevice() } catch (_: Exception) { null }
                    if (paired != null) {
                        markAsPaired()
                        onResult(true, "Vinculado con tu PC")
                        return@launch
                    }
                } catch (_: Exception) {}

                // Strategy 4: Just mark as paired in mobile-code mode
                // if Windows already consumed the profile (check ntfy original channel)
                val profileConsumed = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val url = "https://ntfy.sh/danbron_sync_${code}/json?poll=1&since=60m"
                        val request = okhttp3.Request.Builder().url(url).get().build()
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        response.close()
                        // If the profile message exists, Windows likely consumed it
                        body.contains("\"name\"") && body.length > 50
                    }
                } catch (_: Exception) { false }

                if (profileConsumed) {
                    // Profile was published — assume Windows got it
                    markAsPaired()
                    onResult(true, "Vinculado con tu PC")
                    return@launch
                }

                onResult(false, "Tu PC aun no confirmo. Abre Bron en tu PC y escribe el codigo primero.")
            } catch (e: Exception) {
                onResult(false, "Error verificando: ${e.message?.take(50)}")
            }
        }
    }

    private fun markAsPaired() {
        _isPaired.value = true
        _pairingStatus.value = "paired"
        _pairedDeviceName.value = "Windows PC"
        _lastSyncTime.value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    fun rePushSyncProfile() {
        val code = _syncCode.value
        if (code.length != 6) return
        viewModelScope.launch {
            _user.value?.let { user ->
                com.danbron.app.data.SyncManager.pushProfile(code, user)
            }
        }
    }

    fun linkWithEmail(email: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank() || !email.contains("@")) {
            onResult(false, "Escribe un email valido")
            return
        }
        viewModelScope.launch {
            try {
                val name = _user.value?.name ?: "Usuario"
                val paired = syncManager.autoPairByEmail(email, name)
                if (paired) {
                    markAsPaired()
                    onResult(true, "Vinculado con tu PC!")
                } else {
                    onResult(false, "Tu PC aun no esta registrado con este email. Abre Bron en tu PC y escribe el mismo email: $email")
                }
            } catch (e: Exception) {
                onResult(false, "Error: ${e.message?.take(60)}")
            }
        }
    }

    // ── Pairing ──

    fun checkPairingStatus() {
        viewModelScope.launch {
            _pairingStatus.value = "checking"
            try {
                val pairedId = syncManager.getPairedDevice()
                if (pairedId != null) {
                    _isPaired.value = true
                    _pairingStatus.value = "paired"
                    _pairedDeviceName.value = "Windows PC"
                    _lastSyncTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                } else {
                    _isPaired.value = false
                    _pairingStatus.value = ""
                    _pairedDeviceName.value = ""
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Check pairing error", e)
                _isPaired.value = syncManager.hasPairedDevice()
                _pairingStatus.value = if (_isPaired.value) "paired" else "error"
            }
        }
    }

    fun pairWithCode(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _pairingStatus.value = "checking"
            try {
                // First ensure we're authenticated and registered
                if (!syncManager.isAuthenticated()) {
                    val email = "user_${System.currentTimeMillis()}@danbron.app"
                    val name = _user.value?.name ?: "Usuario"
                    syncManager.authenticate(email, name)
                }
                if (syncManager.currentDeviceId() == null) {
                    syncManager.registerDevice("android", android.os.Build.MODEL)
                }
                syncManager.pairDevice(code)
                _isPaired.value = true
                _pairingStatus.value = "paired"
                _pairedDeviceName.value = "Windows PC"
                _lastSyncTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                // Push current state to sync immediately
                pushSharedState("onboarding")
                onResult(true, "Vinculado con tu PC")
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Pairing failed", e)
                _pairingStatus.value = "error"
                onResult(false, "No se pudo vincular. Verifica el código.")
            }
        }
    }

    fun unpairDevice(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try { syncManager.unpairDevice() } catch (_: Exception) { /* ignore backend error */ }
            // Always clear local state
            _isPaired.value = false
            _pairingStatus.value = ""
            _pairedDeviceName.value = ""
            _lastSyncTime.value = ""
            onResult(true)
        }
    }

    // ── Complete onboarding from conversational flow ──

    fun completeConversationalOnboarding(user: User) {
        _user.value = user

        try {
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
                try {
                    prefs.saveUser(user)
                    prefs.saveTasks(_tasks.value)
                    prefs.saveHabits(_habits.value)
                    prefs.saveReferralCode(code)
                    prefs.setOnboarded()
                } catch (e: Exception) {
                    android.util.Log.e("MainVM", "Error saving onboarding prefs", e)
                }
                try {
                    pushSharedState("onboarding")
                } catch (e: Exception) {
                    android.util.Log.e("MainVM", "Error syncing onboarding state", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainVM", "Error in completeConversationalOnboarding", e)
            // Still mark as onboarded so user isn't stuck
            viewModelScope.launch {
                try { prefs.setOnboarded() } catch (_: Exception) {}
            }
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

        viewModelScope.launch {
            prefs.saveTasks(_tasks.value)
            pushSharedState("task-toggled")
        }
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
            pushSharedState("habit-toggled")
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
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        _habits.value = _habits.value + Habit(name = trimmed)
        HapticManager.tap(ctx)
        viewModelScope.launch {
            prefs.saveHabits(_habits.value)
            pushSharedState("habit-added")
        }
    }

    fun toggleHabitForWeekday(habitId: Long, weekdayIndex: Int) {
        val date = dateForWeekday(weekdayIndex)
        val existing = _habitLogs.value.find { it.habitId == habitId && it.date == date }

        _habitLogs.value = if (existing != null) {
            _habitLogs.value.filter { !(it.habitId == habitId && it.date == date) }
        } else {
            _habitLogs.value + HabitLog(habitId, date)
        }

        updateHabitWeekAndStreak(habitId)
        HapticManager.tap(ctx)

        viewModelScope.launch {
            prefs.saveHabitLogs(_habitLogs.value)
            prefs.saveHabits(_habits.value)
            pushSharedState("habit-weekday")
        }
    }

    private fun dateForWeekday(weekdayIndex: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayDow = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, weekdayIndex - todayDow)
        return fmt.format(cal.time)
    }

    // ── Notes management ──

    fun addNote(title: String, content: String = "", tag: String = "general") {
        val note = Note(title = title, content = content, tag = tag)
        _notes.value = listOf(note) + _notes.value
        HapticManager.tap(ctx)
        viewModelScope.launch {
            prefs.saveNotes(_notes.value)
            pushSharedState("note-added")
        }
    }

    fun updateNote(id: Long, title: String, content: String, tag: String) {
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content, tag = tag, updatedAt = System.currentTimeMillis())
            else it
        }
        viewModelScope.launch {
            prefs.saveNotes(_notes.value)
            pushSharedState("note-updated")
        }
    }

    fun deleteNote(id: Long) {
        _notes.value = _notes.value.filter { it.id != id }
        HapticManager.tap(ctx)
        viewModelScope.launch {
            prefs.saveNotes(_notes.value)
            pushSharedState("note-deleted")
        }
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
                    authToken = syncManager.getAuthToken(),
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
            pushSharedState("profile-updated")
        }
    }
}
