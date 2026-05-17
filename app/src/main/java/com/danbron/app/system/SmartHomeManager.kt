package com.danbron.app.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Smart Home controller — supports Home Assistant, and generic HTTP-based IoT devices.
 * User configures their Home Assistant URL + long-lived access token in SharedPreferences.
 *
 * Supported actions:
 * - turn_on / turn_off → switches, lights, plugs, fans
 * - toggle → flip state
 * - set_brightness → lights (0-255)
 * - set_temperature → climate (thermostats)
 * - lock / unlock → smart locks
 * - list_devices → get all entities
 */
class SmartHomeManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("danbron_smarthome", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean {
        return getBaseUrl().isNotBlank() && getToken().isNotBlank()
    }

    fun getBaseUrl(): String = prefs.getString("ha_url", "").orEmpty().trimEnd('/')
    fun getToken(): String = prefs.getString("ha_token", "").orEmpty()

    fun configure(url: String, token: String) {
        prefs.edit()
            .putString("ha_url", url.trimEnd('/'))
            .putString("ha_token", token)
            .apply()
    }

    suspend fun executeAction(action: String, entityId: String, extraData: Map<String, Any> = emptyMap()): String {
        if (!isConfigured()) return "Smart Home no configurado. El usuario debe agregar su URL y token de Home Assistant en ajustes."

        return withContext(Dispatchers.IO) {
            try {
                when (action.lowercase()) {
                    "turn_on", "encender", "prender" -> callService("homeassistant", "turn_on", entityId)
                    "turn_off", "apagar" -> callService("homeassistant", "turn_off", entityId)
                    "toggle", "cambiar" -> callService("homeassistant", "toggle", entityId)
                    "set_brightness", "brillo" -> {
                        val brightness = extraData["brightness"]?.toString()?.toIntOrNull() ?: 128
                        callService("light", "turn_on", entityId, """{"brightness": $brightness}""")
                    }
                    "set_temperature", "temperatura" -> {
                        val temp = extraData["temperature"]?.toString()?.toDoubleOrNull() ?: 22.0
                        callService("climate", "set_temperature", entityId, """{"temperature": $temp}""")
                    }
                    "lock", "cerrar" -> callService("lock", "lock", entityId)
                    "unlock", "abrir" -> callService("lock", "unlock", entityId)
                    "list", "listar", "dispositivos" -> listDevices()
                    "status", "estado" -> getEntityState(entityId)
                    else -> "Accion '$action' no reconocida para Smart Home."
                }
            } catch (e: Exception) {
                "Error con Smart Home: ${e.message}"
            }
        }
    }

    private fun callService(domain: String, service: String, entityId: String, extraJson: String = "{}"): String {
        val url = URL("${getBaseUrl()}/api/services/$domain/$service")
        val body = if (extraJson == "{}") {
            """{"entity_id": "$entityId"}"""
        } else {
            val extra = extraJson.trimStart('{').trimEnd('}')
            """{"entity_id": "$entityId", $extra}"""
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${getToken()}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
        }

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        return if (code in 200..299) {
            "Listo, ejecute '$service' en $entityId."
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText().orEmpty().take(200)
            "Error ($code) al ejecutar '$service' en $entityId: $error"
        }
    }

    private fun listDevices(): String {
        val url = URL("${getBaseUrl()}/api/states")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${getToken()}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
        }

        val code = conn.responseCode
        if (code !in 200..299) return "Error ($code) al listar dispositivos."

        val response = conn.inputStream.bufferedReader().readText()
        // Parse basic entity IDs and friendly names
        val pattern = Regex(""""entity_id"\s*:\s*"([^"]+)".*?"friendly_name"\s*:\s*"([^"]+)"""")
        val matches = pattern.findAll(response).take(30).toList()

        return if (matches.isEmpty()) {
            "No encontre dispositivos en Home Assistant."
        } else {
            "Dispositivos encontrados:\n" + matches.joinToString("\n") {
                "- ${it.groupValues[2]} (${it.groupValues[1]})"
            }
        }
    }

    private fun getEntityState(entityId: String): String {
        if (entityId.isBlank()) return "Necesito el ID del dispositivo."
        val url = URL("${getBaseUrl()}/api/states/$entityId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${getToken()}")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
        }

        val code = conn.responseCode
        if (code !in 200..299) return "Error ($code) al consultar estado de $entityId."

        val response = conn.inputStream.bufferedReader().readText()
        val stateMatch = Regex(""""state"\s*:\s*"([^"]+)"""").find(response)
        val nameMatch = Regex(""""friendly_name"\s*:\s*"([^"]+)"""").find(response)

        val state = stateMatch?.groupValues?.get(1) ?: "desconocido"
        val name = nameMatch?.groupValues?.get(1) ?: entityId

        return "$name esta: $state"
    }
}
