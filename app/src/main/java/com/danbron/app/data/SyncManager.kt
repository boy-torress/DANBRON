package com.danbron.app.data

import android.util.Log
import com.danbron.app.data.models.User
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SyncManager {
    private const val TAG = "SyncManager"
    private const val BASE_URL = "https://ntfy.sh/danbron_sync_"

    suspend fun pushProfile(syncCode: String, user: User) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(BASE_URL + syncCode)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                
                // Add a title to the ntfy message
                connection.setRequestProperty("Title", "Danbron Sync Profile")
                
                val jsonPayload = Gson().toJson(user)
                
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Sync Push Response: $responseCode")
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push sync profile", e)
            }
        }
    }
}
