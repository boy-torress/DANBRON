package com.danbron.app.notifications

import android.content.Context
import androidx.work.*
import com.danbron.app.data.UserPreferences
import com.danbron.app.data.models.User
import com.danbron.app.engine.AdaptiveEngine
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Bron proactive notification worker — runs every 4 hours.
 * Now uses the AdaptiveEngine to send segment-aware messages.
 */
class BronNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        val user = prefs.user.first() ?: return Result.success()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val segment = AdaptiveEngine.classifyUser(user)

        val message = getContextualMessage(user, hour, segment.segment.name)
        val title = getTitle(hour)

        NotificationHelper.showBronMessage(
            applicationContext,
            title = title,
            message = message,
            notificationId = 1000 + hour
        )

        return Result.success()
    }

    private fun getTitle(hour: Int): String = when {
        hour < 12 -> "☀️ Bron · Buenos días"
        hour < 18 -> "💪 Bron · Tarde productiva"
        hour < 22 -> "🌙 Bron · Mensaje nocturno"
        else -> "😴 Bron · Hora de descansar"
    }

    private fun getContextualMessage(user: User, hour: Int, segmentName: String): String {
        val name = user.name.ifBlank { "amigo" }
        val free = user.freeFlow
        val hasDebt = user.debt > 0
        val isUnemployed = user.employmentStatus == "unemployed"
        val isParent = user.lifeRole == "parent"
        val hasHealthFocus = user.healthFocus != "none"

        return when {
            // Morning motivation
            hour in 6..9 -> when {
                isUnemployed -> "$name, hoy es un día para avanzar. Revisa ofertas de empleo, actualiza tu CV y no pierdas la fe. Cada paso cuenta 💪"
                isParent && hasDebt -> "$name, sé que la mañana es caótica con los niños. Pero dedica 5 min a revisar tu plan financiero del día. Tu familia lo merece."
                hasDebt -> "$name, hoy es un día más cerca de $0 deuda. Recuerda: cada peso cuenta. ¿Ya revisaste tus tareas del día?"
                hasHealthFocus -> "Buenos días $name 💪 Arranca con 15 min de movimiento. Tu cuerpo te lo agradece. ¡Hoy es tu día!"
                else -> "Buenos días $name 💪 Arrancamos con energía. Abre tu plan de hoy y conquista cada tarea."
            }
            // Midday
            hour in 10..13 -> when {
                isUnemployed -> "$name, ¿ya enviaste postulaciones hoy? LinkedIn, Computrabajo, Indeed... cada CV enviado es una oportunidad más."
                isParent -> "$name, aprovecha este rato para adelantar algo mientras puedas. 15 min de foco valen oro cuando tienes familia."
                hasDebt -> "¿Ya almorzaste? Cocinar en casa te ahorra ~\$${String.format("%,.0f", free * 0.1)} al mes vs delivery. Tu yo del futuro te lo agradece."
                else -> "$name, ¿cómo vas con las tareas de hoy? Cada hábito completado es un ladrillo más. No pares."
            }
            // Afternoon
            hour in 14..17 -> when {
                isUnemployed -> "Son las $hour:00, $name. ¿Qué tal aprender algo nuevo? Un curso gratis de 30 min puede marcar la diferencia en tu próxima entrevista."
                hasHealthFocus -> "$name, ¿ya hiciste ejercicio hoy? Aunque sean 20 min de caminata. Tu cuerpo y mente te lo agradecen."
                user.workStyle in listOf("freelance", "part_time") -> "Son las $hour:00, $name. 2 horas de foco sin teléfono = $$ en tu bolsillo. ¿Arrancamos?"
                else -> "$name, la tarde es tuya. ¿Ya completaste tu hábito de hoy? No dejes que se rompa la racha 🔥"
            }
            // Evening
            hour in 18..21 -> when {
                isParent -> "$name, tiempo con la familia es sagrado. Deja el teléfono y disfruta. Mañana seguimos con todo."
                hasDebt && user.workStyle in listOf("freelance", "part_time") -> "🚗 Alta demanda en apps de delivery/transporte ahora. 3-4 horas pueden ser \$${String.format("%,.0f", free * 0.15)} extra esta semana."
                isUnemployed -> "$name, descansa tu mente. Mañana seguimos buscando con energía renovada. No te rindas."
                else -> "$name, antes de cerrar el día revisa tu progreso. ¿Completaste todas las tareas? Cuéntale a Bron cómo te fue."
            }
            // Night
            else -> when {
                hasHealthFocus -> "Hora de descansar, $name. El sueño es medicina. Pantallas off, respira profundo y a dormir. Mañana vamos con todo ✨"
                else -> "Hora de descansar, $name. Mañana seguimos. Dormir bien = mejor productividad = mejor vida. Nos vemos en la mañana ✨"
            }
        }
    }

    companion object {
        private const val WORK_NAME = "bron_notifications"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BronNotificationWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
