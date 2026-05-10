package com.danbron.app.api

import com.danbron.app.data.models.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Unified AI provider that auto-detects which service to use based on the API key prefix.
 * - gsk_ → Groq (free testing with Llama 3.3 70B)
 * - sk-ant- → Anthropic (Claude)
 */
object AiProvider {

    enum class Provider { GROQ, ANTHROPIC, UNKNOWN }

    fun detect(apiKey: String): Provider = when {
        apiKey.startsWith("gsk_") -> Provider.GROQ
        apiKey.startsWith("sk-ant-") -> Provider.ANTHROPIC
        else -> Provider.UNKNOWN
    }

    fun providerName(apiKey: String): String = when (detect(apiKey)) {
        Provider.GROQ -> "Groq (Llama 3.3)"
        Provider.ANTHROPIC -> "Anthropic (Claude)"
        Provider.UNKNOWN -> "Desconocido"
    }

    /**
     * Send a chat message through the appropriate provider.
     * Uses the adaptive system prompt when provided.
     */
    suspend fun sendChat(
        apiKey: String,
        user: User? = null,
        systemPrompt: String? = null,
        messages: List<ApiMessage>,
        maxTokens: Int = 500
    ): String {
        val prompt = systemPrompt ?: (user?.let { AnthropicService.buildSystemPrompt(it) } ?: "Eres Bron, asistente de vida.")

        return when (detect(apiKey)) {
            Provider.GROQ -> sendViaGroq(apiKey, prompt, messages, maxTokens)
            Provider.ANTHROPIC -> sendViaAnthropic(apiKey, prompt, messages, maxTokens)
            Provider.UNKNOWN -> throw IllegalStateException("API key no reconocida. Usa una key de Groq (gsk_...) o Anthropic (sk-ant-...).")
        }
    }

    private suspend fun sendViaGroq(
        apiKey: String,
        systemPrompt: String,
        messages: List<ApiMessage>,
        maxTokens: Int
    ): String {
        val groqMessages = mutableListOf(GroqMessage("system", systemPrompt))
        groqMessages.addAll(messages.map { GroqMessage(it.role, it.content) })

        val resp = GroqService.api.sendMessage(
            auth = "Bearer $apiKey",
            request = GroqRequest(
                maxTokens = maxTokens,
                messages = groqMessages
            )
        )
        return resp.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Respuesta vacía de Groq")
    }

    suspend fun transcribeAudio(apiKey: String, audioFile: java.io.File): String {
        if (detect(apiKey) != Provider.GROQ) {
            throw IllegalStateException("La transcripción de audio solo está soportada con llaves de Groq.")
        }
        val mediaTypeAudio = "audio/mp4".toMediaType()
        val requestFile = audioFile.asRequestBody(mediaTypeAudio)
        val body = okhttp3.MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
        val model = "whisper-large-v3".toRequestBody("text/plain".toMediaType())

        val response = GroqService.api.transcribeAudio("Bearer $apiKey", body, model)
        return response.text
    }

    private suspend fun sendViaAnthropic(
        apiKey: String,
        systemPrompt: String,
        messages: List<ApiMessage>,
        maxTokens: Int
    ): String {
        val resp = AnthropicService.api.sendMessage(
            apiKey = apiKey,
            request = AnthropicRequest(
                system = systemPrompt,
                maxTokens = maxTokens,
                messages = messages
            )
        )
        return resp.content.firstOrNull()?.text
            ?: throw IllegalStateException("Respuesta vacía de Anthropic")
    }
}
