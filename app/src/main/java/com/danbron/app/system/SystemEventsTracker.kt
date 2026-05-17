package com.danbron.app.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.danbron.app.sync.DanbronSyncManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    suspend fun trackScreenSnapshot(snapshot: AndroidScreenSnapshot) {
        val visibleText = snapshot.nodes
            .mapNotNull { it.text.ifBlank { it.description }.takeIf(String::isNotBlank) }
            .distinct()
            .take(80)
            .joinToString("\n")

        val nodesJson = JsonArray().apply {
            snapshot.nodes.take(120).forEach { node ->
                add(JsonObject().apply {
                    addProperty("text", node.text)
                    addProperty("description", node.description)
                    addProperty("className", node.className)
                    addProperty("viewId", node.viewId)
                    addProperty("clickable", node.clickable)
                    addProperty("editable", node.editable)
                    addProperty("bounds", node.bounds)
                })
            }
        }

        val eventData = JsonObject().apply {
            addProperty("packageName", snapshot.packageName)
            addProperty("className", snapshot.className)
            addProperty("visibleText", visibleText.take(4000))
            addProperty("nodeCount", snapshot.nodes.size)
            addProperty("timestamp", snapshot.timestamp)
            add("nodes", nodesJson)
        }

        syncManager.recordSystemEvent("screen_snapshot", eventData)
    }
}

data class AndroidScreenNode(
    val text: String,
    val description: String,
    val className: String,
    val viewId: String,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: String
)

data class AndroidScreenSnapshot(
    val packageName: String,
    val className: String,
    val timestamp: Long,
    val nodes: List<AndroidScreenNode>
)

object AndroidScreenContextStore {
    @Volatile
    private var latest: AndroidScreenSnapshot? = null

    fun update(snapshot: AndroidScreenSnapshot) {
        latest = snapshot
    }

    fun latestSnapshot(): AndroidScreenSnapshot? = latest
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
    private lateinit var commandExecutor: CommandExecutor
    private var commandPollingJob: Job? = null
    private var lastSnapshotAt = 0L
    private var lastSnapshotSignature = ""

    companion object {
        @Volatile
        var instance: AppTrackerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tracker = SystemEventsTracker(this)
        commandExecutor = CommandExecutor(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        serviceInfo = info
        startCommandPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                tracker.trackAppOpened(packageName, packageName)
            }
            captureReadOnlySnapshot(packageName, className)
        }
    }

    override fun onInterrupt() {
        // Called when accessibility service is interrupted
    }

    override fun onDestroy() {
        instance = null
        commandPollingJob?.cancel()
        super.onDestroy()
    }

    /**
     * Find a clickable node by text and perform a click action.
     * Returns true if successful.
     */
    fun tapNodeByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndClick(root, targetText.lowercase())
    }

    private fun findAndClick(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if ((text.contains(target) || desc.contains(target))) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            // Try clicking the parent
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 4) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
                depth++
            }
        }
        for (i in 0 until node.childCount) {
            if (findAndClick(node.getChild(i), target)) return true
        }
        return false
    }

    /**
     * Scroll down in the current view
     */
    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }

    /**
     * Force a fresh screen snapshot right now (bypasses throttle).
     * Returns the snapshot text or null.
     */
    fun forceSnapshot(): String? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<AndroidScreenNode>()
        collectReadableNodes(root, nodes, 0)
        val pkg = root.packageName?.toString() ?: "unknown"
        val snap = AndroidScreenSnapshot(
            packageName = pkg,
            className = "",
            timestamp = System.currentTimeMillis(),
            nodes = nodes
        )
        AndroidScreenContextStore.update(snap)
        lastSnapshotAt = snap.timestamp
        lastSnapshotSignature = "forced"
        return nodes
            .mapNotNull { it.text.ifBlank { it.description }.takeIf(String::isNotBlank) }
            .distinct()
            .take(80)
            .joinToString("\n")
    }

    /**
     * Find the first EditText and set its text.
     */
    fun setTextInField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editNode = findEditText(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", ignoreCase = true) && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val result = findEditText(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    /**
     * Tap a node by its content description (e.g. WhatsApp send button = "Enviar" / "Send").
     */
    fun tapByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndClickByDesc(root, desc.lowercase())
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val d = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (d.contains(target)) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 3) {
                if (parent.isClickable) { parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                parent = parent.parent; depth++
            }
        }
        for (i in 0 until node.childCount) {
            if (findAndClickByDesc(node.getChild(i), target)) return true
        }
        return false
    }

    /**
     * Full WhatsApp send flow: open chat → type message → tap send.
     * Must be called from a coroutine.
     */
    suspend fun sendWhatsAppMessage(context: Context, contact: String, message: String): String {
        // Step 1: Open WhatsApp
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: return "WhatsApp no esta instalado."
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(1800)

        // Step 2: Tap search icon
        val searchTapped = tapByDescription("buscar") || tapByDescription("search")
            || tapNodeByText("buscar") || tapNodeByText("search")
        if (!searchTapped) return "No pude encontrar el boton de busqueda en WhatsApp."
        delay(800)

        // Step 3: Type contact name in search
        val typed = setTextInField(contact)
        if (!typed) return "No pude escribir el nombre del contacto en la busqueda."
        delay(1200)

        // Step 4: Tap the first search result (the contact name)
        val contactTapped = tapNodeByText(contact.lowercase())
        if (!contactTapped) return "No encontre un chat con '$contact'. Verifica que el nombre sea correcto."
        delay(1200)

        // Step 5: Type the message in the chat input
        val msgTyped = setTextInField(message)
        if (!msgTyped) return "Entre al chat pero no pude escribir el mensaje."
        delay(500)

        // Step 6: Tap send button
        val sent = tapByDescription("enviar") || tapByDescription("send")
        if (!sent) return "Escribi el mensaje pero no pude encontrar el boton de enviar."

        return "Mensaje enviado a $contact por WhatsApp."
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val result = findScrollable(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    private fun startCommandPolling() {
        if (commandPollingJob?.isActive == true) return
        commandPollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                commandExecutor.processRemoteCommands()
                delay(7000)
            }
        }
    }

    private suspend fun captureReadOnlySnapshot(packageName: String, className: String) {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotAt < 1500) return

        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<AndroidScreenNode>()
        collectReadableNodes(root, nodes, 0)

        val signature = "$packageName:${nodes.take(25).joinToString("|") { node -> node.text.ifBlank { node.description } }}"
        if (signature == lastSnapshotSignature) return

        lastSnapshotAt = now
        lastSnapshotSignature = signature

        val snapshot = AndroidScreenSnapshot(
            packageName = packageName,
            className = className,
            timestamp = now,
            nodes = nodes
        )

        AndroidScreenContextStore.update(snapshot)
        tracker.trackScreenSnapshot(snapshot)
    }

    private fun collectReadableNodes(
        node: AccessibilityNodeInfo?,
        output: MutableList<AndroidScreenNode>,
        depth: Int
    ) {
        if (node == null || output.size >= 160 || depth > 12) return

        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val editable = className.contains("EditText", ignoreCase = true)

        if (!node.isPassword && (text.isNotBlank() || description.isNotBlank() || viewId.isNotBlank())) {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            output += AndroidScreenNode(
                text = sanitizeVisibleText(text),
                description = sanitizeVisibleText(description),
                className = className,
                viewId = viewId,
                clickable = node.isClickable,
                editable = editable,
                bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            )
        }

        for (i in 0 until node.childCount) {
            collectReadableNodes(node.getChild(i), output, depth + 1)
        }
    }

    private fun sanitizeVisibleText(value: String): String {
        return value
            .replace(Regex("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b"), "[email]")
            .replace(Regex("\\b\\d{12,19}\\b"), "[numero-largo]")
            .take(300)
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
