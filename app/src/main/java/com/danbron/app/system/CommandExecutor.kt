package com.danbron.app.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.danbron.app.sync.DanbronSyncManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CommandExecutor(private val context: Context) {
    private val syncManager = DanbronSyncManager(context)
    private val prefs = context.getSharedPreferences("danbron_command_executor", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROCESSED = "processed_command_ids"
    }

    suspend fun processRemoteCommands() {
        val processed = processedIds().toMutableSet()
        val commands = syncManager.getRemoteCommandEvents()

        for (event in commands) {
            val data = event.event_data ?: continue
            val id = data.get("id")?.asString ?: continue
            if (processed.contains(id)) continue

            val target = data.get("target")?.asString ?: "android"
            if (target !in listOf("android", "phone", "telefono", "celular", "paired")) continue

            processed.add(id)
            saveProcessedIds(processed)

            val result = runCatching { execute(data) }
            val payload = JsonObject().apply {
                if (result.isSuccess) {
                    addProperty("message", result.getOrNull() ?: "Comando ejecutado")
                } else {
                    addProperty("error", result.exceptionOrNull()?.message ?: "Error desconocido")
                }
            }
            syncManager.recordCommandResult(
                commandId = id,
                status = if (result.isSuccess) "completed" else "failed",
                result = payload
            )
        }
    }

    suspend fun execute(command: JsonObject): String = withContext(Dispatchers.Main) {
        val action = command.get("action")?.asString ?: throw IllegalArgumentException("Sin accion")
        val args = command.getAsJsonObject("args") ?: JsonObject()

        when (action) {
            "sequence" -> executeSequence(args)
            "open_app" -> openApp(args.get("app")?.asString.orEmpty())
            "open_url" -> openUrl(args.get("url")?.asString.orEmpty())
            "compose_whatsapp" -> composeWhatsApp(args.get("message")?.asString.orEmpty())
            "compose_email" -> composeEmail(
                args.get("to")?.asString.orEmpty(),
                args.get("subject")?.asString.orEmpty(),
                args.get("body")?.asString.orEmpty()
            )
            "send_email" -> composeEmail(
                args.get("to")?.asString.orEmpty(),
                args.get("subject")?.asString.orEmpty(),
                args.get("body")?.asString.orEmpty()
            )
            "create_calendar_event" -> createCalendarEvent(
                args.get("title")?.asString.orEmpty(),
                args.get("details")?.asString.orEmpty()
            )
            "play_music" -> playMusic(args.get("query")?.asString.orEmpty())
            "open_maps" -> openMaps(args.get("query")?.asString.orEmpty())
            "make_call" -> makeCall(args.get("number")?.asString.orEmpty())
            "send_sms" -> sendSms(
                args.get("number")?.asString.orEmpty(),
                args.get("message")?.asString.orEmpty()
            )
            "take_photo" -> takePhoto()
            else -> throw IllegalArgumentException("Accion no soportada en Android: $action")
        }
    }

    private suspend fun executeSequence(args: JsonObject): String {
        val commands = args.getAsJsonArray("commands")
            ?: throw IllegalArgumentException("Secuencia sin comandos")
        val results = mutableListOf<String>()

        for ((index, item) in commands.withIndex()) {
            val child = item.asJsonObject
            val action = child.get("action")?.asString ?: continue
            val childPayload = JsonObject().apply {
                addProperty("id", "sequence_${System.currentTimeMillis()}_$index")
                addProperty("target", "android")
                addProperty("action", action)
                add("args", child.getAsJsonObject("args") ?: JsonObject())
            }
            results.add(execute(childPayload))
            if (index < commands.size() - 1) delay(900)
        }

        return results.joinToString(" Luego ")
    }

    private fun openApp(app: String): String {
        val packageNames = when (app.lowercase()) {
            "whatsapp", "wsp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
            "gmail" -> listOf("com.google.android.gm")
            "youtube" -> listOf("com.google.android.youtube")
            "chrome" -> listOf("com.android.chrome")
            "settings", "configuracion", "ajustes" -> listOf("com.android.settings")
            "calendar", "calendario" -> listOf("com.google.android.calendar")
            "drive" -> listOf("com.google.android.apps.docs")
            "docs", "documentos" -> listOf("com.google.android.apps.docs.editors.docs")
            "sheets", "hojas" -> listOf("com.google.android.apps.docs.editors.sheets")
            "contacts", "contactos" -> listOf("com.google.android.contacts")
            "maps", "mapa" -> listOf("com.google.android.apps.maps")
            "spotify" -> listOf("com.spotify.music")
            "music", "musica" -> listOf("com.google.android.apps.youtube.music")
            "uber" -> listOf("com.ubercab", "com.ubercab.driver")
            "uber driver", "uber drive", "uber conductor" -> listOf("com.ubercab.driver", "com.ubercab")
            "instagram", "ig" -> listOf("com.instagram.android")
            "tiktok" -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            "telegram", "tg" -> listOf("org.telegram.messenger")
            "facebook", "fb" -> listOf("com.facebook.katana")
            "twitter", "x" -> listOf("com.twitter.android")
            "netflix" -> listOf("com.netflix.mediaclient")
            "camera", "camara" -> listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera")
            "calculator", "calculadora" -> listOf("com.google.android.calculator", "com.android.calculator2")
            "clock", "reloj", "alarma" -> listOf("com.google.android.deskclock")
            "files", "archivos" -> listOf("com.google.android.apps.nbu.files", "com.android.documentsui")
            "photos", "fotos" -> listOf("com.google.android.apps.photos")
            else -> listOf(app)
        }

        val launchIntent = packageNames.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
            ?: throw ActivityNotFoundException("No encontre la app $app instalada")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return "Abri $app"
    }

    private fun openUrl(url: String): String {
        require(url.isNotBlank()) { "URL vacia" }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Abri $url"
    }

    private fun composeWhatsApp(message: String): String {
        require(message.isNotBlank()) { "Mensaje de WhatsApp vacio" }

        for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(packageName)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return "Prepare WhatsApp con el mensaje. Falta elegir chat y confirmar envio."
            }
        }

        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(message)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
        return "Abri WhatsApp Web con el mensaje preparado. Falta elegir chat y confirmar envio."
    }

    private fun composeEmail(to: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, if (to.isNotBlank()) arrayOf(to) else emptyArray<String>())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Prepare un correo. Falta revisar y confirmar envio."
    }

    private fun createCalendarEvent(title: String, details: String): String {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = Uri.parse("content://com.android.calendar/events")
            putExtra("title", title.ifBlank { "Nuevo evento" })
            putExtra("description", details)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Prepare un evento de calendario. Falta revisar y guardar."
    }

    private fun playMusic(query: String): String {
        val url = if (query.isBlank()) {
            "https://music.youtube.com/"
        } else {
            "https://music.youtube.com/search?q=${Uri.encode(query)}"
        }
        return openUrl(url)
    }

    private fun openMaps(query: String): String {
        val url = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
        return openUrl(url)
    }

    private fun makeCall(number: String): String {
        require(number.isNotBlank()) { "Numero de telefono vacio" }
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Llamando a $cleanNumber..."
        } else {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Abri el marcador con el numero $cleanNumber. Falta permiso para llamar automaticamente."
        }
    }

    private fun sendSms(number: String, message: String): String {
        require(number.isNotBlank()) { "Numero de telefono vacio" }
        require(message.isNotBlank()) { "Mensaje SMS vacio" }
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
                return "SMS enviado a $cleanNumber."
            } catch (e: Exception) {
                return "Error enviando SMS: ${e.message}"
            }
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanNumber")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Abri la app de SMS con el mensaje. Falta permiso para enviar automaticamente."
        }
    }

    private fun takePhoto(): String {
        val cameraPackages = listOf(
            "com.android.camera", "com.google.android.GoogleCamera",
            "com.sec.android.app.camera", "com.samsung.android.camera"
        )
        val launchIntent = cameraPackages.firstNotNullOfOrNull {
            context.packageManager.getLaunchIntentForPackage(it)
        } ?: context.packageManager.getLaunchIntentForPackage("com.android.camera2")

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return "Abri la camara. Usa [TOOL:TAP] para tomar la foto."
        }
        // Fallback: generic camera intent
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Abri la camara."
    }

    private fun processedIds(): Set<String> {
        return prefs.getStringSet(KEY_PROCESSED, emptySet()) ?: emptySet()
    }

    private fun saveProcessedIds(ids: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_PROCESSED, ids.toList().takeLast(100).toSet())
            .apply()
    }
}
