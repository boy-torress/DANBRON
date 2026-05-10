package com.danbron.app.api

import com.danbron.app.data.models.User
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class AnthropicRequest(
    val model: String = "claude-sonnet-4-20250514",
    @SerializedName("max_tokens") val maxTokens: Int = 500,
    val system: String,
    val messages: List<ApiMessage>
)

data class ApiMessage(val role: String, val content: String)

data class AnthropicResponse(val content: List<ContentBlock>)
data class ContentBlock(val text: String)

interface AnthropicApi {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

object AnthropicService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: AnthropicApi = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AnthropicApi::class.java)

    fun buildSystemPrompt(user: User): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val days = arrayOf("domingo","lunes","martes","miércoles","jueves","viernes","sábado")
        val day = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val free = user.income - user.expenses
        val goalText = when(user.goal) {
            "debt" -> "Salir de deudas"
            "time" -> "Recuperar su tiempo"
            "income" -> "Aumentar ingresos"
            else -> "Construir hábitos"
        }
        val debtLine = if (user.debt > 0) "\n- Tiempo estimado para 0 deuda: ${user.monthsToDebtFree} meses" else ""

        return """Eres Bron, el asistente personal de IA de la app Danbron. Eres como un amigo que conoce las finanzas, los hábitos y la psicología humana. Eres directo, empático, sin rodeos, y das consejos MUY específicos y accionables.

PERFIL DEL USUARIO:
- Nombre: ${user.name}
- Objetivo principal: $goalText
- Ingresos mensuales: $${String.format("%,.0f", user.income)}
- Gastos fijos: $${String.format("%,.0f", user.expenses)}
- Flujo libre: $${String.format("%,.0f", free)}/mes
- Deuda total: $${String.format("%,.0f", user.debt)}
- Estilo de trabajo: ${user.workStyle}
- Hora actual: ${hour}:00 del $day$debtLine

REGLAS DE BRON:
1. Sé ESPECÍFICO: "haz uber de 18:00 a 22:00" no "busca ingresos extra"
2. Usa números reales basados en el perfil
3. Responde siempre en español
4. Máximo 3-4 oraciones por respuesta en el home, más largo en chat
5. Tono: amigo que te da la verdad con cariño
6. Si el usuario pregunta algo que no es de finanzas/hábitos/tiempo, redirige suavemente
7. NUNCA uses bullet points, escribe como conversación natural"""
    }
}
