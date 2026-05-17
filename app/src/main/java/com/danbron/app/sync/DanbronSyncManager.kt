package com.danbron.app.sync

import android.content.Context
import retrofit2.Retrofit
import com.danbron.app.BuildConfig
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.google.gson.JsonObject

// API Service
interface DanbronSyncApi {
    @POST("auth")
    suspend fun auth(
        @Body body: AuthRequest
    ): AuthResponse

    @POST("devices/register")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body body: DeviceRegisterRequest
    ): DeviceRegisterResponse

    @POST("devices/pair")
    suspend fun pairDevices(
        @Header("Authorization") token: String,
        @Body body: DevicePairRequest
    ): DevicePairResponse

    @POST("devices/pairing-code")
    suspend fun refreshPairingCode(
        @Header("Authorization") token: String,
        @Body body: DevicePairingCodeRequest
    ): DeviceRegisterResponse

    @GET("devices/paired")
    suspend fun getPairedDevice(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String
    ): PairedDeviceResponse

    @POST("devices/auto-pair")
    suspend fun autoPairDevices(
        @Header("Authorization") token: String,
        @Body body: AutoPairRequest
    ): AutoPairResponse

    @POST("devices/unpair")
    suspend fun unpairDevices(
        @Header("Authorization") token: String,
        @Body body: UnpairRequest
    ): UnpairResponse

    @POST("sync/data")
    suspend fun syncData(
        @Header("Authorization") token: String,
        @Body body: SyncDataRequest
    ): SyncDataResponse

    @GET("sync/data")
    suspend fun getSyncData(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String
    ): GetSyncDataResponse

    @POST("sync/state")
    suspend fun syncSharedState(
        @Header("Authorization") token: String,
        @Body body: SyncStateRequest
    ): SyncStateResponse

    @GET("sync/state")
    suspend fun getSharedState(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String
    ): SyncStateResponse

    @POST("sync/event")
    suspend fun recordEvent(
        @Header("Authorization") token: String,
        @Body body: RecordEventRequest
    ): RecordEventResponse

    @GET("sync/events")
    suspend fun getEvents(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String,
        @Query("limit") limit: Int = 50
    ): GetEventsResponse

    @GET("sync/events/since")
    suspend fun getEventsSince(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String,
        @Query("since") since: String
    ): GetEventsResponse
}

// Request/Response models
data class AuthRequest(
    val email: String,
    val name: String? = null
)

data class AuthResponse(
    val token: String,
    val user: UserInfo
)

data class UserInfo(
    val id: String,
    val email: String,
    val name: String
)

data class DeviceRegisterRequest(
    val deviceType: String,
    val deviceName: String
)

data class DeviceRegisterResponse(
    val device: DeviceInfo
)

data class DeviceInfo(
    val id: String,
    val pairingCode: String,
    val deviceType: String,
    val deviceName: String
)

data class DevicePairRequest(
    val pairingCode: String,
    val confirmedDeviceId: String
)

data class DevicePairingCodeRequest(
    val deviceId: String
)

data class DevicePairResponse(
    val paired: Boolean,
    val pairing: PairingInfo? = null
)

data class PairingInfo(
    val id: String,
    val device_1: String,
    val device_2: String
)

data class PairedDeviceResponse(
    val paired: Boolean,
    val device: PairedDeviceInfo? = null
)

data class PairedDeviceInfo(
    val id: String,
    val deviceType: String,
    val deviceName: String,
    val lastSync: String
)

data class AutoPairRequest(
    val deviceId: String
)

data class AutoPairResponse(
    val paired: Boolean,
    val otherDevice: AutoPairDeviceInfo? = null,
    val message: String? = null
)

data class AutoPairDeviceInfo(
    val id: String,
    val device_type: String? = null,
    val device_name: String? = null
)

data class UnpairRequest(
    val deviceId: String
)

data class UnpairResponse(
    val unpaired: Boolean
)

data class SyncDataRequest(
    val deviceId: String,
    val userData: JsonObject
)

data class SyncDataResponse(
    val synced: Boolean,
    val data: JsonObject? = null
)

data class GetSyncDataResponse(
    val data: JsonObject? = null
)

data class SyncStateRequest(
    val deviceId: String,
    val state: JsonObject
)

data class SyncStateResponse(
    val synced: Boolean = false,
    val state: JsonObject? = null
)

data class RecordEventRequest(
    val deviceId: String,
    val eventType: String,
    val eventData: JsonObject? = null
)

data class RecordEventResponse(
    val recorded: Boolean,
    val event: EventInfo? = null
)

data class EventInfo(
    val id: String,
    val event_type: String,
    val event_data: JsonObject?,
    val timestamp: String
)

data class GetEventsResponse(
    val events: List<EventInfo>
)

// Sync Manager
class DanbronSyncManager(context: Context) {
    private val api: DanbronSyncApi
    private val prefs = context.getSharedPreferences("danbron_backend_sync", Context.MODE_PRIVATE)
    private val authToken = mutableListOf<String?>()
    private val userId = mutableListOf<String?>()
    private val deviceId = mutableListOf<String?>()
    private val pairedDeviceId = mutableListOf<String?>()
    private val pairingCode = mutableListOf<String?>()

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
    }

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(DanbronSyncApi::class.java)
        authToken.add(prefs.getString(KEY_AUTH_TOKEN, null))
        userId.add(prefs.getString(KEY_USER_ID, null))
        deviceId.add(prefs.getString(KEY_DEVICE_ID, null))
        pairedDeviceId.add(prefs.getString(KEY_PAIRED_DEVICE_ID, null))
        pairingCode.add(prefs.getString(KEY_PAIRING_CODE, null))
    }

    suspend fun authenticate(email: String, name: String) {
        try {
            val response = api.auth(AuthRequest(email, name))
            authToken.clear()
            authToken.add(response.token)
            userId.clear()
            userId.add(response.user.id)
            prefs.edit()
                .putString(KEY_AUTH_TOKEN, response.token)
                .putString(KEY_USER_ID, response.user.id)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
                .apply()
        } catch (e: Exception) {
            throw Exception("Authentication failed: ${e.message}")
        }
    }

    suspend fun registerDevice(deviceType: String, deviceName: String): String {
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val response = api.registerDevice("Bearer $token", DeviceRegisterRequest(deviceType, deviceName))
            deviceId.clear()
            deviceId.add(response.device.id)
            pairingCode.clear()
            pairingCode.add(response.device.pairingCode)
            prefs.edit()
                .putString(KEY_DEVICE_ID, response.device.id)
                .putString(KEY_PAIRING_CODE, response.device.pairingCode)
                .apply()
            response.device.pairingCode
        } catch (e: Exception) {
            throw Exception("Device registration failed: ${e.message}")
        }
    }

    suspend fun refreshPairingCode(): String {
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            val response = api.refreshPairingCode("Bearer $token", DevicePairingCodeRequest(devId))
            pairingCode.clear()
            pairingCode.add(response.device.pairingCode)
            prefs.edit().putString(KEY_PAIRING_CODE, response.device.pairingCode).apply()
            response.device.pairingCode
        } catch (e: Exception) {
            throw Exception("Pairing code refresh failed: ${e.message}")
        }
    }

    suspend fun pairDevice(pairingCode: String) {
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            val response = api.pairDevices("Bearer $token", DevicePairRequest(pairingCode, devId))
            val pair = response.pairing
            val other = when (devId) {
                pair?.device_1 -> pair.device_2
                pair?.device_2 -> pair.device_1
                else -> null
            }
            pairedDeviceId.clear()
            pairedDeviceId.add(other)
            prefs.edit().putString(KEY_PAIRED_DEVICE_ID, other).apply()
        } catch (e: Exception) {
            throw Exception("Device pairing failed: ${e.message}")
        }
    }

    suspend fun getPairedDevice(): String? {
        return try {
            val token = authToken.firstOrNull() ?: return null
            val devId = deviceId.firstOrNull() ?: return null
            val response = api.getPairedDevice("Bearer $token", devId)
            val pairedId = response.device?.id
            if (pairedId != null) {
                pairedDeviceId.clear()
                pairedDeviceId.add(pairedId)
                prefs.edit().putString(KEY_PAIRED_DEVICE_ID, pairedId).apply()
            } else {
                pairedDeviceId.clear()
                pairedDeviceId.add(null)
                prefs.edit().remove(KEY_PAIRED_DEVICE_ID).apply()
            }
            pairedId
        } catch (e: Exception) {
            null
        }
    }

    suspend fun autoPairByEmail(email: String, name: String): Boolean {
        // Step 1: Authenticate with this email
        authenticate(email, name)
        // Step 2: Register device if needed
        if (currentDeviceId() == null) {
            registerDevice("android", android.os.Build.MODEL)
        }
        // Step 3: Call auto-pair endpoint
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            val response = api.autoPairDevices("Bearer $token", AutoPairRequest(devId))
            if (response.paired && response.otherDevice != null) {
                pairedDeviceId.clear()
                pairedDeviceId.add(response.otherDevice.id)
                prefs.edit().putString(KEY_PAIRED_DEVICE_ID, response.otherDevice.id).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncMgr", "Auto-pair failed", e)
            false
        }
    }

    suspend fun unpairDevice() {
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            api.unpairDevices("Bearer $token", UnpairRequest(devId))
            deviceId.clear()
            pairedDeviceId.clear()
            prefs.edit()
                .remove(KEY_DEVICE_ID)
                .remove(KEY_PAIRED_DEVICE_ID)
                .apply()
        } catch (e: Exception) {
            throw Exception("Device unpair failed: ${e.message}")
        }
    }

    suspend fun syncUserData(data: JsonObject) {
        try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            api.syncData("Bearer $token", SyncDataRequest(devId, data))
        } catch (e: Exception) {
            throw Exception("Data sync failed: ${e.message}")
        }
    }

    suspend fun getPairedUserData(): JsonObject? {
        return try {
            val token = authToken.firstOrNull() ?: return null
            val devId = deviceId.firstOrNull() ?: return null
            val response = api.getSyncData("Bearer $token", devId)
            response.data
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncSharedState(state: JsonObject): JsonObject? {
        return try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            api.syncSharedState("Bearer $token", SyncStateRequest(devId, state)).state
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSharedState(): JsonObject? {
        return try {
            val token = authToken.firstOrNull() ?: return null
            val devId = deviceId.firstOrNull() ?: return null
            api.getSharedState("Bearer $token", devId).state
        } catch (e: Exception) {
            null
        }
    }

    suspend fun recordSystemEvent(eventType: String, eventData: JsonObject) {
        try {
            val token = authToken.firstOrNull() ?: throw Exception("No auth token")
            val devId = deviceId.firstOrNull() ?: throw Exception("No device ID")
            api.recordEvent("Bearer $token", RecordEventRequest(devId, eventType, eventData))
        } catch (e: Exception) {
            // Silently fail for event recording
        }
    }

    suspend fun getRecentEvents(): List<JsonObject> {
        return try {
            val token = authToken.firstOrNull() ?: return emptyList()
            val devId = deviceId.firstOrNull() ?: return emptyList()
            val response = api.getEvents("Bearer $token", devId)
            response.events.mapNotNull { it.event_data }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRemoteCommandEvents(limit: Int = 25): List<EventInfo> {
        return try {
            val token = authToken.firstOrNull() ?: return emptyList()
            val devId = deviceId.firstOrNull() ?: return emptyList()
            api.getEvents("Bearer $token", devId, limit).events
                .filter { it.event_type == "remote_command" && it.event_data != null }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendRemoteCommand(
        target: String,
        action: String,
        args: JsonObject,
        requiresConfirmation: Boolean = false
    ): String? {
        return try {
            val id = "cmd_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val eventData = JsonObject().apply {
                addProperty("id", id)
                addProperty("sourceDeviceId", deviceId.firstOrNull())
                addProperty("target", target)
                addProperty("action", action)
                add("args", args)
                addProperty("status", "queued")
                addProperty("requiresConfirmation", requiresConfirmation)
                addProperty("createdAt", System.currentTimeMillis())
            }
            recordSystemEvent("remote_command", eventData)
            id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun recordCommandResult(commandId: String, status: String, result: JsonObject) {
        val eventData = JsonObject().apply {
            addProperty("commandId", commandId)
            addProperty("status", status)
            add("result", result)
            addProperty("completedAt", System.currentTimeMillis())
        }
        recordSystemEvent("remote_command_result", eventData)
    }

    fun hasPairedDevice(): Boolean = !pairedDeviceId.firstOrNull().isNullOrBlank()

    fun currentDeviceId(): String? = deviceId.firstOrNull()

    fun currentPairedDeviceId(): String? = pairedDeviceId.firstOrNull()

    fun currentPairingCode(): String? = pairingCode.firstOrNull()

    fun getAuthToken(): String? = authToken.firstOrNull()

    fun isAuthenticated(): Boolean = !authToken.firstOrNull().isNullOrBlank()

    fun configureFromExistingSession(
        token: String,
        configuredUserId: String,
        configuredDeviceId: String,
        configuredPairedDeviceId: String? = null,
        email: String? = null,
        name: String? = null
    ) {
        authToken.clear()
        authToken.add(token)
        userId.clear()
        userId.add(configuredUserId)
        deviceId.clear()
        deviceId.add(configuredDeviceId)
        pairedDeviceId.clear()
        pairedDeviceId.add(configuredPairedDeviceId)

        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ID, configuredUserId)
            .putString(KEY_DEVICE_ID, configuredDeviceId)
            .putString(KEY_PAIRED_DEVICE_ID, configuredPairedDeviceId)
            .apply()
        prefs.edit()
            .apply {
                if (email != null) putString(KEY_EMAIL, email)
                if (name != null) putString(KEY_NAME, name)
            }
            .apply()
    }
}
