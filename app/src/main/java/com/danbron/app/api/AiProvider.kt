package com.danbron.app.api

import com.danbron.app.data.models.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.async

/**
 * Unified AI provider that auto-detects which service to use based on the API key prefix.
 * - gsk_ → Groq (free testing with Llama 3.3 70B)
 * - sk-ant- → Anthropic (Claude)
 */
object AiProvider {

    enum class Provider { BACKEND, GROQ, OPENROUTER, ANTHROPIC, UNKNOWN }

    fun detect(apiKey: String): Provider = when {
        apiKey == "backend" -> Provider.BACKEND
        apiKey.startsWith("gsk_") -> Provider.GROQ
        apiKey.startsWith("sk-or-") -> Provider.OPENROUTER
        apiKey.startsWith("sk-ant-") -> Provider.ANTHROPIC
        else -> Provider.UNKNOWN
    }

    fun providerName(apiKey: String): String = when (detect(apiKey)) {
        Provider.BACKEND -> "Danbron Backend"
        Provider.GROQ -> "Groq (${GroqModels.DEFAULT_CHAT_MODEL})"
        Provider.OPENROUTER -> "OpenRouter (${OpenRouterModels.DEFAULT_CHAT_MODEL})"
        Provider.ANTHROPIC -> "Anthropic (Claude)"
        Provider.UNKNOWN -> "Desconocido"
    }

    /**
     * Send a chat message through the appropriate provider.
     * Uses the adaptive system prompt when provided.
     */
    suspend fun sendChat(
        apiKey: String,
        authToken: String? = null,
        user: User? = null,
        systemPrompt: String? = null,
        messages: List<ApiMessage>,
        maxTokens: Int = 500
    ): String {
        val prompt = systemPrompt ?: (user?.let { AnthropicService.buildSystemPrompt(it) } ?: "Eres Bron, asistente de vida.")

        return when (detect(apiKey)) {
            Provider.BACKEND -> sendViaBackend(authToken, prompt, messages, maxTokens)
            Provider.GROQ -> sendViaGroq(apiKey, prompt, messages, maxTokens)
            Provider.OPENROUTER -> sendViaOpenRouter(apiKey, prompt, messages, maxTokens)
            Provider.ANTHROPIC -> sendViaAnthropic(apiKey, prompt, messages, maxTokens)
            Provider.UNKNOWN -> throw IllegalStateException("API key no reconocida. Usa backend, Groq (gsk_...), OpenRouter (sk-or-...) o Anthropic (sk-ant-...).")
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

    private suspend fun sendViaOpenRouter(
        apiKey: String,
        systemPrompt: String,
        messages: List<ApiMessage>,
        maxTokens: Int
    ): String {
        val openRouterMessages = mutableListOf(GroqMessage("system", systemPrompt))
        openRouterMessages.addAll(messages.map { GroqMessage(it.role, it.content) })

        // Try each model in the fallback chain
        val errors = mutableListOf<String>()
        for (model in OpenRouterModels.FALLBACK_MODELS) {
            try {
                val resp = OpenRouterService.api.sendMessage(
                    auth = "Bearer $apiKey",
                    request = OpenRouterRequest(
                        model = model,
                        maxTokens = maxTokens,
                        messages = openRouterMessages
                    )
                )
                val content = resp.choices.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) return content
                errors.add("$model: empty response")
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val body = try { e.response()?.errorBody()?.string()?.take(100) ?: "" } catch (_: Exception) { "" }
                errors.add("$model: HTTP $code $body")
                android.util.Log.w("AiProvider", "OpenRouter $model: HTTP $code $body")
                if (code == 401 || code == 403) throw IllegalStateException("API key invalida o sin permisos ($code)")
                continue
            } catch (e: Exception) {
                errors.add("$model: ${e.javaClass.simpleName}: ${e.message?.take(80)}")
                android.util.Log.w("AiProvider", "OpenRouter $model failed: ${e.javaClass.simpleName}: ${e.message}")
                continue
            }
        }
        throw IllegalStateException("Ningún modelo respondió. Errores: ${errors.joinToString(" | ")}")
    }

    /**
     * Deep Thinking (Mixture-of-Agents): call 3 models in parallel, synthesize the best answer.
     * Falls back to normal sendChat if deep mode fails.
     */
    suspend fun sendDeepChat(
        apiKey: String,
        authToken: String? = null,
        systemPrompt: String,
        messages: List<ApiMessage>,
        maxTokens: Int = 800
    ): String {
        // Only works with OpenRouter (needs multiple model access)
        if (detect(apiKey) != Provider.OPENROUTER) {
            return sendChat(apiKey, authToken, systemPrompt = systemPrompt, messages = messages, maxTokens = maxTokens)
        }

        val proposerModels = listOf(
            "openai/gpt-oss-120b:free",
            "deepseek/deepseek-v4-flash:free",
            "meta-llama/llama-3.3-70b-instruct:free"
        )
        val aggregatorModels = listOf(
            "nvidia/nemotron-3-super-120b-a12b:free",
            "openai/gpt-oss-120b:free",
            "minimax/minimax-m2.5:free"
        )

        val openRouterMessages = mutableListOf(GroqMessage("system", systemPrompt))
        openRouterMessages.addAll(messages.map { GroqMessage(it.role, it.content) })

        // Phase 1: Call proposers in parallel
        val proposals = kotlinx.coroutines.coroutineScope {
            proposerModels.map { model ->
                async {
                    try {
                        val resp = OpenRouterService.api.sendMessage(
                            auth = "Bearer $apiKey",
                            request = OpenRouterRequest(
                                model = model,
                                maxTokens = 1000,
                                messages = openRouterMessages,
                                temperature = 0.7
                            )
                        )
                        val content = resp.choices.firstOrNull()?.message?.content
                        if (!content.isNullOrBlank()) Pair(model, content) else null
                    } catch (e: Exception) {
                        android.util.Log.w("AiProvider", "DeepThink proposer $model failed: ${e.message}")
                        null
                    }
                }
            }.mapNotNull { it.await() }
        }

        // Fallback: if no proposals succeeded, use normal chat
        if (proposals.isEmpty()) {
            return sendChat(apiKey, authToken, systemPrompt = systemPrompt, messages = messages, maxTokens = maxTokens)
        }

        // If only 1 proposal, return it directly
        if (proposals.size == 1) return proposals[0].second

        // Phase 2: Aggregate — synthesize the best answer
        val expertResponses = proposals.mapIndexed { i, (model, response) ->
            "\n--- EXPERTO ${i + 1} (${model.substringAfterLast("/")}) ---\n$response"
        }.joinToString("\n")

        val synthesisPrompt = """Eres un sintetizador experto. Abajo hay ${proposals.size} respuestas de distintos expertos a la misma pregunta del usuario.
Tu trabajo es crear UNA SOLA respuesta final que:
1. Tome lo MEJOR de cada experto (datos correctos, ideas utiles, perspectivas unicas)
2. Corrija errores o contradicciones entre ellos
3. Sea clara, concisa y en español latino casual
4. NO menciones que hay multiples expertos. Responde como si fueras TU, Bron, el amigo del usuario
5. Mantén el tono natural y amigable

RESPUESTAS DE LOS EXPERTOS:$expertResponses

RESPUESTA FINAL SINTETIZADA:"""

        val userQuestion = messages.lastOrNull()?.content ?: ""
        val aggMessages = listOf(
            GroqMessage("system", systemPrompt),
            GroqMessage("user", userQuestion),
            GroqMessage("user", synthesisPrompt)
        )

        // Try aggregator models
        for (model in aggregatorModels) {
            try {
                val resp = OpenRouterService.api.sendMessage(
                    auth = "Bearer $apiKey",
                    request = OpenRouterRequest(
                        model = model,
                        maxTokens = maxTokens,
                        messages = aggMessages,
                        temperature = 0.2
                    )
                )
                val content = resp.choices.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) return content
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                if (code == 401 || code == 403) break
                continue
            } catch (_: Exception) {
                continue
            }
        }

        // Aggregation failed — return longest proposal (usually most complete)
        return proposals.maxByOrNull { it.second.length }?.second
            ?: proposals[0].second
    }

    suspend fun transcribeAudio(apiKey: String, audioFile: java.io.File, authToken: String? = null): String {
        return when (detect(apiKey)) {
            Provider.BACKEND -> {
                val token = authToken ?: throw IllegalStateException("Token requerido para transcripcion")
                val (filePart, modelPart) = BackendAiService.buildTranscriptionParts(audioFile)
                val response = BackendAiService.api.transcribe("Bearer $token", filePart, modelPart)
                response.text
            }
            Provider.GROQ -> {
                val mediaTypeAudio = "audio/mp4".toMediaType()
                val requestFile = audioFile.asRequestBody(mediaTypeAudio)
                val body = okhttp3.MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
                val model = "whisper-large-v3".toRequestBody("text/plain".toMediaType())

                val response = GroqService.api.transcribeAudio("Bearer $apiKey", body, model)
                response.text
            }
            else -> throw IllegalStateException("La transcripción requiere backend o Groq.")
        }
    }

    private suspend fun sendViaBackend(
        authToken: String?,
        systemPrompt: String,
        messages: List<ApiMessage>,
        maxTokens: Int
    ): String {
        val token = authToken ?: throw IllegalStateException("Token requerido para IA")
        val resp = BackendAiService.api.chat(
            token = "Bearer $token",
            body = BackendChatRequest(
                messages = messages,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens,
                temperature = 0.7
            )
        )
        return resp.text
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
