package com.danbron.app.api

import com.danbron.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

data class BackendChatRequest(
    val messages: List<ApiMessage>,
    val systemPrompt: String? = null,
    val maxTokens: Int = 600,
    val temperature: Double = 0.7
)

data class BackendChatResponse(
    val text: String,
    val provider: String? = null
)

data class BackendTranscribeResponse(
    val text: String,
    val provider: String? = null
)

data class BackendToolTextResponse(
    val ok: Boolean = false,
    val text: String = ""
)

data class BackendCalendarCreateRequest(
    val summary: String,
    val from: String,
    val to: String,
    val description: String = "",
    val calendarId: String = "primary"
)

data class BackendGmailSendRequest(
    val to: String,
    val subject: String,
    val body: String
)

interface BackendAiApi {
    @POST("ai/chat")
    suspend fun chat(
        @Header("Authorization") token: String,
        @Body body: BackendChatRequest
    ): BackendChatResponse

    @Multipart
    @POST("ai/transcribe")
    suspend fun transcribe(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: okhttp3.RequestBody
    ): BackendTranscribeResponse

    @GET("tools/google/gmail/search")
    suspend fun gmailSearch(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("max") max: Int = 5
    ): BackendToolTextResponse

    @GET("tools/google/gmail/read/{id}")
    suspend fun gmailRead(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): BackendToolTextResponse

    @POST("tools/google/gmail/send")
    suspend fun gmailSend(
        @Header("Authorization") token: String,
        @Body body: BackendGmailSendRequest
    ): BackendToolTextResponse

    @GET("tools/google/calendar/events")
    suspend fun calendarEvents(
        @Header("Authorization") token: String,
        @Query("calendarId") calendarId: String = "primary"
    ): BackendToolTextResponse

    @POST("tools/google/calendar/create")
    suspend fun calendarCreate(
        @Header("Authorization") token: String,
        @Body body: BackendCalendarCreateRequest
    ): BackendToolTextResponse

    @GET("tools/google/drive/ls")
    suspend fun driveList(
        @Header("Authorization") token: String,
        @Query("max") max: Int = 10
    ): BackendToolTextResponse
}

object BackendAiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: BackendAiApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BackendAiApi::class.java)

    fun buildTranscriptionParts(audioFile: File): Pair<MultipartBody.Part, okhttp3.RequestBody> {
        val mediaTypeAudio = "audio/mp4".toMediaType()
        val requestFile = audioFile.asRequestBody(mediaTypeAudio)
        val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
        val model = "whisper-large-v3".toRequestBody("text/plain".toMediaType())
        return body to model
    }
}
