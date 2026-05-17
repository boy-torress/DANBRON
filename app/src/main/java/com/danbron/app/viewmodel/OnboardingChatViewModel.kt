package com.danbron.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danbron.app.api.*
import com.danbron.app.data.AudioRecorderManager
import com.danbron.app.data.SpeechRecognitionManager
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.VoiceManager
import com.danbron.app.data.models.*
import com.danbron.app.sync.DanbronSyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Conversational Onboarding ViewModel
 *
 * Instead of forms, Bron has a natural conversation to get to know the user.
 * Each response is analyzed to extract profile data progressively.
 * After enough context is gathered (~5-8 exchanges), Bron suggests starting.
 */
class OnboardingChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)
    private val syncManager = DanbronSyncManager(app)
    val voiceManager = VoiceManager(app)
    val recorderManager = AudioRecorderManager(app)
    val speechRecognizer = SpeechRecognitionManager(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _progressText = MutableStateFlow("Bron te está conociendo...")
    val progressText: StateFlow<String> = _progressText

    private val _suggestedChips = MutableStateFlow<List<String>>(emptyList())
    val suggestedChips: StateFlow<List<String>> = _suggestedChips

    // Profile being built progressively
    private val _profileData = MutableStateFlow(mutableMapOf<String, String>())

    private var apiKey = ""
    private var phase = OnboardingPhase.GREETING
    private var exchangeCount = 0

    enum class OnboardingPhase {
        GREETING, NAME, LIFE_SITUATION, WORK, FINANCES, HEALTH, CHALLENGES, SUMMARY
    }

    init {
        viewModelScope.launch { prefs.apiKey.collect { apiKey = it } }
    }

    fun startConversation() {
        if (_messages.value.isNotEmpty()) return

        val greeting = ChatMessage("assistant",
            "¡Hola! 🦊 Soy Bron, tu compañero personal. Estoy aquí para conocerte y ayudarte con lo que necesites — finanzas, hábitos, salud, trabajo, lo que sea.\n\nAntes de empezar, ¿cómo te llamas?"
        )
        _messages.value = listOf(greeting)
        voiceManager.speak(greeting.content)
        _suggestedChips.value = emptyList()
        phase = OnboardingPhase.NAME
        persistMessages()
    }

    fun toggleVoice(enabled: Boolean) {
        voiceManager.isEnabled = enabled
        if (!enabled) voiceManager.stop()
    }

    fun startRecording() {
        voiceManager.stop()
        _isRecording.value = true

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
            if (apiKey.isBlank()) {
                _isRecording.value = false
                _messages.value = _messages.value + ChatMessage("assistant", "Reconocimiento de voz no disponible. Escríbeme en su lugar.")
                voiceManager.speak("No puedo escucharte. Escríbeme.")
                return
            }
            recorderManager.startRecording()
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecordingAndSend()
        } else {
            startRecording()
        }
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) return

        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            _isRecording.value = false
            return
        }

        _isRecording.value = false
        val audioFile = recorderManager.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() < 500) {
            _messages.value = _messages.value + ChatMessage("assistant", "No alcancé a escucharte. Intenta de nuevo.")
            voiceManager.speak("No te escuché bien, intenta de nuevo.")
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
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage("assistant", "Tuve un problema escuchándote. Intenta de nuevo o escríbeme.")
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

        _messages.value = _messages.value + ChatMessage("user", text)
        exchangeCount++
        persistMessages()

        // Extract data from user response based on current phase
        extractProfileData(text)

        // Check if the user clicked "start" and we're ready
        if ((_isReady.value && text.contains("empezar", ignoreCase = true)) || (text.contains("listo", ignoreCase = true) && exchangeCount >= 5)) {
            _isReady.value = true
            return
        }

        // Generate AI response or use local fallback
        _isTyping.value = true
        viewModelScope.launch {
            try {
                if (apiKey.isNotBlank()) {
                    val reply = generateAIResponse(text)
                    _messages.value = _messages.value + ChatMessage("assistant", reply)
                    voiceManager.speak(reply)
                } else {
                    val reply = generateLocalResponse(text)
                    _messages.value = _messages.value + ChatMessage("assistant", reply)
                    voiceManager.speak(reply)
                }
            } catch (e: Exception) {
                // Fallback to local questions
                val reply = generateLocalResponse(text)
                _messages.value = _messages.value + ChatMessage("assistant", reply)
                voiceManager.speak(reply)
            } finally {
                _isTyping.value = false
                advancePhase()
                updateProgress()
                updateChips()
                persistMessages()
            }
        }
    }

    private fun extractProfileData(text: String) {
        val data = _profileData.value.toMutableMap()
        val lower = text.lowercase().trim()

        when (phase) {
            OnboardingPhase.NAME -> {
                // Extract name — strip greetings and filler words first
                val greetings = listOf("hola", "hey", "buenas", "buenos dias", "buenas tardes", "buenas noches", "que tal", "saludos", "ey", "ei", "hi", "hello")
                val fillers = listOf("me llamo", "mi nombre es", "soy", "me dicen", "dime", "llamame", "yo soy", "puedes decirme", "me puedes decir")
                var cleaned = text.trim()
                // Remove greetings at start
                for (g in greetings) {
                    if (cleaned.lowercase().startsWith(g)) {
                        cleaned = cleaned.substring(g.length).trimStart(',', '!', '.', ' ')
                    }
                }
                // Remove filler phrases
                for (f in fillers) {
                    val idx = cleaned.lowercase().indexOf(f)
                    if (idx >= 0) {
                        cleaned = cleaned.substring(idx + f.length).trimStart(',', '!', '.', ' ')
                    }
                }
                // Take the first meaningful word as the name
                val name = cleaned.split(" ", ",", ".").firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                    ?: text.trim().split(" ").lastOrNull()?.replaceFirstChar { it.uppercase() }
                    ?: text.trim()
                data["name"] = name
            }
            OnboardingPhase.LIFE_SITUATION -> {
                // Detect life role
                when {
                    lower.contains("hijo") || lower.contains("papá") || lower.contains("mamá") || lower.contains("padre") || lower.contains("madre") || lower.contains("familia") -> data["lifeRole"] = "parent"
                    lower.contains("estudi") -> data["lifeRole"] = "student"
                    lower.contains("jubilad") || lower.contains("retir") -> data["lifeRole"] = "retired"
                    lower.contains("cuid") -> data["lifeRole"] = "caregiver"
                    else -> data["lifeRole"] = "individual"
                }
            }
            OnboardingPhase.WORK -> {
                when {
                    lower.contains("sin trabajo") || lower.contains("desempleado") || lower.contains("sin empleo") || lower.contains("buscando") -> data["employment"] = "unemployed"
                    lower.contains("independiente") || lower.contains("freelance") || lower.contains("emprendedor") || lower.contains("mi negocio") || lower.contains("propio") -> data["employment"] = "self_employed"
                    lower.contains("estudio") || lower.contains("estudiante") || lower.contains("universidad") || lower.contains("colegio") -> data["employment"] = "student"
                    lower.contains("jubilad") || lower.contains("retir") -> data["employment"] = "retired"
                    else -> data["employment"] = "employed"
                }
                // Try to detect work style
                when {
                    lower.contains("remoto") || lower.contains("casa") || lower.contains("home") -> data["workStyle"] = "remote"
                    lower.contains("medio") || lower.contains("part") -> data["workStyle"] = "part_time"
                    lower.contains("independ") || lower.contains("freelance") -> data["workStyle"] = "freelance"
                    else -> data["workStyle"] = "full_time"
                }
            }
            OnboardingPhase.FINANCES -> {
                // Try to extract numbers
                val numbers = Regex("\\d+[.,]?\\d*").findAll(lower).map { it.value.replace(",", ".").toDoubleOrNull() ?: 0.0 }.toList()
                if (numbers.isNotEmpty()) data["income"] = numbers.first().toString()
                if (numbers.size > 1) data["expenses"] = numbers[1].toString()
                if (numbers.size > 2) data["debt"] = numbers[2].toString()

                // Detect goal
                when {
                    lower.contains("deuda") || lower.contains("debo") || lower.contains("pagar") -> data["goal"] = "debt"
                    lower.contains("ahorr") || lower.contains("ganar") || lower.contains("ingreso") -> data["goal"] = "income"
                    lower.contains("tiempo") || lower.contains("productiv") -> data["goal"] = "time"
                    else -> data["goal"] = "habits"
                }
            }
            OnboardingPhase.HEALTH -> {
                when {
                    lower.contains("baj") && (lower.contains("peso") || lower.contains("kilo")) -> data["healthFocus"] = "weight_loss"
                    lower.contains("ejercicio") || lower.contains("gimnasio") || lower.contains("correr") || lower.contains("deporte") -> data["healthFocus"] = "fitness"
                    lower.contains("estrés") || lower.contains("ansiedad") || lower.contains("mental") || lower.contains("depre") -> data["healthFocus"] = "mental_health"
                    lower.contains("bien") || lower.contains("no") || lower.contains("nada") -> data["healthFocus"] = "none"
                    else -> data["healthFocus"] = "none"
                }
                // Stress level from keywords
                when {
                    lower.contains("muy estresado") || lower.contains("mucho estrés") || lower.contains("terrible") -> data["stressLevel"] = "8"
                    lower.contains("estresado") || lower.contains("estrés") || lower.contains("agotado") -> data["stressLevel"] = "7"
                    lower.contains("normal") || lower.contains("regular") || lower.contains("más o menos") -> data["stressLevel"] = "5"
                    lower.contains("bien") || lower.contains("tranquil") || lower.contains("relajado") -> data["stressLevel"] = "3"
                    else -> data["stressLevel"] = "5"
                }
            }
            OnboardingPhase.CHALLENGES -> {
                val challenges = mutableListOf<String>()
                if (lower.contains("ahorr") || lower.contains("plata") || lower.contains("dinero")) challenges.add("no_savings")
                if (lower.contains("gast") || lower.contains("compro") || lower.contains("impuls")) challenges.add("impulse_spending")
                if (lower.contains("sedentario") || lower.contains("sentado") || lower.contains("no hago ejercicio")) challenges.add("sedentary")
                if (lower.contains("dormir") || lower.contains("insomnio") || lower.contains("sueño")) challenges.add("insomnia")
                if (lower.contains("redes") || lower.contains("pantalla") || lower.contains("teléfono") || lower.contains("celular")) challenges.add("screen_time")
                if (lower.contains("estrés") || lower.contains("ansiedad") || lower.contains("nervio")) challenges.add("anxiety")
                if (lower.contains("empleo") || lower.contains("trabajo") || lower.contains("buscar")) challenges.add("unemployment")
                if (lower.contains("comida") || lower.contains("alimentación") || lower.contains("chatarra")) challenges.add("bad_diet")
                if (challenges.isNotEmpty()) data["challenges"] = challenges.joinToString(",")
            }
            else -> {}
        }

        _profileData.value = data
    }

    private suspend fun generateAIResponse(userText: String): String {
        val data = _profileData.value
        val name = data["name"] ?: "amigo"

        val systemPrompt = """Eres Bron, un zorrito dorado amigable y empático que está conociendo a un usuario nuevo en la app Danbron. Estás en el proceso de ONBOARDING CONVERSACIONAL.

TU PERSONALIDAD: Eres como el mejor amigo — cálido, genuino, directo pero con cariño. No eres formal ni robótico. Hablas como un amigo cercano latinoamericano.

FASE ACTUAL: ${phase.name}
DATOS RECOPILADOS HASTA AHORA: ${data.entries.joinToString(", ") { "${it.key}=${it.value}" }}
INTERCAMBIOS: $exchangeCount

INSTRUCCIONES CRÍTICAS:
1. Haz UNA sola pregunta por respuesta — no bombardees
2. Sé breve (2-3 oraciones máximo)
3. Reacciona genuinamente a lo que dice el usuario antes de preguntar
4. Siempre en español latinoamericano casual
5. NO uses bullet points ni listas
6. Si el usuario da información financiera, no juzgues
7. Sé empático si el usuario comparte problemas personales
8. Después de ${if (exchangeCount >= 5) "ESTA respuesta" else "recopilar suficiente info"}, di que ya lo conoces bastante y pregunta si quiere que armes su plan personalizado

PRÓXIMA PREGUNTA A HACER (adáptala naturalmente):
${getNextQuestion(name)}

NOMBRE DEL USUARIO: $name"""

        val apiMessages = _messages.value.takeLast(8).map { ApiMessage(it.role, it.content) }

        return AiProvider.sendChat(
            apiKey = apiKey,
            authToken = syncManager.getAuthToken(),
            systemPrompt = systemPrompt,
            messages = apiMessages,
            maxTokens = 200
        )
    }

    private fun generateLocalResponse(userText: String): String {
        val data = _profileData.value
        val name = data["name"] ?: "amigo"

        return when (phase) {
            OnboardingPhase.NAME -> {
                "¡Mucho gusto, $name! 🦊 Me da mucho gusto conocerte. Cuéntame un poco de ti... ¿A qué te dedicas? ¿Trabajas, estudias, o andas en otra onda?"
            }
            OnboardingPhase.LIFE_SITUATION -> {
                val role = data["lifeRole"] ?: "individual"
                val reaction = when (role) {
                    "parent" -> "¡Ser padre/madre es de lo más bonito y retador!"
                    "student" -> "¡Qué bueno que estás invirtiendo en ti!"
                    "retired" -> "¡Qué etapa tan importante!"
                    else -> "¡Entiendo!"
                }
                "$reaction Y dime $name, ¿cómo andas de trabajo? ¿Tienes algo estable, estás buscando, o eres independiente?"
            }
            OnboardingPhase.WORK -> {
                "Perfecto, ya me voy haciendo la idea. 💰 Ahora vamos con la parte que a veces duele pero es importante: ¿cómo andas de plata? ¿Sientes que te alcanza, que andas justo, o que te falta?"
            }
            OnboardingPhase.FINANCES -> {
                "Gracias por la confianza, $name. Todo queda entre nosotros. 🏥 ¿Y tu salud? ¿Hay algo que te preocupe? Puede ser físico, mental, estrés... lo que sea."
            }
            OnboardingPhase.HEALTH -> {
                "Te escucho, $name. ¿Y cuál dirías que es el hábito o la cosa que más te cuesta en tu día a día? Eso que quisieras cambiar pero no has podido."
            }
            OnboardingPhase.CHALLENGES -> {
                "¡Perfecto $name! Ya te conozco bastante. 🦊✨ Siento que tengo una buena idea de quién eres y qué necesitas. ¿Listo para que arme tu plan personalizado?"
            }
            OnboardingPhase.SUMMARY -> {
                "¡Vamos con todo! Tu plan está listo. 🚀"
            }
            else -> "¡Genial! Cuéntame más..."
        }
    }

    private fun getNextQuestion(name: String): String {
        return when (phase) {
            OnboardingPhase.NAME -> "Pregúntale su nombre de forma cálida"
            OnboardingPhase.LIFE_SITUATION -> "Pregunta a qué se dedica, si trabaja, estudia, tiene familia"
            OnboardingPhase.WORK -> "Pregunta sobre su situación laboral específica"
            OnboardingPhase.FINANCES -> "Pregunta cómo anda de plata, si le alcanza, si tiene deudas"
            OnboardingPhase.HEALTH -> "Pregunta sobre su salud, estrés, algo que le preocupe"
            OnboardingPhase.CHALLENGES -> "Pregunta cuál es el hábito o cosa que más le cuesta cambiar"
            OnboardingPhase.SUMMARY -> "Dile que ya lo conoces bastante y pregunta si quiere empezar"
            else -> "Haz una pregunta relevante"
        }
    }

    private fun advancePhase() {
        phase = when (phase) {
            OnboardingPhase.GREETING -> OnboardingPhase.NAME
            OnboardingPhase.NAME -> OnboardingPhase.LIFE_SITUATION
            OnboardingPhase.LIFE_SITUATION -> OnboardingPhase.WORK
            OnboardingPhase.WORK -> OnboardingPhase.FINANCES
            OnboardingPhase.FINANCES -> OnboardingPhase.HEALTH
            OnboardingPhase.HEALTH -> OnboardingPhase.CHALLENGES
            OnboardingPhase.CHALLENGES -> {
                _isReady.value = true
                OnboardingPhase.SUMMARY
            }
            OnboardingPhase.SUMMARY -> OnboardingPhase.SUMMARY
        }
    }

    private fun updateProgress() {
        val total = OnboardingPhase.values().size
        val current = phase.ordinal
        val pct = (current.toFloat() / total * 100).toInt()
        _progressText.value = when {
            pct < 25 -> "Bron te está conociendo..."
            pct < 50 -> "Bron está aprendiendo sobre ti..."
            pct < 75 -> "Ya casi te conozco..."
            else -> "¡Ya te conozco! Listo para tu plan"
        }
    }

    private fun updateChips() {
        _suggestedChips.value = when (phase) {
            OnboardingPhase.LIFE_SITUATION -> listOf("Trabajo y estudio", "Tengo familia", "Soy independiente", "Estoy jubilado")
            OnboardingPhase.WORK -> listOf("Tengo trabajo estable", "Estoy buscando empleo", "Soy freelancer", "Soy estudiante")
            OnboardingPhase.FINANCES -> listOf("Me alcanza justo", "Tengo deudas", "Estoy bien", "Quiero ganar más")
            OnboardingPhase.HEALTH -> listOf("Quiero bajar de peso", "Mucho estrés", "Quiero ejercitarme", "Estoy bien")
            OnboardingPhase.CHALLENGES -> listOf("No puedo ahorrar", "Procrastino mucho", "Mala alimentación", "No duermo bien")
            OnboardingPhase.SUMMARY -> listOf("¡Listo, empecemos!", "Cuéntame más antes")
            else -> emptyList()
        }
    }

    fun buildUser(): User {
        val data = _profileData.value
        val challengesList = (data["challenges"] ?: "").split(",").filter { it.isNotBlank() }

        return User(
            name = data["name"] ?: "Usuario",
            goal = data["goal"] ?: "habits",
            income = data["income"]?.toDoubleOrNull() ?: 0.0,
            expenses = data["expenses"]?.toDoubleOrNull() ?: 0.0,
            debt = data["debt"]?.toDoubleOrNull() ?: 0.0,
            workStyle = data["workStyle"] ?: "full_time",
            joinDate = System.currentTimeMillis(),
            streak = 0,
            lifeRole = data["lifeRole"] ?: "individual",
            employmentStatus = data["employment"] ?: "employed",
            healthFocus = data["healthFocus"] ?: "none",
            stressLevel = (data["stressLevel"] ?: "5").toIntOrNull() ?: 5,
            challenges = challengesList,
            consecutiveDaysActive = 1
        )
    }

    private fun persistMessages() {
        viewModelScope.launch {
            prefs.saveOnboardingMessages(_messages.value.takeLast(30))
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }
}
