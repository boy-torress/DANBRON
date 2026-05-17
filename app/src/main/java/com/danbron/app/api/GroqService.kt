package com.danbron.app.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Groq uses OpenAI-compatible format
data class GroqRequest(
    val model: String = GroqModels.DEFAULT_CHAT_MODEL,
    @SerializedName("max_tokens") val maxTokens: Int = 500,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7
)

data class GroqMessage(val role: String, val content: String)

data class GroqResponse(val choices: List<GroqChoice>)
data class GroqChoice(val message: GroqMessage)

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqRequest
    ): GroqResponse

    @retrofit2.http.Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") auth: String,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("model") model: okhttp3.RequestBody
    ): GroqTranscriptionResponse
}

data class GroqTranscriptionResponse(val text: String)

object GroqModels {
    const val DEFAULT_CHAT_MODEL = "llama-3.3-70b-versatile"
}

object GroqService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GroqApi = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqApi::class.java)
}
