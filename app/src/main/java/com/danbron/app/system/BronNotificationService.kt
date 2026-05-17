package com.danbron.app.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification Listener Service — reads all notifications from all apps.
 * User must enable it in Settings > Notifications > Notification access.
 */
class BronNotificationService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: BronNotificationService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // Capture existing active notifications
        try {
            val active = activeNotifications ?: return
            for (sbn in active.takeLast(30)) {
                NotificationStore.add(sbn.toNotificationEntry())
            }
        } catch (_: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationStore.add(sbn.toNotificationEntry())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: we keep the notification in history anyway
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    private fun StatusBarNotification.toNotificationEntry(): NotificationEntry {
        val extras = notification.extras
        return NotificationEntry(
            packageName = packageName,
            title = extras?.getCharSequence("android.title")?.toString().orEmpty(),
            text = extras?.getCharSequence("android.text")?.toString().orEmpty(),
            timestamp = postTime,
            key = key
        )
    }
}

data class NotificationEntry(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val key: String
)

object NotificationStore {
    private val notifications = mutableListOf<NotificationEntry>()
    private const val MAX_SIZE = 50

    @Synchronized
    fun add(entry: NotificationEntry) {
        // Avoid duplicates by key
        notifications.removeAll { it.key == entry.key }
        notifications.add(entry)
        if (notifications.size > MAX_SIZE) {
            notifications.removeAt(0)
        }
    }

    @Synchronized
    fun getRecent(count: Int = 20): List<NotificationEntry> {
        return notifications.takeLast(count).reversed()
    }

    @Synchronized
    fun getByApp(packageName: String, count: Int = 10): List<NotificationEntry> {
        return notifications.filter { it.packageName.contains(packageName, ignoreCase = true) }
            .takeLast(count).reversed()
    }

    @Synchronized
    fun clear() {
        notifications.clear()
    }

    @Synchronized
    fun summary(): String {
        if (notifications.isEmpty()) return "No hay notificaciones recientes."
        return notifications.takeLast(15).reversed().joinToString("\n") { entry ->
            val appName = entry.packageName.substringAfterLast(".")
            val age = ((System.currentTimeMillis() - entry.timestamp) / 1000 / 60)
            val timeStr = if (age < 1) "ahora" else "hace ${age}min"
            "[$appName] ${entry.title}: ${entry.text} ($timeStr)"
        }
    }
}
