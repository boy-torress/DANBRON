package com.danbron.app.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class OpenRouterRequest(
    val model: String = OpenRouterModels.DEFAULT_CHAT_MODEL,
    @SerializedName("max_tokens") val maxTokens: Int = 800,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.3
)

data class OpenRouterResponse(val choices: List<GroqChoice>)

interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "https://danbron.local",
        @Header("X-Title") title: String = "Danbron",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}

object OpenRouterModels {
    const val DEFAULT_CHAT_MODEL = "openai/gpt-oss-120b:free"
    val FALLBACK_MODELS = listOf(
        "openai/gpt-oss-120b:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
        "minimax/minimax-m2.5:free",
        "nvidia/nemotron-nano-12b-v2-vl:free",
        "deepseek/deepseek-v4-flash:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-4-31b-it:free",
        "nousresearch/hermes-3-llama-3.1-405b:free",
        "meta-llama/llama-3.2-3b-instruct:free",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
    )
}

object OpenRouterService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val api: OpenRouterApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenRouterApi::class.java)
}
