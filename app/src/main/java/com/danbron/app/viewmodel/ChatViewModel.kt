package com.danbron.app.viewmodel

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danbron.app.api.*
import com.danbron.app.data.AudioRecorderManager
import com.danbron.app.data.SpeechRecognitionManager
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.VoiceManager
import com.danbron.app.data.models.*
import com.danbron.app.engine.AdaptiveEngine
import com.danbron.app.sync.DanbronSyncManager
import com.danbron.app.system.CommandExecutor
import com.danbron.app.system.AppTrackerAccessibilityService
import com.danbron.app.system.AndroidScreenContextStore
import com.danbron.app.system.NotificationStore
import com.danbron.app.system.SmartHomeManager
import com.danbron.app.system.DeviceController
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)
    private val syncManager = DanbronSyncManager(app)
    private val commandExecutor = CommandExecutor(app)
    private val smartHome = SmartHomeManager(app)
    private val deviceCtrl = DeviceController(app)
    private val gson = Gson()
    val voiceManager = VoiceManager(app)
    val recorderManager = AudioRecorderManager(app)
    val speechRecognizer = SpeechRecognitionManager(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private var apiKey = ""
    private var user: User? = null
    private var notesCache: List<Note> = emptyList()
    private var habitsCache: List<Habit> = emptyList()
    private var habitLogsCache: List<HabitLog> = emptyList()
    private var loaded = false
    private var applyingRemoteState = false

    init {
        viewModelScope.launch { prefs.apiKey.collect { apiKey = it } }
        viewModelScope.launch { prefs.user.collect { user = it } }
        viewModelScope.launch { prefs.notes.collect { notesCache = it } }
        viewModelScope.launch { prefs.habits.collect { habitsCache = it } }
        viewModelScope.launch { prefs.habitLogs.collect { habitLogsCache = it } }
        viewModelScope.launch {
            prefs.chatMessages.collect { saved ->
                if (!loaded && saved.isNotEmpty()) {
                    _messages.value = saved
                    loaded = true
                }
            }
        }
        startSharedChatSyncLoop()
    }

    fun initWelcome() {
        if (_messages.value.isEmpty()) {
            val u = user
            val name = u?.name ?: "amigo"
            val segment = u?.let { AdaptiveEngine.classifyUser(it) }

            val welcomeMsg = when (segment?.segment) {
                LifeSegment.UNEMPLOYED_SEEKING ->
                    "Hola $name. Soy Bron. Estoy aqui para ayudarte a encontrar empleo y mantener tu animo alto. Cuentame, como va la busqueda?"
                LifeSegment.PARENT_BUSY ->
                    "Hola $name. Soy Bron. Se que tu tiempo es oro cuando tienes familia. Estoy aqui para ayudarte a optimizar cada minuto. En que te puedo ayudar hoy?"
                LifeSegment.YOUNG_DEBT ->
                    "Hola $name. Soy Bron. Vamos a destruir esa deuda juntos. Cada peso cuenta y yo estoy aqui para guiarte. Que necesitas hoy?"
                LifeSegment.HEALTH_FOCUSED ->
                    "Hola $name. Soy Bron. Tu salud es la base de todo. Estoy aqui para acompanarte en cada paso. Como te sientes hoy?"
                LifeSegment.STUDENT_BROKE ->
                    "Hola $name. Soy Bron. Estudiar y manejar la plata no es facil, pero juntos lo hacemos. Que necesitas?"
                else ->
                    "Hola $name. Soy Bron. Estoy aqui para ayudarte a tomar las decisiones correctas cada dia. En que te puedo ayudar hoy?"
            }

            _messages.value = listOf(ChatMessage("assistant", welcomeMsg))
            voiceManager.speak(welcomeMsg)
            persistMessages()
        }
    }

    fun toggleVoice(enabled: Boolean) {
        voiceManager.isEnabled = enabled
        if (!enabled) voiceManager.stop()
    }

    fun startRecording() {
        voiceManager.stop()
        _isRecording.value = true

        // Use native SpeechRecognizer (no API key needed)
        if (speechRecognizer.isAvailable) {
            speechRecognizer.startListening(
                onResultCallback = { recognizedText ->
                    _isRecording.value = false
                    if (recognizedText.isNotBlank()) {
                        sendMessageInternal(recognizedText)
                    }
                },
                onErrorCallback = { errorMsg ->
                    _isRecording.value = false
                    _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
                    voiceManager.speak(errorMsg)
                }
            )
        } else {
            // Fallback to audio file recording (requires API key)
            if (apiKey.isBlank()) {
                _isRecording.value = false
                _messages.value = _messages.value + ChatMessage("assistant", "El reconocimiento de voz no está disponible. Escríbeme en su lugar.")
                voiceManager.speak("No puedo escucharte. Escríbeme.")
                return
            }
            recorderManager.startRecording()
        }
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) return

        // If using native recognizer, just stop it (result comes via callback)
        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            _isRecording.value = false
            return
        }

        // Fallback: audio file transcription via API
        _isRecording.value = false
        val audioFile = recorderManager.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() < 500) {
            _messages.value = _messages.value + ChatMessage("assistant", "No te escuché bien. Toca el micrófono, habla, y toca de nuevo para enviar.")
            voiceManager.speak("No te escuché. Intenta de nuevo.")
            return
        }
        _isTyping.value = true
        viewModelScope.launch {
            try {
                val text = AiProvider.transcribeAudio(apiKey, audioFile, syncManager.getAuthToken())
                audioFile.delete()
                if (text.isNotBlank()) {
                    sendMessageInternal(text)
                } else {
                    _messages.value = _messages.value + ChatMessage("assistant", "No entendí lo que dijiste. ¿Puedes repetirlo?")
                    voiceManager.speak("No entendí, repítelo por favor.")
                    _isTyping.value = false
                }
            } catch (_: Exception) {
                _messages.value = _messages.value + ChatMessage("assistant", "Tuve un problema con el audio. Intenta de nuevo o escríbeme.")
                voiceManager.speak("Tuve un problema. Intenta de nuevo.")
                _isTyping.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return
        sendMessageInternal(text)
    }

    private fun sendMessageInternal(text: String) {
        val u = user
        if (u == null) {
            _messages.value = _messages.value + ChatMessage("user", text)
            val errorMsg = "Necesito que completes tu perfil primero para poder ayudarte. Ve a tu perfil y llena tus datos."
            _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
            voiceManager.speak(errorMsg)
            persistMessages()
            return
        }

        if (apiKey.isBlank()) {
            _messages.value = _messages.value + ChatMessage("user", text)
            val errorMsg = "Falta configurar la conexion. Ve a Ajustes y asegurate de tener una clave API."
            _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
            voiceManager.speak(errorMsg)
            persistMessages()
            return
        }

        _messages.value = _messages.value + ChatMessage("user", text)
        persistMessages()

        // Only intercept direct app data mutations (notes, habits, profile)
        val appCommand = parseDanbronAppCommand(text)
        if (appCommand != null) {
            _isTyping.value = true
            viewModelScope.launch {
                val reply = runCatching { executeDanbronAppCommand(appCommand) }
                    .getOrElse { error -> "No pude cambiar la app: ${error.message}" }
                _messages.value = _messages.value + ChatMessage("assistant", reply)
                voiceManager.speak(reply)
                persistMessages()
                _isTyping.value = false
            }
            return
        }

        // Everything else goes through the agentic AI
        _isTyping.value = true
        viewModelScope.launch {
            try {
                val reply = androidAgenticLoop(u, text)
                _messages.value = _messages.value + ChatMessage("assistant", reply)
                voiceManager.speak(reply)
                persistMessages()
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "AI call failed", e)
                val detail = e.message ?: e.javaClass.simpleName
                val errorMsg = "Error: $detail"
                _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
                voiceManager.speak(errorMsg)
                persistMessages()
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * Android Agentic Loop: AI can read screen, execute commands, and iterate.
     * Similar to the desktop version but adapted for Android capabilities.
     */
    private suspend fun androidAgenticLoop(u: User, userText: String): String {
        val segment = AdaptiveEngine.classifyUser(u)
        val screenContext = buildScreenContextForAI()
        val deviceContext = buildDeviceContextForAI()

        val systemPrompt = """Eres Bron, amigo personal del usuario. Hablas español latino casual, con calidez y humor. Respuestas cortas (3-5 oraciones).

USUARIO: ${u.name}, Trabajo: ${u.employmentStatus}, Meta: ${u.goal}
DISPOSITIVO: Android
$deviceContext
$screenContext

TIENES HERRAMIENTAS REALES EN ANDROID. Usa estos marcadores:

[TOOL:APP]nombre[/TOOL] → Abrir app
[TOOL:URL]https://ejemplo.com[/TOOL] → Abrir URL
[TOOL:WHATSAPP]contacto|mensaje[/TOOL] → Enviar WhatsApp automatico
[TOOL:LLAMAR]numero_o_nombre[/TOOL] → Llamar (si das nombre busco el numero en contactos)
[TOOL:SMS]numero_o_nombre|mensaje[/TOOL] → Enviar SMS
[TOOL:CONTACTO]nombre[/TOOL] → Buscar contacto por nombre y obtener su numero
[TOOL:FOTO][/TOOL] → Abrir camara
[TOOL:NOTIFICACIONES][/TOOL] → Leer notificaciones de todas las apps
[TOOL:BUSCAR]consulta[/TOOL] → Buscar en Google
[TOOL:LEER_PANTALLA][/TOOL] → Leer pantalla AHORA (captura fresca)
[TOOL:TAP]texto[/TOOL] → Tocar boton o elemento
[TOOL:SCROLL][/TOOL] → Scroll abajo
[TOOL:ALARMA]HH:MM|etiqueta[/TOOL] → Poner alarma
[TOOL:TIMER]segundos|etiqueta[/TOOL] → Poner timer
[TOOL:VOLUMEN]porcentaje[/TOOL] → Ajustar volumen (0-100)
[TOOL:LINTERNA]on/off[/TOOL] → Encender/apagar linterna
[TOOL:SILENCIO]modo[/TOOL] → Silencio, vibrar, o normal
[TOOL:UBICACION][/TOOL] → Obtener ubicacion GPS actual
[TOOL:CALENDARIO][/TOOL] → Leer eventos del calendario
[TOOL:LLAMADAS_RECIENTES][/TOOL] → Ver historial de llamadas
[TOOL:INFO_DISPOSITIVO][/TOOL] → Bateria, almacenamiento, red, modelo
[TOOL:COPIAR]texto[/TOOL] → Copiar texto al portapapeles
[TOOL:PORTAPAPELES][/TOOL] → Leer contenido del portapapeles
[TOOL:COMPARTIR]texto[/TOOL] → Compartir texto con cualquier app
[TOOL:AJUSTES]seccion[/TOOL] → Abrir ajustes (wifi, bluetooth, sonido, pantalla, etc)
[TOOL:CASA]accion|entity_id[/TOOL] → Smart Home (luces, enchufes, TV, cerraduras)
[TOOL:PC]{"action":"...","args":{}}[/TOOL] → Enviar orden al PC

EJEMPLOS:
- "manda te amo a mi novia" → [TOOL:WHATSAPP]Mi Novia|te amo[/TOOL]
- "llama a mama" → [TOOL:LLAMAR]mama[/TOOL] (busco el numero automaticamente)
- "mandale sms a Juan diciendo que ya voy" → [TOOL:SMS]Juan|ya voy[/TOOL]
- "sacame foto" → [TOOL:FOTO][/TOOL]
- "quien me escribio" → [TOOL:NOTIFICACIONES][/TOOL]
- "que me respondio mi novia en wsp" → [TOOL:APP]whatsapp[/TOOL] + [TOOL:LEER_PANTALLA][/TOOL]
- "cuanto gane en uber" → [TOOL:APP]uber[/TOOL] + [TOOL:LEER_PANTALLA][/TOOL]
- "ponme alarma a las 7" → [TOOL:ALARMA]07:00|Despertar[/TOOL]
- "timer de 5 minutos" → [TOOL:TIMER]300|Timer[/TOOL]
- "baja el volumen" → [TOOL:VOLUMEN]30[/TOOL]
- "prende la linterna" → [TOOL:LINTERNA]on[/TOOL]
- "silencia el telefono" → [TOOL:SILENCIO]silencio[/TOOL]
- "donde estoy" → [TOOL:UBICACION][/TOOL]
- "que tengo en el calendario" → [TOOL:CALENDARIO][/TOOL]
- "cuanta bateria tengo" → [TOOL:INFO_DISPOSITIVO][/TOOL]
- "quien me llamo" → [TOOL:LLAMADAS_RECIENTES][/TOOL]
- "prende la luz de la sala" → [TOOL:CASA]encender|light.sala[/TOOL]
- "abre ajustes de wifi" → [TOOL:AJUSTES]wifi[/TOOL]
- "copia esto: hola mundo" → [TOOL:COPIAR]hola mundo[/TOOL]

FLUJO MULTI-PASO: Si necesitas abrir una app Y leer datos:
1. [TOOL:APP] para abrir  2. [TOOL:LEER_PANTALLA] para ver  3. [TOOL:TAP]/[TOOL:SCROLL] para navegar

REGLAS:
- Si piden llamar/sms por NOMBRE, busca el numero en contactos automaticamente
- SIEMPRE intenta. Nunca digas "no puedo".
- Habla como amigo, nunca como robot.
- Nunca menciones: API, backend, accesibilidad, servicio, token, modelo, ni nada tecnico."""

        val apiMessages = _messages.value.takeLast(10).map { ApiMessage(it.role, it.content) }
        val messages = mutableListOf<ApiMessage>()
        messages.addAll(apiMessages)

        var iterations = 0
        val maxIterations = 4
        val needsDeep = !shouldUseTools(userText) && detectDeepThinkingNeed(userText)

        while (iterations < maxIterations) {
            iterations++
            // Use MoA deep thinking for the first call on complex non-tool queries
            val reply = if (iterations == 1 && needsDeep) {
                try {
                    AiProvider.sendDeepChat(
                        apiKey = apiKey,
                        authToken = syncManager.getAuthToken(),
                        systemPrompt = systemPrompt,
                        messages = messages,
                        maxTokens = 800
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ChatVM", "DeepThink failed, falling back: ${e.message}")
                    AiProvider.sendChat(
                        apiKey = apiKey,
                        authToken = syncManager.getAuthToken(),
                        systemPrompt = systemPrompt,
                        messages = messages,
                        maxTokens = 800
                    )
                }
            } else {
                AiProvider.sendChat(
                    apiKey = apiKey,
                    authToken = syncManager.getAuthToken(),
                    systemPrompt = systemPrompt,
                    messages = messages,
                    maxTokens = 800
                )
            }

            val toolCalls = parseAndroidToolCalls(reply)
            if (toolCalls.isEmpty()) {
                // Check if AI should have used tools but didn't
                if (iterations == 1 && shouldUseTools(userText)) {
                    messages.add(ApiMessage("assistant", reply))
                    messages.add(ApiMessage("user", "[SISTEMA]: Debes usar herramientas. El usuario pidio algo que requiere accion. Usa [TOOL:LEER_PANTALLA][/TOOL] o [TOOL:APP]nombre[/TOOL] segun corresponda."))
                    continue
                }
                return cleanAndroidToolMarkers(reply)
            }

            // Execute tools
            val results = mutableListOf<String>()
            for (tool in toolCalls) {
                val result = executeAndroidTool(tool)
                results.add("[RESULTADO ${tool.first}]: $result")
            }

            messages.add(ApiMessage("assistant", reply))
            messages.add(ApiMessage("user", "[SISTEMA - resultados de tus herramientas]:\n${results.joinToString("\n\n")}\n\nResponde al usuario con los datos reales. Si necesitas mas, usa herramientas de nuevo."))
        }

        return "Hice lo que pude pero necesito mas contexto. Preguntame de nuevo."
    }

    private fun buildScreenContextForAI(): String {
        val snapshot = AndroidScreenContextStore.latestSnapshot() ?: return "\n[PANTALLA]: No hay captura reciente. El usuario debe abrir la app que quiere analizar."
        val ageSeconds = ((System.currentTimeMillis() - snapshot.timestamp) / 1000).coerceAtLeast(0)
        if (ageSeconds > 60) return "\n[PANTALLA]: Ultima captura hace ${ageSeconds}s (muy antigua). Pide al usuario que abra la app."

        val visibleText = snapshot.nodes
            .mapNotNull { it.text.ifBlank { it.description }.takeIf(String::isNotBlank) }
            .distinct()
            .take(50)
            .joinToString("\n")

        return "\n[PANTALLA ACTUAL - app: ${snapshot.packageName}, hace ${ageSeconds}s]:\n$visibleText"
    }

    private fun buildDeviceContextForAI(): String {
        val app = getApplication<Application>()
        val accessibilityOk = isAccessibilityServiceEnabled(app)
        val pcLinked = syncManager.hasPairedDevice()
        return buildString {
            if (accessibilityOk) append("ACCESIBILIDAD: Activa (puedo leer pantalla de cualquier app)\n")
            else append("ACCESIBILIDAD: NO activa (no puedo leer otras apps)\n")
            if (pcLinked) append("PC: Vinculado (puedo enviar ordenes al computador)\n")
            else append("PC: No vinculado\n")
        }
    }

    private fun shouldUseTools(text: String): Boolean {
        val t = normalizeQuestion(text)
        // Direct tool triggers — these always mean "use a tool"
        val directTriggers = listOf(
            "llama a", "llamar a", "llamale", "marcale",
            "manda", "envia", "enviame", "mandame", "dile",
            "que respondio", "que me respondio", "que dice", "quien me escribio", "quien escribio",
            "sacame foto", "toma foto", "foto", "camara",
            "alarma", "timer", "temporizador", "recordatorio",
            "linterna", "flash",
            "volumen", "sube volumen", "baja volumen", "silencio", "vibrar",
            "donde estoy", "ubicacion", "gps", "mi ubicacion",
            "calendario", "eventos", "agenda",
            "bateria", "almacenamiento", "cuanta bateria",
            "quien me llamo", "llamadas recientes", "historial de llamadas",
            "notificaciones", "quien me notifico",
            "copia", "copiar", "portapapeles", "clipboard",
            "comparte", "compartir",
            "ajustes de", "configuracion de", "wifi", "bluetooth",
            "prende la luz", "apaga la luz", "smart home"
        )
        if (directTriggers.any { t.contains(it) }) return true
        // Action + target pattern
        val actionWords = listOf("abre", "abrir", "muestra", "dime", "revisa", "mira", "accede", "entra", "busca", "pon")
        val targetWords = listOf("uber", "whatsapp", "wsp", "youtube", "spotify", "instagram", "pantalla", "app", "aplicacion", "ganancia", "ingreso", "mensaje", "correo", "gmail", "pareja", "novia", "novio", "mama", "papa", "hermano", "amigo", "contacto", "chat")
        return actionWords.any { t.contains(it) } && targetWords.any { t.contains(it) }
    }

    private fun detectDeepThinkingNeed(text: String): Boolean {
        val t = normalizeQuestion(text)
        if (t.length < 20) return false
        // Explicit triggers
        if (Regex("(piensa bien|analiza|razona|deep think|piensalo|reflexiona)").containsMatchIn(t)) return true
        // Complex reasoning
        val reasoning = Regex("(por que|como funciona|explica|explicame|diferencia entre|compara|ventaja|desventaja|pros y contras|que opinas|que piensas|que me recomiendas|como puedo|deberia|conviene|mejor opcion|estrategia|plan para|ayudame a decidir|analisis|evalua)").containsMatchIn(t)
        // Math / code
        val mathCode = Regex("(calcula|formula|ecuacion|algoritmo|programar?|codigo|funcion|variable|porcentaje|interes compuesto|proyeccion|estimacion|cuanto.*si.*entonces|si.*ahorro|si.*gano|si.*invierto)").containsMatchIn(t)
        // Long complex questions (40+ words)
        val isLong = text.trim().split(Regex("\\s+")).size >= 40
        // Planning
        val planning = Regex("(plan |planifica|organiza mi|haz un plan|como organizo|paso a paso|guia para|tutorial)").containsMatchIn(t)
        return reasoning || mathCode || isLong || planning
    }

    private data class AndroidToolCall(val first: String, val second: String)

    private fun parseAndroidToolCalls(reply: String): List<AndroidToolCall> {
        val pattern = Regex("\\[TOOL:(\\w+)](.*?)\\[/TOOL]", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(reply).map { match ->
            AndroidToolCall(match.groupValues[1], match.groupValues[2].trim())
        }.toList()
    }

    private suspend fun executeAndroidTool(tool: AndroidToolCall): String {
        return when (tool.first.uppercase()) {
            "APP" -> {
                val payload = JsonObject().apply {
                    addProperty("id", "ai_${System.currentTimeMillis()}")
                    addProperty("target", "android")
                    addProperty("action", "open_app")
                    add("args", JsonObject().apply { addProperty("app", tool.second) })
                }
                runCatching { commandExecutor.execute(payload) }.getOrElse { "Error abriendo ${tool.second}: ${it.message}" }
            }
            "URL" -> {
                val payload = JsonObject().apply {
                    addProperty("id", "ai_${System.currentTimeMillis()}")
                    addProperty("target", "android")
                    addProperty("action", "open_url")
                    add("args", JsonObject().apply { addProperty("url", tool.second) })
                }
                runCatching { commandExecutor.execute(payload) }.getOrElse { "Error abriendo URL: ${it.message}" }
            }
            "WHATSAPP" -> {
                val parts = tool.second.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    // New format: contact|message → full auto-send
                    val service = AppTrackerAccessibilityService.instance
                    if (service != null) {
                        runCatching {
                            service.sendWhatsAppMessage(getApplication(), parts[0].trim(), parts[1].trim())
                        }.getOrElse { "Error enviando WhatsApp: ${it.message}" }
                    } else {
                        // Fallback to compose if no accessibility
                        val payload = JsonObject().apply {
                            addProperty("id", "ai_${System.currentTimeMillis()}")
                            addProperty("target", "android")
                            addProperty("action", "compose_whatsapp")
                            add("args", JsonObject().apply { addProperty("message", parts[1].trim()) })
                        }
                        runCatching { commandExecutor.execute(payload) }.getOrElse { "Error con WhatsApp: ${it.message}" }
                    }
                } else {
                    // Old format: just message → compose only
                    val payload = JsonObject().apply {
                        addProperty("id", "ai_${System.currentTimeMillis()}")
                        addProperty("target", "android")
                        addProperty("action", "compose_whatsapp")
                        add("args", JsonObject().apply { addProperty("message", tool.second) })
                    }
                    runCatching { commandExecutor.execute(payload) }.getOrElse { "Error con WhatsApp: ${it.message}" }
                }
            }
            "BUSCAR" -> {
                val query = java.net.URLEncoder.encode(tool.second, "UTF-8")
                val payload = JsonObject().apply {
                    addProperty("id", "ai_${System.currentTimeMillis()}")
                    addProperty("target", "android")
                    addProperty("action", "open_url")
                    add("args", JsonObject().apply { addProperty("url", "https://www.google.com/search?q=$query") })
                }
                runCatching { commandExecutor.execute(payload) }.getOrElse { "Error buscando: ${it.message}" }
            }
            "LEER_PANTALLA" -> {
                // Force a fresh snapshot via AccessibilityService
                val service = AppTrackerAccessibilityService.instance
                if (service != null) {
                    val freshText = service.forceSnapshot()
                    if (!freshText.isNullOrBlank()) {
                        val snap = AndroidScreenContextStore.latestSnapshot()
                        "App: ${snap?.packageName ?: "desconocida"} (captura fresca)\nTexto visible:\n$freshText"
                    } else {
                        "La pantalla esta vacia o no se pudo leer. Intenta abrir la app primero."
                    }
                } else {
                    // Fallback to cached snapshot
                    val snapshot = AndroidScreenContextStore.latestSnapshot()
                        ?: return "No hay captura de pantalla. Pide al usuario que habilite los Superpoderes de Bron en ajustes."
                    val ageSeconds = ((System.currentTimeMillis() - snapshot.timestamp) / 1000)
                    val text = snapshot.nodes
                        .mapNotNull { it.text.ifBlank { it.description }.takeIf(String::isNotBlank) }
                        .distinct()
                        .take(60)
                        .joinToString("\n")
                    "App: ${snapshot.packageName} (hace ${ageSeconds}s)\nTexto visible:\n${text.ifBlank { "No se detecto texto legible." }}"
                }
            }
            "TAP" -> {
                val service = AppTrackerAccessibilityService.instance
                    ?: return "Servicio de accesibilidad no activo. El usuario debe habilitarlo."
                val tapped = service.tapNodeByText(tool.second)
                if (tapped) "Toque el elemento '${tool.second}' exitosamente."
                else "No encontre un elemento visible con texto '${tool.second}' para tocar."
            }
            "SCROLL" -> {
                val service = AppTrackerAccessibilityService.instance
                    ?: return "Servicio de accesibilidad no activo."
                val scrolled = service.scrollDown()
                if (scrolled) "Hice scroll hacia abajo."
                else "No pude hacer scroll (no hay vista scrollable visible)."
            }
            "LLAMAR" -> {
                var number = tool.second.trim()
                // If it doesn't look like a phone number, resolve from contacts
                if (!number.matches(Regex("^[+\\d][\\d\\s\\-()]+$"))) {
                    val lookup = deviceCtrl.lookupContact(number)
                    if (lookup.startsWith("FOUND:")) {
                        val parts = lookup.removePrefix("FOUND:").split("|", limit = 2)
                        number = parts.getOrElse(1) { number }
                    } else if (lookup.startsWith("MULTIPLE:")) {
                        return lookup.removePrefix("MULTIPLE:")
                    } else {
                        return lookup
                    }
                }
                val payload = JsonObject().apply {
                    addProperty("id", "ai_${System.currentTimeMillis()}")
                    addProperty("target", "android")
                    addProperty("action", "make_call")
                    add("args", JsonObject().apply { addProperty("number", number) })
                }
                runCatching { commandExecutor.execute(payload) }.getOrElse { "Error llamando: ${it.message}" }
            }
            "SMS" -> {
                val parts = tool.second.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    var number = parts[0].trim()
                    // If it doesn't look like a phone number, resolve from contacts
                    if (!number.matches(Regex("^[+\\d][\\d\\s\\-()]+$"))) {
                        val lookup = deviceCtrl.lookupContact(number)
                        if (lookup.startsWith("FOUND:")) {
                            val lparts = lookup.removePrefix("FOUND:").split("|", limit = 2)
                            number = lparts.getOrElse(1) { number }
                        } else if (lookup.startsWith("MULTIPLE:")) {
                            return lookup.removePrefix("MULTIPLE:")
                        } else {
                            return lookup
                        }
                    }
                    val payload = JsonObject().apply {
                        addProperty("id", "ai_${System.currentTimeMillis()}")
                        addProperty("target", "android")
                        addProperty("action", "send_sms")
                        add("args", JsonObject().apply {
                            addProperty("number", number)
                            addProperty("message", parts[1].trim())
                        })
                    }
                    runCatching { commandExecutor.execute(payload) }.getOrElse { "Error enviando SMS: ${it.message}" }
                } else {
                    "Formato incorrecto. Usa: numero|mensaje"
                }
            }
            "CONTACTO" -> {
                val result = deviceCtrl.lookupContact(tool.second.trim())
                when {
                    result.startsWith("FOUND:") -> {
                        val p = result.removePrefix("FOUND:").split("|", limit = 2)
                        "Contacto encontrado: ${p[0]} — Numero: ${p.getOrElse(1) { "?" }}"
                    }
                    result.startsWith("MULTIPLE:") -> result.removePrefix("MULTIPLE:")
                    else -> result
                }
            }
            "FOTO" -> {
                val payload = JsonObject().apply {
                    addProperty("id", "ai_${System.currentTimeMillis()}")
                    addProperty("target", "android")
                    addProperty("action", "take_photo")
                    add("args", JsonObject())
                }
                runCatching { commandExecutor.execute(payload) }.getOrElse { "Error abriendo camara: ${it.message}" }
            }
            "NOTIFICACIONES" -> {
                val summary = NotificationStore.summary()
                summary.ifBlank { "No hay notificaciones recientes. El usuario debe habilitar el acceso a notificaciones en Ajustes > Notificaciones." }
            }
            "ALARMA" -> {
                val parts = tool.second.split("|", limit = 2)
                val timePart = parts[0].trim()
                val label = parts.getOrElse(1) { "" }.trim()
                val timeParts = timePart.split(":")
                val hour = timeParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
                val minute = timeParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                deviceCtrl.setAlarm(hour, minute, label)
            }
            "TIMER" -> {
                val parts = tool.second.split("|", limit = 2)
                val seconds = parts[0].trim().toIntOrNull() ?: 60
                val label = parts.getOrElse(1) { "" }.trim()
                deviceCtrl.setTimer(seconds, label)
            }
            "VOLUMEN" -> {
                val percent = tool.second.trim().toIntOrNull() ?: 50
                deviceCtrl.setVolume(percent)
            }
            "LINTERNA" -> {
                val on = tool.second.trim().lowercase() in listOf("on", "encender", "prender", "si", "1")
                deviceCtrl.toggleFlashlight(on)
            }
            "SILENCIO" -> {
                deviceCtrl.setRingerMode(tool.second.trim())
            }
            "UBICACION" -> {
                deviceCtrl.getLocation()
            }
            "CALENDARIO" -> {
                deviceCtrl.readCalendarEvents()
            }
            "LLAMADAS_RECIENTES" -> {
                deviceCtrl.readCallLog()
            }
            "INFO_DISPOSITIVO" -> {
                deviceCtrl.getDeviceInfo()
            }
            "COPIAR" -> {
                deviceCtrl.copyToClipboard(tool.second.trim())
            }
            "PORTAPAPELES" -> {
                deviceCtrl.readClipboard()
            }
            "COMPARTIR" -> {
                deviceCtrl.shareText(tool.second.trim())
            }
            "AJUSTES" -> {
                deviceCtrl.openSettings(tool.second.trim())
            }
            "CASA" -> {
                val parts = tool.second.split("|", limit = 2)
                val action = parts.getOrElse(0) { "" }.trim()
                val entityId = parts.getOrElse(1) { "" }.trim()
                runCatching { smartHome.executeAction(action, entityId) }.getOrElse { "Error con Smart Home: ${it.message}" }
            }
            "PC" -> {
                if (!syncManager.hasPairedDevice()) return "PC no vinculado."
                runCatching {
                    val cmdJson = gson.fromJson(tool.second, JsonObject::class.java)
                    val action = cmdJson.get("action")?.asString ?: "open_url"
                    val args = cmdJson.getAsJsonObject("args") ?: JsonObject()
                    syncManager.sendRemoteCommand(target = "windows", action = action, args = args)
                    "Orden enviada al PC."
                }.getOrElse { "Error enviando al PC: ${it.message}" }
            }
            else -> "Herramienta '${tool.first}' no reconocida."
        }
    }

    private fun cleanAndroidToolMarkers(text: String): String {
        return text.replace(Regex("\\[TOOL:\\w+].*?\\[/TOOL]", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
        speechRecognizer.destroy()
    }

    fun clearChat() {
        _messages.value = emptyList()
        loaded = false
        viewModelScope.launch {
            prefs.saveChatMessages(emptyList())
            pushSharedState("chat-cleared")
        }
    }

    private fun persistMessages() {
        viewModelScope.launch {
            prefs.saveChatMessages(_messages.value.takeLast(50))
            pushSharedState("chat")
        }
    }

    private fun startSharedChatSyncLoop() {
        viewModelScope.launch {
            delay(3000)
            while (true) {
                pullSharedChatState()
                delay(7000)
            }
        }
    }

    private fun buildSharedStateSnapshot(includeProfile: Boolean = false): JsonObject {
        return JsonObject().apply {
            addProperty("schemaVersion", 2)
            addProperty("source", "android-chat")
            addProperty("updatedAt", System.currentTimeMillis())
            if (includeProfile) user?.let {
                add("profile", gson.toJsonTree(it))
                add("userProfile", gson.toJsonTree(it))
                addProperty("profileUpdatedAt", System.currentTimeMillis())
            }
            add("notes", gson.toJsonTree(notesCache))
            add("habits", gson.toJsonTree(habitsCache))
            add("habitLogs", gson.toJsonTree(habitLogsCache))
            add("chatMessages", gson.toJsonTree(_messages.value.takeLast(80)))
            add("messages", gson.toJsonTree(_messages.value.takeLast(80)))
        }
    }

    private fun pushSharedState(reason: String = "chat") {
        if (applyingRemoteState) return
        viewModelScope.launch {
            val includeProfile = reason.contains("profile", ignoreCase = true)
            syncManager.syncSharedState(buildSharedStateSnapshot(includeProfile))
        }
    }

    private suspend fun pullSharedChatState() {
        val shared = syncManager.getSharedState() ?: return
        applyingRemoteState = true
        try {
            if (shared.has("chatMessages") && shared.get("chatMessages").isJsonArray) {
                val remoteMessages = runCatching {
                    gson.fromJson<List<ChatMessage>>(shared.get("chatMessages"), object : TypeToken<List<ChatMessage>>() {}.type)
                }.getOrNull().orEmpty()
                val merged = (_messages.value + remoteMessages)
                    .distinctBy { "${it.role}:${it.timestamp}:${it.content}" }
                    .sortedBy { it.timestamp }
                    .takeLast(80)
                if (merged != _messages.value && merged.isNotEmpty()) {
                    _messages.value = merged
                    prefs.saveChatMessages(merged.takeLast(50))
                    loaded = true
                }
            }

            if (shared.has("notes") && shared.get("notes").isJsonArray) {
                runCatching {
                    gson.fromJson<List<Note>>(shared.get("notes"), object : TypeToken<List<Note>>() {}.type)
                }.getOrNull()?.let {
                    notesCache = it
                    prefs.saveNotes(it)
                }
            }

            if (shared.has("habits") && shared.get("habits").isJsonArray) {
                runCatching {
                    gson.fromJson<List<Habit>>(shared.get("habits"), object : TypeToken<List<Habit>>() {}.type)
                }.getOrNull()?.let {
                    habitsCache = it
                    prefs.saveHabits(it)
                }
            }

            if (shared.has("habitLogs") && shared.get("habitLogs").isJsonArray) {
                runCatching {
                    gson.fromJson<List<HabitLog>>(shared.get("habitLogs"), object : TypeToken<List<HabitLog>>() {}.type)
                }.getOrNull()?.let {
                    habitLogsCache = it
                    prefs.saveHabitLogs(it)
                }
            }
        } finally {
            applyingRemoteState = false
        }
    }

    private data class AutomationCommand(
        val target: String,
        val action: String,
        val args: JsonObject,
        val requiresConfirmation: Boolean = false
    )

    private data class BackendGoogleTool(
        val action: String,
        val query: String = ""
    )

    private data class DanbronAppCommand(
        val action: String,
        val title: String = "",
        val content: String = "",
        val tag: String = "general",
        val profilePatch: User? = null,
        val habitName: String = ""
    )

    private fun parseDanbronAppCommand(text: String): DanbronAppCommand? {
        val t = normalizeQuestion(text)
        val wantsMutation = listOf("agrega", "agregame", "anade", "añade", "crea", "guarda", "edita", "actualiza", "cambia", "marca", "registrame")
            .any { t.contains(it) }
        if (!wantsMutation) return null

        if (listOf("nota", "notas", "anota", "recuerda").any { t.contains(it) }) {
            val raw = extractAfterAny(text, listOf("nota", "anota", "recuerda que", "recuerda", "guarda")).ifBlank { text }
            val clean = cleanCommandPayload(raw)
                .replace(Regex("^(una|un|nueva|nuevo)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            val content = clean.ifBlank { text.trim() }
            val title = content
                .replace(Regex("\\s+"), " ")
                .take(54)
                .ifBlank { "Nota rapida" }
            return DanbronAppCommand(
                action = "add_note",
                title = title,
                content = content,
                tag = inferNoteTag(t)
            )
        }

        if (listOf("habito", "habitos", "racha", "progreso").any { t.contains(it) }) {
            val isMark = listOf("marca", "complete", "completa", "hecho", "cumpli").any { t.contains(it) }
            val payload = cleanCommandPayload(
                extractAfterAny(text, listOf("habito", "progreso", "racha", "marca", "agrega", "agregame", "anade", "añade"))
                    .ifBlank { text }
            )
            return DanbronAppCommand(
                action = if (isMark) "mark_habit" else "add_habit",
                habitName = payload.ifBlank { "Progreso personal" }
            )
        }

        if (listOf("perfil", "ingreso", "ingresos", "gasto", "gastos", "deuda", "trabajo", "chofer", "uber", "nombre").any { t.contains(it) }) {
            val current = user ?: return null
            var updated = current
            extractMoneyAfter(t, listOf("ingreso", "ingresos", "gano", "ganancia"))?.let { updated = updated.copy(income = it) }
            extractMoneyAfter(t, listOf("gasto", "gastos", "costo", "costos"))?.let { updated = updated.copy(expenses = it) }
            extractMoneyAfter(t, listOf("deuda", "debo"))?.let { updated = updated.copy(debt = it) }

            if (t.contains("uber") || t.contains("chofer") || t.contains("conductor")) {
                val summary = buildString {
                    append(updated.lifeSummary)
                    if (isNotBlank()) append(" ")
                    append("Trabaja como conductor/chofer; Danbron debe apoyar control de ingresos, horas, viajes y finanzas.")
                }.trim().take(700)
                updated = updated.copy(
                    employmentStatus = "self_employed",
                    workStyle = "driver",
                    priorities = (updated.priorities + listOf("finances", "income")).distinct(),
                    lifeSummary = summary
                )
            }

            val nameMatch = Regex("(?:me llamo|nombre es|cambia mi nombre a)\\s+([A-Za-zÁÉÍÓÚáéíóúÑñ ]{2,40})", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!nameMatch.isNullOrBlank()) updated = updated.copy(name = nameMatch)

            return if (updated != current) {
                DanbronAppCommand(action = "update_profile", profilePatch = updated)
            } else {
                null
            }
        }

        return null
    }

    private suspend fun executeDanbronAppCommand(command: DanbronAppCommand): String {
        return when (command.action) {
            "add_note" -> {
                val note = Note(title = command.title, content = command.content, tag = command.tag)
                notesCache = listOf(note) + notesCache
                prefs.saveNotes(notesCache)
                "Listo. Agregue la nota real en Danbron: \"${note.title}\"."
            }
            "add_habit" -> {
                val habit = Habit(name = command.habitName.replaceFirstChar { it.uppercase() })
                habitsCache = listOf(habit) + habitsCache
                prefs.saveHabits(habitsCache)
                "Listo. Agregue el habito/progreso real: \"${habit.name}\"."
            }
            "mark_habit" -> {
                val habit = findBestHabit(command.habitName)
                    ?: return "No encontre un habito parecido a \"${command.habitName}\". Puedes decirme: agregame un progreso llamado ..."
                val today = LocalDate.now(ZoneId.of("America/Santiago")).toString()
                val exists = habitLogsCache.any { it.habitId == habit.id && it.date == today }
                if (!exists) {
                    habitLogsCache = habitLogsCache + HabitLog(habit.id, today)
                    habitsCache = habitsCache.map {
                        if (it.id == habit.id) it.copy(streak = it.streak + 1) else it
                    }
                    prefs.saveHabitLogs(habitLogsCache)
                    prefs.saveHabits(habitsCache)
                }
                "Listo. Marque progreso de hoy para \"${habit.name}\"."
            }
            "update_profile" -> {
                val updated = command.profilePatch ?: return "No encontre cambios concretos para el perfil."
                user = updated
                prefs.saveUser(updated)
                pushSharedState("profile-chat-updated")
                "Listo. Actualice tu perfil real en Danbron. Ingresos: ${updated.income.toInt()}, gastos: ${updated.expenses.toInt()}, deuda: ${updated.debt.toInt()}."
            }
            else -> "No reconozco esa accion interna de Danbron."
        }
    }

    private fun findBestHabit(query: String): Habit? {
        val q = normalizeQuestion(query)
        return habitsCache.firstOrNull { normalizeQuestion(it.name).contains(q) || q.contains(normalizeQuestion(it.name)) }
            ?: habitsCache.firstOrNull()
    }

    private fun inferNoteTag(text: String): String = when {
        listOf("plata", "dinero", "finanza", "finanzas", "gasto", "ingreso", "deuda").any { text.contains(it) } -> "finance"
        listOf("meta", "objetivo", "progreso").any { text.contains(it) } -> "goal"
        listOf("idea", "proyecto").any { text.contains(it) } -> "idea"
        else -> "general"
    }

    private fun extractMoneyAfter(text: String, markers: List<String>): Double? {
        markers.forEach { marker ->
            val match = Regex("$marker\\D{0,18}([0-9][0-9.,]*)", RegexOption.IGNORE_CASE).find(text)
            val raw = match?.groupValues?.getOrNull(1)
            if (!raw.isNullOrBlank()) {
                val normalized = raw.replace(".", "").replace(",", ".")
                normalized.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun parseBackendGoogleTool(text: String): BackendGoogleTool? {
        val t = normalizeQuestion(text)
        val asksGoogle = listOf("gmail", "correo", "email", "mail", "calendario", "calendar", "agenda", "drive").any { t.contains(it) }
        if (!asksGoogle) return null

        if (listOf("no leidos", "unread", "inbox", "bandeja", "correos").any { t.contains(it) }) {
            val query = when {
                t.contains("no leidos") || t.contains("unread") -> "is:unread"
                else -> "newer_than:7d"
            }
            return BackendGoogleTool("gmail_search", query)
        }

        val readId = Regex("\\b[a-f0-9]{12,}\\b", RegexOption.IGNORE_CASE).find(t)?.value
        if (readId != null && listOf("lee", "leer", "read").any { t.contains(it) }) {
            return BackendGoogleTool("gmail_read", readId)
        }

        if (listOf("calendario", "calendar", "agenda").any { t.contains(it) }) {
            return BackendGoogleTool("calendar_events")
        }

        if (t.contains("drive")) {
            return BackendGoogleTool("drive_list")
        }

        return null
    }

    private suspend fun executeBackendGoogleTool(tool: BackendGoogleTool): String {
        val token = syncManager.getAuthToken() ?: return "Necesito que inicies sesion para usar Google desde el backend."
        val bearer = "Bearer $token"
        val result = when (tool.action) {
            "gmail_search" -> BackendAiService.api.gmailSearch(bearer, tool.query, 5).text
            "gmail_read" -> BackendAiService.api.gmailRead(bearer, tool.query).text
            "calendar_events" -> BackendAiService.api.calendarEvents(bearer).text
            "drive_list" -> BackendAiService.api.driveList(bearer, 10).text
            else -> ""
        }
        return result.ifBlank { "No encontre resultados." }
    }

    private fun parseAutomationCommand(text: String): AutomationCommand? {
        val t = normalizeQuestion(text)
        val looksLikeAgentTest = listOf("prueba completa", "prueba integral", "modo agente", "agent test", "todas las skills", "varias skills")
            .any { t.contains(it) }
        val looksLikeCommand = listOf("abre", "abrir", "pon", "busca", "buscar", "envia", "enviar", "manda", "mandar", "escribe", "redacta", "entra")
            .any { t.contains(it) }
        if (!looksLikeCommand && !looksLikeAgentTest) return null

        if (looksLikeAgentTest) {
            return AutomationCommand(
                target = "android",
                action = "agent_test",
                args = JsonObject().apply { addProperty("prompt", text) }
            )
        }

        val target = if (listOf("pc", "computador", "windows", "ordenador").any { t.contains(it) }) {
            "windows"
        } else {
            "android"
        }

        if (target == "android" && t.contains("youtube") && (t.contains("whatsapp") || t.contains("wsp"))) {
            val commands = JsonArray()
            val youtubeQuery = extractYouTubeQuery(text)
            commands.add(JsonObject().apply {
                addProperty("action", if (youtubeQuery.isBlank()) "open_app" else "open_url")
                add("args", JsonObject().apply {
                    if (youtubeQuery.isBlank()) {
                        addProperty("app", "youtube")
                    } else {
                        addProperty("url", "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(youtubeQuery, "UTF-8")}")
                    }
                })
            })
            commands.add(JsonObject().apply {
                addProperty("action", "compose_whatsapp")
                add("args", JsonObject().apply { addProperty("message", extractMessagePayload(text)) })
            })
            return AutomationCommand(
                target = "android",
                action = "sequence",
                args = JsonObject().apply { add("commands", commands) },
                requiresConfirmation = true
            )
        }

        val app = when {
            t.contains("whatsapp") || t.contains("wsp") -> "whatsapp"
            t.contains("gmail") -> "gmail"
            t.contains("youtube") -> "youtube"
            t.contains("chrome") -> "chrome"
            t.contains("configuracion") || t.contains("settings") -> "settings"
            t.contains("portal") || t.contains("universidad") -> "portal_universidad"
            else -> null
        }

        if (app == "whatsapp" && listOf("envia", "enviar", "manda", "mandar", "escribe", "redacta").any { t.contains(it) }) {
            return AutomationCommand(
                target = target,
                action = "compose_whatsapp",
                args = JsonObject().apply { addProperty("message", extractMessagePayload(text)) },
                requiresConfirmation = true
            )
        }

        if (app == "youtube") {
            val query = extractYouTubeQuery(text)
            return if (query.isNotBlank()) {
                AutomationCommand(
                    target = target,
                    action = "open_url",
                    args = JsonObject().apply {
                        addProperty("url", "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}")
                    }
                )
            } else {
                AutomationCommand(target, "open_app", JsonObject().apply { addProperty("app", "youtube") })
            }
        }

        if (app == "portal_universidad") {
            return AutomationCommand(
                target,
                "open_url",
                JsonObject().apply { addProperty("url", "https://portal.uv.cl/") }
            )
        }

        if (app != null) {
            return AutomationCommand(target, "open_app", JsonObject().apply { addProperty("app", app) })
        }

        if (t.contains("busca") || t.contains("buscar")) {
            val query = cleanCommandPayload(extractAfterAny(text, listOf("busca", "buscar")).ifBlank { text })
            return AutomationCommand(
                target,
                "open_url",
                JsonObject().apply {
                    addProperty("url", "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                }
            )
        }

        return null
    }

    private suspend fun executeAutomationCommand(command: AutomationCommand): String {
        if (command.target == "android" && command.action == "agent_test") {
            return runAndroidIntegratedAgentTest()
        }

        if (command.target == "windows") {
            if (!syncManager.hasPairedDevice()) {
                return "Todavia no veo el PC emparejado para mandarle esa orden."
            }
            val id = syncManager.sendRemoteCommand(
                target = "windows",
                action = command.action,
                args = command.args,
                requiresConfirmation = command.requiresConfirmation
            )
            return if (command.requiresConfirmation) {
                "Le mande al PC la orden. Si involucra enviar o redactar algo, Bron lo va a dejar preparado y pedir confirmacion. ID: $id"
            } else {
                "Le mande al PC la orden para ejecutarla. ID: $id"
            }
        }

        val payload = JsonObject().apply {
            addProperty("id", "local_${System.currentTimeMillis()}")
            addProperty("target", "android")
            addProperty("action", command.action)
            add("args", command.args)
        }
        val result = commandExecutor.execute(payload)
        return if (command.requiresConfirmation) "$result Te dejo el ultimo toque a la vista." else result
    }

    private suspend fun runAndroidIntegratedAgentTest(): String {
        data class StepResult(val name: String, val ok: Boolean, val detail: String)
        val results = mutableListOf<StepResult>()

        fun clip(value: String, max: Int = 1100): String {
            val clean = value.trim()
            return if (clean.length > max) clean.take(max).trimEnd() + "\n..." else clean
        }

        fun mark(name: String, ok: Boolean, detail: String) {
            results += StepResult(name, ok, clip(detail))
        }

        mark("Contexto Android", true, buildAndroidAccessReply())
        mark("Pantalla visible Android", true, buildAndroidScreenContextReply())

        val token = syncManager.getAuthToken()
        val bearer = token?.let { "Bearer $it" }
        if (bearer == null) {
            mark("Backend Google", false, "No hay sesion backend activa; no puedo usar Gmail/Calendar/Drive desde Android.")
        } else {
            val gmail = runCatching { BackendAiService.api.gmailSearch(bearer, "is:unread", 5).text }
            mark("Gmail no leidos", gmail.isSuccess, gmail.getOrNull()?.ifBlank { "Sin correos no leidos." } ?: gmail.exceptionOrNull()?.message.orEmpty())

            val calendar = runCatching { BackendAiService.api.calendarEvents(bearer).text }
            mark("Calendar hoy", calendar.isSuccess, calendar.getOrNull()?.ifBlank { "Sin eventos visibles." } ?: calendar.exceptionOrNull()?.message.orEmpty())

            val drive = runCatching { BackendAiService.api.driveList(bearer, 5).text }
            mark("Drive", drive.isSuccess, drive.getOrNull()?.ifBlank { "Drive sin archivos visibles." } ?: drive.exceptionOrNull()?.message.orEmpty())

            val zone = ZoneId.of("America/Santiago")
            val start = LocalDate.now(zone).plusDays(1).atTime(10, 0).atZone(zone).toOffsetDateTime()
            val end = start.plusMinutes(30)
            val event = runCatching {
                BackendAiService.api.calendarCreate(
                    bearer,
                    BackendCalendarCreateRequest(
                        summary = "Revision Danbron Android Agent Test",
                        from = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        to = end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        description = "Prueba integral Android de Gmail, Calendar, Drive, backend y sync PC."
                    )
                ).text
            }
            mark("Crear evento Calendar", event.isSuccess, event.getOrNull()?.ifBlank { "Evento creado para manana 10:00 America/Santiago." } ?: event.exceptionOrNull()?.message.orEmpty())

            val emailBody = """
                Hola Brandon,

                Danbron ejecuto una prueba integral desde Android: contexto/permisos, Gmail, Calendar, Drive, creacion de evento, envio de correo y sincronizacion con PC.

                Este correo confirma que el orquestador Android quedo implementado.

                Saludos,
                Danbron
            """.trimIndent()
            val email = runCatching {
                BackendAiService.api.gmailSend(
                    bearer,
                    BackendGmailSendRequest(
                        to = "brandontorres.dev@gmail.com",
                        subject = "Danbron Android Agent Test completado",
                        body = emailBody
                    )
                ).text
            }
            mark("Enviar correo Gmail", email.isSuccess, email.getOrNull()?.ifBlank { "Correo enviado." } ?: email.exceptionOrNull()?.message.orEmpty())
        }

        val localYoutube = runCatching {
            val payload = JsonObject().apply {
                addProperty("id", "local_${System.currentTimeMillis()}")
                addProperty("target", "android")
                addProperty("action", "open_app")
                add("args", JsonObject().apply { addProperty("app", "youtube") })
            }
            commandExecutor.execute(payload)
        }
        mark("Accion local Android", localYoutube.isSuccess, localYoutube.getOrNull() ?: localYoutube.exceptionOrNull()?.message.orEmpty())

        val pcSync = if (syncManager.hasPairedDevice()) {
            runCatching {
                syncManager.sendRemoteCommand(
                    target = "windows",
                    action = "open_app",
                    args = JsonObject().apply { addProperty("app", "youtube") }
                )
            }
        } else {
            Result.failure(Exception("Telefono sin PC emparejado."))
        }
        mark("Android-PC sync", pcSync.isSuccess && !pcSync.getOrNull().isNullOrBlank(), pcSync.getOrNull()?.let { "Orden enviada al PC. ID: $it" } ?: pcSync.exceptionOrNull()?.message.orEmpty())

        val checklist = results.joinToString("\n\n") { result ->
            "${if (result.ok) "[OK]" else "[FALLO]"} ${result.name}\n${result.detail.ifBlank { "Sin detalle." }}"
        }
        val failed = results.filter { !it.ok }.map { it.name }
        val summary = if (failed.isEmpty()) {
            "Funciono completo desde Android: contexto, pantalla visible, Google tools, evento, correo, accion local y sync PC."
        } else {
            "Funciono parcialmente. Fallaron: ${failed.joinToString(", ")}."
        }

        return "DANBRON ANDROID AGENT TEST\n\n$checklist\n\nRESUMEN\n$summary\n\nNOTA\nNo invente resultados: cada bloque viene de una herramienta real o reporta su fallo."
    }

    private fun extractMessagePayload(text: String): String {
        val quoted = Regex("[\"“](.+?)[\"”]").find(text)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank()) return quoted.trim()
        return extractAfterAny(text, listOf("envia", "enviar", "manda", "mandar", "escribe", "redacta")).trim()
    }

    private fun extractAfterAny(text: String, markers: List<String>): String {
        val normalized = normalizeQuestion(text)
        for (marker in markers) {
            val idx = normalized.indexOf(marker)
            if (idx >= 0) {
                return text.substring((idx + marker.length).coerceAtMost(text.length))
                    .replace(Regex("^(en|mi|el|la|por|para)\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()
            }
        }
        return ""
    }

    private fun cleanCommandPayload(value: String): String {
        return value
            .replace(Regex("^\\s*(con|de|sobre|acerca de|en|mi|el|la|por|para)\\s+", RegexOption.IGNORE_CASE), "")
            .split(Regex("\\s*(?:,|\\.)?\\s+y\\s+(?:despues|luego|entonces)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.split(Regex("\\s*(?:,|\\.)?\\s+and\\s+(?:then|after)\\b", RegexOption.IGNORE_CASE))
            ?.firstOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun extractYouTubeQuery(text: String): String {
        val direct = cleanCommandPayload(extractAfterAny(text, listOf("youtube")))
        if (direct.isNotBlank() && direct !in listOf("abre", "abrir", "busca", "buscar", "pon")) {
            return direct
        }

        val intentQuery = cleanCommandPayload(extractAfterAny(text, listOf("busca", "buscar", "pon", "reproduce", "escucha")))
        if (intentQuery.isNotBlank()) return intentQuery

        return ""
    }

    private fun isAndroidAccessQuestion(text: String): Boolean {
        val t = text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        val accessTerms = listOf(
            "acceso", "permiso", "permisos", "puedes ver", "que ves",
            "que tengo abierto", "apps", "aplicaciones", "whatsapp", "wsp",
            "gmail", "telefono", "celular", "pantalla", "texto visible", "visible"
        )
        val askTerms = listOf("tienes", "puedes", "sabes", "dime", "digas", "ver", "mira", "leer", "abierto")
        return accessTerms.any { t.contains(it) } && askTerms.any { t.contains(it) }
    }

    private fun isDeviceLinkQuestion(text: String): Boolean {
        val t = normalizeQuestion(text)
        val deviceTerms = listOf(
            "pc", "computador", "windows", "telefono", "celular",
            "conectado", "vinculado", "emparejado", "sincronizado"
        )
        val askTerms = listOf("estas", "tienes", "estoy", "puedes", "sabes", "conectado", "vinculado")
        return deviceTerms.any { t.contains(it) } && askTerms.any { t.contains(it) }
    }

    private fun buildAndroidAccessReply(): String {
        val app = getApplication<Application>()
        val runtimeOk = requiredRuntimePermissions().all {
            ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
        }
        val accessibilityOk = isAccessibilityServiceEnabled(app)

        if (!runtimeOk || !accessibilityOk) {
            val missing = mutableListOf<String>()
            if (!runtimeOk) missing.add("microfono/notificaciones")
            if (!accessibilityOk) missing.add("Accesibilidad")
            return "Todavia falta activar esto en Android: ${missing.joinToString(" y ")}. Cuando lo aceptes, Danbron puede registrar contexto permitido del telefono desde ese momento."
        }

        val pcStatus = if (syncManager.hasPairedDevice()) {
            "Tambien estoy vinculado con tu PC por la sincronizacion de Danbron, asi que puedo usar los eventos y contexto que la app de Windows envie al backend."
        } else {
            "En este telefono todavia no veo una vinculacion de PC guardada; hay que emparejarlo antes de prometer datos del computador."
        }

        return "Si. En este telefono Danbron tiene microfono/notificaciones y Accesibilidad activos. $pcStatus Puedo detectar cambios de apps abiertas y eventos permitidos desde ahora, pero no leer mensajes cifrados ni inventar contenido de WhatsApp, Gmail u otras apps. Si no tengo datos concretos, te lo voy a decir claro."
    }

    private fun buildAndroidScreenContextReply(): String {
        val snapshot = AndroidScreenContextStore.latestSnapshot()
            ?: return "Todavia no tengo una captura de texto visible. Abre la app que quieres analizar y vuelve a pedirmelo."

        val ageSeconds = ((System.currentTimeMillis() - snapshot.timestamp) / 1000).coerceAtLeast(0)
        val visible = snapshot.nodes
            .mapNotNull { it.text.ifBlank { it.description }.takeIf(String::isNotBlank) }
            .distinct()
            .take(30)
            .joinToString("\n")
            .ifBlank { "No encontre texto visible legible en esta pantalla." }

        return "Pantalla Android detectada\nApp: ${snapshot.packageName}\nClase: ${snapshot.className.ifBlank { "sin clase visible" }}\nAntiguedad: ${ageSeconds}s\nNodos leidos: ${snapshot.nodes.size}\n\nTexto visible:\n$visible\n\nModo: solo lectura por Accesibilidad; no toque botones ni ejecute acciones en esta pantalla."
    }

    private fun normalizeQuestion(text: String): String {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return normalized
            .replace("Ã¡", "a")
            .replace("Ã©", "e")
            .replace("Ã­", "i")
            .replace("Ã³", "o")
            .replace("Ãº", "u")
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun isAccessibilityServiceEnabled(app: Application): Boolean {
        val expected = ComponentName(app, AppTrackerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { service ->
            service.equals(expected, ignoreCase = true)
        }
    }
}
