package com.danbron.app.system

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Comprehensive device controller for Android.
 * Handles contacts, system controls, device info, clipboard, location, alarms, calendar.
 */
class DeviceController(private val context: Context) {

    // ═══════════════════════════════════════════
    // CONTACTS — lookup by name, get phone number
    // ═══════════════════════════════════════════

    fun lookupContact(name: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return "NO_PERMISSION:Necesito permiso para leer contactos."
        }

        val results = mutableListOf<Pair<String, String>>() // name, number
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            while (it.moveToNext()) {
                val contactName = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                results.add(contactName to number)
            }
        }

        return when {
            results.isEmpty() -> "No encontre ningun contacto con '$name'."
            results.size == 1 -> "FOUND:${results[0].first}|${results[0].second}"
            else -> {
                val list = results.take(5).joinToString("\n") { "- ${it.first}: ${it.second}" }
                "MULTIPLE:Encontre ${results.size} contactos:\n$list"
            }
        }
    }

    fun listContacts(limit: Int = 20): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return "Necesito permiso para leer contactos."
        }

        val contacts = mutableListOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = context.contentResolver.query(
            uri, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            while (it.moveToNext() && contacts.size < limit) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                contacts.add("$name: $number")
            }
        }

        return if (contacts.isEmpty()) "No hay contactos guardados."
        else "Contactos:\n${contacts.joinToString("\n")}"
    }

    // ═══════════════════════════════════════════
    // DEVICE INFO — battery, storage, network
    // ═══════════════════════════════════════════

    fun getDeviceInfo(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGB = (stat.totalBytes / (1024.0 * 1024 * 1024))
        val freeGB = (stat.availableBytes / (1024.0 * 1024 * 1024))
        val usedGB = totalGB - freeGB

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val networkType = when {
            caps == null -> "Sin conexion"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos moviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Conectado"
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = (volume * 100) / maxVolume

        return buildString {
            append("Bateria: $batteryLevel%${if (isCharging) " (cargando)" else ""}\n")
            append("Almacenamiento: ${String.format("%.1f", usedGB)}GB usado / ${String.format("%.1f", totalGB)}GB total (${String.format("%.1f", freeGB)}GB libre)\n")
            append("Red: $networkType\n")
            append("Volumen: $volumePercent%\n")
            append("Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        }
    }

    // ═══════════════════════════════════════════
    // SYSTEM CONTROLS — volume, flashlight, brightness
    // ═══════════════════════════════════════════

    fun setVolume(percent: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (percent.coerceIn(0, 100) * maxVol) / 100
        am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        return "Volumen ajustado a $percent%."
    }

    fun toggleFlashlight(on: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return "No se encontro camara con flash."
            cm.setTorchMode(cameraId, on)
            if (on) "Linterna encendida." else "Linterna apagada."
        } catch (e: Exception) {
            "Error con la linterna: ${e.message}"
        }
    }

    fun setRingerMode(mode: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (mode.lowercase()) {
            "silencio", "silent", "mute" -> am.ringerMode = AudioManager.RINGER_MODE_SILENT
            "vibrar", "vibrate" -> am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            "normal", "sonido", "sound" -> am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            else -> return "Modo no reconocido. Usa: silencio, vibrar, o normal."
        }
        return "Modo de sonido cambiado a $mode."
    }

    fun openWifiSettings(): String {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Abri los ajustes de WiFi."
    }

    fun openBluetoothSettings(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Abri los ajustes de Bluetooth."
    }

    // ═══════════════════════════════════════════
    // CLIPBOARD
    // ═══════════════════════════════════════════

    fun copyToClipboard(text: String): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("bron", text)
        clipboard.setPrimaryClip(clip)
        return "Texto copiado al portapapeles."
    }

    fun readClipboard(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            "Portapapeles: $text"
        } else {
            "El portapapeles esta vacio."
        }
    }

    // ═══════════════════════════════════════════
    // LOCATION
    // ═══════════════════════════════════════════

    @Suppress("MissingPermission")
    suspend fun getLocation(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return "Necesito permiso de ubicacion."
        }

        return withContext(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val location: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (location == null) return@withContext "No pude obtener la ubicacion. Asegurate de tener el GPS activado."

                val lat = location.latitude
                val lon = location.longitude

                // Try to reverse geocode
                try {
                    val geocoder = Geocoder(context, Locale("es"))
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val parts = listOfNotNull(
                            addr.thoroughfare,
                            addr.subLocality,
                            addr.locality,
                            addr.adminArea,
                            addr.countryName
                        )
                        return@withContext "Ubicacion: ${parts.joinToString(", ")} (${String.format("%.4f", lat)}, ${String.format("%.4f", lon)})"
                    }
                } catch (_: Exception) { }

                "Ubicacion: ${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
            } catch (e: Exception) {
                "Error obteniendo ubicacion: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════
    // ALARMS & TIMERS
    // ═══════════════════════════════════════════

    fun setAlarm(hour: Int, minute: Int, label: String = ""): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Alarma configurada para las ${String.format("%02d:%02d", hour, minute)}${if (label.isNotBlank()) " ($label)" else ""}."
    }

    fun setTimer(seconds: Int, label: String = ""): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val mins = seconds / 60
        val secs = seconds % 60
        return "Timer configurado: ${if (mins > 0) "${mins}min " else ""}${if (secs > 0) "${secs}seg" else ""}${if (label.isNotBlank()) " ($label)" else ""}."
    }

    // ═══════════════════════════════════════════
    // CALENDAR — read upcoming events
    // ═══════════════════════════════════════════

    fun readCalendarEvents(daysAhead: Int = 7): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return "Necesito permiso para leer el calendario."
        }

        val events = mutableListOf<String>()
        val now = Calendar.getInstance()
        val later = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysAhead) }

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.timeInMillis.toString(), later.timeInMillis.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )
        val sdf = SimpleDateFormat("EEE dd/MM HH:mm", Locale("es"))
        cursor?.use {
            while (it.moveToNext() && events.size < 15) {
                val title = it.getString(0) ?: "Sin titulo"
                val start = it.getLong(1)
                val location = it.getString(3)
                val dateStr = sdf.format(Date(start))
                events.add("- $dateStr: $title${if (!location.isNullOrBlank()) " ($location)" else ""}")
            }
        }

        return if (events.isEmpty()) "No tienes eventos en los proximos $daysAhead dias."
        else "Eventos proximos:\n${events.joinToString("\n")}"
    }

    // ═══════════════════════════════════════════
    // CALL LOG — recent calls
    // ═══════════════════════════════════════════

    fun readCallLog(limit: Int = 10): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            return "Necesito permiso para leer el historial de llamadas."
        }

        val calls = mutableListOf<String>()
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection, null, null,
            "${CallLog.Calls.DATE} DESC"
        )
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale("es"))
        cursor?.use {
            while (it.moveToNext() && calls.size < limit) {
                val name = it.getString(0) ?: "Desconocido"
                val number = it.getString(1) ?: ""
                val type = when (it.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> "↓ Entrante"
                    CallLog.Calls.OUTGOING_TYPE -> "↑ Saliente"
                    CallLog.Calls.MISSED_TYPE -> "✗ Perdida"
                    else -> "?"
                }
                val date = sdf.format(Date(it.getLong(3)))
                val duration = it.getLong(4)
                val durStr = if (duration > 0) "${duration / 60}m${duration % 60}s" else ""
                calls.add("$type $name ($number) - $date ${if (durStr.isNotBlank()) "[$durStr]" else ""}")
            }
        }

        return if (calls.isEmpty()) "No hay llamadas recientes."
        else "Llamadas recientes:\n${calls.joinToString("\n")}"
    }

    // ═══════════════════════════════════════════
    // SHARE — share text/URL via Android share sheet
    // ═══════════════════════════════════════════

    fun shareText(text: String): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Abri el menu para compartir."
    }

    // ═══════════════════════════════════════════
    // OPEN SYSTEM SETTINGS
    // ═══════════════════════════════════════════

    fun openSettings(section: String): String {
        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sonido", "sound" -> Settings.ACTION_SOUND_SETTINGS
            "pantalla", "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "bateria", "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "almacenamiento", "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps", "aplicaciones" -> Settings.ACTION_APPLICATION_SETTINGS
            "ubicacion", "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "seguridad", "security" -> Settings.ACTION_SECURITY_SETTINGS
            "accesibilidad", "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notificaciones", "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Abri ajustes de $section."
    }
}
