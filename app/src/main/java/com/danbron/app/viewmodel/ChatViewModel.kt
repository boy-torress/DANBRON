package com.danbron.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danbron.app.api.*
import com.danbron.app.data.AudioRecorderManager
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.VoiceManager
import com.danbron.app.data.models.*
import com.danbron.app.engine.AdaptiveEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPreferences(app)
    val voiceManager = VoiceManager(app)
    val recorderManager = AudioRecorderManager(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private var apiKey = ""
    private var user: User? = null
    private var loaded = false

    init {
        viewModelScope.launch { prefs.apiKey.collect { apiKey = it } }
        viewModelScope.launch { prefs.user.collect { user = it } }
        // Load persisted chat messages
        viewModelScope.launch {
            prefs.chatMessages.collect { saved ->
                if (!loaded && saved.isNotEmpty()) {
                    _messages.value = saved
                    loaded = true
                }
            }
        }
    }

    fun initWelcome() {
        if (_messages.value.isEmpty()) {
            val u = user
            val name = u?.name ?: "amigo"
            val segment = u?.let { AdaptiveEngine.classifyUser(it) }

            val welcomeMsg = when (segment?.segment) {
                LifeSegment.UNEMPLOYED_SEEKING ->
                    "¡Hola $name! 👋 Soy Bron. Estoy aquí para ayudarte a encontrar empleo y mantener tu ánimo alto. ¿Cuéntame, cómo va la búsqueda?"
                LifeSegment.PARENT_BUSY ->
                    "¡Hola $name! 👋 Soy Bron. Sé que tu tiempo es oro cuando tienes familia. Estoy aquí para ayudarte a optimizar cada minuto. ¿En qué te puedo ayudar hoy?"
                LifeSegment.YOUNG_DEBT ->
                    "¡Hola $name! 👋 Soy Bron. Vamos a destruir esa deuda juntos. Cada peso cuenta y yo estoy aquí para guiarte. ¿Qué necesitas hoy?"
                LifeSegment.HEALTH_FOCUSED ->
                    "¡Hola $name! 👋 Soy Bron. Tu salud es la base de todo. Estoy aquí para acompañarte en cada paso. ¿Cómo te sientes hoy?"
                LifeSegment.STUDENT_BROKE ->
                    "¡Hola $name! 👋 Soy Bron. Estudiar y manejar la plata no es fácil, pero juntos lo hacemos. ¿Qué necesitas?"
                else ->
                    "¡Hola $name! 👋 Soy Bron. Estoy aquí para ayudarte a tomar las decisiones correctas cada día. ¿En qué te puedo ayudar hoy?"
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
        if (apiKey.isBlank()) return
        recorderManager.startRecording()
        _isRecording.value = true
        voiceManager.stop() // Stop speaking if user interrupts
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val audioFile = recorderManager.stopRecording()
        if (audioFile != null && audioFile.exists() && audioFile.length() > 1024) {
            _isTyping.value = true
            viewModelScope.launch {
                try {
                    // 1. Transcribe with Whisper
                    val text = AiProvider.transcribeAudio(apiKey, audioFile)
                    if (text.isNotBlank()) {
                        audioFile.delete()
                        // 2. Send the transcribed text to LLM as a normal message
                        sendMessageInternal(text)
                    } else {
                        _isTyping.value = false
                    }
                } catch (e: Exception) {
                    _isTyping.value = false
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return
        sendMessageInternal(text)
    }

    private fun sendMessageInternal(text: String) {
        val u = user ?: return

        _messages.value = _messages.value + ChatMessage("user", text)
        persistMessages()

        _isTyping.value = true
        viewModelScope.launch {
            try {
                val segment = AdaptiveEngine.classifyUser(u)
                val systemPrompt = AdaptiveEngine.buildAdaptiveSystemPrompt(u, segment)
                val apiMessages = _messages.value.takeLast(10).map {
                    ApiMessage(it.role, it.content)
                }
                val reply = AiProvider.sendChat(
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    messages = apiMessages,
                    maxTokens = 600
                )
                _messages.value = _messages.value + ChatMessage("assistant", reply)
                voiceManager.speak(reply)
                persistMessages()
            } catch (e: Exception) {
                val errorMsg = "No pude conectarme en este momento. Intenta de nuevo en unos segundos 🔄"
                _messages.value = _messages.value + ChatMessage("assistant", errorMsg)
                voiceManager.speak(errorMsg)
                persistMessages()
            } finally {
                _isTyping.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }

    fun clearChat() {
        _messages.value = emptyList()
        loaded = false
        viewModelScope.launch { prefs.saveChatMessages(emptyList()) }
    }

    private fun persistMessages() {
        viewModelScope.launch {
            prefs.saveChatMessages(_messages.value.takeLast(50))
        }
    }
}
