package com.danbron.app.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.accessibility.AccessibilityEvent
import com.danbron.app.sync.DanbronSyncManager
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System events tracker for Android
 * Monitors app usage, emails, messages, and notifications
 * Requires ACCESSIBILITY_SERVICE and READ_CONTACTS permissions
 */
class SystemEventsTracker(private val context: Context) {
    private val syncManager = DanbronSyncManager(context)
    private var lastTrackedApp: String = ""

    /**
     * Track when an app is opened
     * Should be called from AccessibilityService or AppLifecycleObserver
     */
    suspend fun trackAppOpened(packageName: String, appName: String) {
        if (lastTrackedApp == packageName) return // Prevent duplicate events

        lastTrackedApp = packageName

        val eventData = JsonObject().apply {
            addProperty("packageName", packageName)
            addProperty("appName", appName)
            addProperty("timestamp", System.currentTimeMillis())
        }

        syncManager.recordSystemEvent("app_opened", eventData)
    }

    /**
     * Track email or message events
     */
    suspend fun trackEmailReceived(sender: String, subject: String? = null) {
        val eventData = JsonObject().apply {
            addProperty("sender", sender)
            addProperty("subject", subject ?: "No subject")
            addProperty("timestamp", System.currentTimeMillis())
        }

        syncManager.recordSystemEvent("email_received", eventData)
    }

    /**
     * Track pending notifications/tasks
     */
    suspend fun trackPendingTask(taskType: String, taskData: Map<String, String>) {
        val eventData = JsonObject().apply {
            taskData.forEach { (key, value) ->
                addProperty(key, value)
            }
            addProperty("taskType", taskType)
            addProperty("timestamp", System.currentTimeMillis())
        }

        syncManager.recordSystemEvent("pending_task", eventData)
    }

    /**
     * Track battery status
     */
    suspend fun trackBatteryStatus(level: Int, isCharging: Boolean) {
        val eventData = JsonObject().apply {
            addProperty("level", level)
            addProperty("isCharging", isCharging)
            addProperty("timestamp", System.currentTimeMillis())
        }

        syncManager.recordSystemEvent("battery_status", eventData)
    }

    /**
     * Track screen time
     */
    suspend fun trackScreenTime(seconds: Int) {
        val eventData = JsonObject().apply {
            addProperty("screenTimeSeconds", seconds)
            addProperty("timestamp", System.currentTimeMillis())
        }

        syncManager.recordSystemEvent("screen_time", eventData)
    }
}

/**
 * Accessibility Service to track app usage
 * Add to AndroidManifest.xml:
 * <service android:name=".system.AppTrackerAccessibilityService"
 *          android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 *          android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.accessibilityservice.AccessibilityService" />
 *     </intent-filter>
 *     <meta-data
 *         android:name="android.accessibilityservice"
 *         android:resource="@xml/accessibility_service_config" />
 * </service>
 */
class AppTrackerAccessibilityService : AccessibilityService() {
    private lateinit var tracker: SystemEventsTracker

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = SystemEventsTracker(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val appName = event.source?.packageName?.toString() ?: packageName

        CoroutineScope(Dispatchers.IO).launch {
            tracker.trackAppOpened(packageName, appName)
        }
    }

    override fun onInterrupt() {
        // Called when accessibility service is interrupted
    }
}

/**
 * Content Observer to track email/message changes
 * Add this to MainActivity:
 * context.contentResolver.registerContentObserver(
 *     ContactsContract.CommonDataKinds.Email.CONTENT_URI,
 *     true,
 *     EmailContentObserver(context)
 * )
 */
class EmailContentObserver(private val context: Context) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val tracker = SystemEventsTracker(context)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        CoroutineScope(Dispatchers.IO).launch {
            // Query for new emails (implementation depends on email provider)
            // This is a basic example - real implementation would need to access email provider
            val eventData = mapOf(
                "source" to "email_provider",
                "timestamp" to System.currentTimeMillis().toString()
            )
            tracker.trackEmailReceived("email_provider", "New email received")
        }
    }
}
