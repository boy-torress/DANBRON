package com.danbron.app.sync

import android.content.Context
import com.danbron.app.data.UserPreferences
import retrofit2.Retrofit
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

    @GET("devices/paired")
    suspend fun getPairedDevice(
        @Header("Authorization") token: String,
        @Query("deviceId") deviceId: String
    ): PairedDeviceResponse

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
    private val prefs = UserPreferences(context)
    private val api: DanbronSyncApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL) // Add to build.gradle: buildConfigField "String", "BACKEND_URL", "\"https://your-backend.com/api/\""
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(DanbronSyncApi::class.java)
    }

    suspend fun authenticate(email: String, name: String) {
        try {
            val response = api.auth(AuthRequest(email, name))
            prefs.setAuthToken(response.token)
            prefs.setUserId(response.user.id)
        } catch (e: Exception) {
            throw Exception("Authentication failed: ${e.message}")
        }
    }

    suspend fun registerDevice(deviceType: String, deviceName: String): String {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val response = api.registerDevice("Bearer $token", DeviceRegisterRequest(deviceType, deviceName))
            prefs.setDeviceId(response.device.id)
            response.device.pairingCode
        } catch (e: Exception) {
            throw Exception("Device registration failed: ${e.message}")
        }
    }

    suspend fun pairDevice(pairingCode: String, confirmedDeviceId: String) {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            api.pairDevices("Bearer $token", DevicePairRequest(pairingCode, confirmedDeviceId))
        } catch (e: Exception) {
            throw Exception("Pairing failed: ${e.message}")
        }
    }

    suspend fun getPairedDevice(): PairedDeviceInfo? {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            val response = api.getPairedDevice("Bearer $token", deviceId)
            response.device
        } catch (e: Exception) {
            null
        }
    }

    suspend fun unpairDevice() {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            api.unpairDevices("Bearer $token", UnpairRequest(deviceId))
            prefs.clearPairedDeviceId()
        } catch (e: Exception) {
            throw Exception("Unpairing failed: ${e.message}")
        }
    }

    suspend fun syncUserData(userData: JsonObject) {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            api.syncData("Bearer $token", SyncDataRequest(deviceId, userData))
        } catch (e: Exception) {
            throw Exception("Sync failed: ${e.message}")
        }
    }

    suspend fun getPairedUserData(): JsonObject? {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            val response = api.getSyncData("Bearer $token", deviceId)
            response.data
        } catch (e: Exception) {
            null
        }
    }

    suspend fun recordSystemEvent(eventType: String, eventData: JsonObject? = null) {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            api.recordEvent("Bearer $token", RecordEventRequest(deviceId, eventType, eventData))
        } catch (e: Exception) {
            // Silently fail for events
        }
    }

    suspend fun getRecentEvents(limit: Int = 50): List<EventInfo> {
        return try {
            val token = prefs.getAuthToken() ?: throw Exception("No auth token")
            val deviceId = prefs.getDeviceId() ?: throw Exception("No device ID")
            val response = api.getEvents("Bearer $token", deviceId, limit)
            response.events
        } catch (e: Exception) {
            emptyList()
        }
    }
}
