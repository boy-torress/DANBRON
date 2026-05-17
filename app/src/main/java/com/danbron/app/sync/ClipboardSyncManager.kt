package com.danbron.app.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Clipboard Sync Manager
 * Monitors clipboard changes and syncs with paired Windows PC via backend.
 * Copy on phone → paste on PC, and vice versa.
 */
class ClipboardSyncManager(private val context: Context, private val syncManager: DanbronSyncManager) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastSentContent = ""
    private var lastReceivedContent = ""
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isBlank() || text == lastSentContent || text == lastReceivedContent) return@OnPrimaryClipChangedListener
        if (text.length > 50000) return@OnPrimaryClipChangedListener

        lastSentContent = text
        scope.launch {
            try {
                syncManager.pushClipboard(text)
                Log.d("ClipSync", "Pushed clipboard: ${text.take(30)}...")
            } catch (e: Exception) {
                Log.w("ClipSync", "Push failed: ${e.message}")
            }
        }
    }

    fun start() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        startPolling()
        Log.d("ClipSync", "Clipboard sync started")
    }

    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        syncJob?.cancel()
        syncJob = null
        Log.d("ClipSync", "Clipboard sync stopped")
    }

    private fun startPolling() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                try {
                    val remote = syncManager.pullClipboard()
                    if (remote != null && remote != lastSentContent && remote != lastReceivedContent) {
                        lastReceivedContent = remote
                        // Set clipboard on main thread
                        withContext(Dispatchers.Main) {
                            val clip = ClipData.newPlainText("Danbron Sync", remote)
                            clipboardManager.setPrimaryClip(clip)
                        }
                        Log.d("ClipSync", "Received clipboard from PC: ${remote.take(30)}...")
                    }
                } catch (e: Exception) {
                    Log.w("ClipSync", "Poll failed: ${e.message}")
                }
                delay(4000) // Poll every 4 seconds
            }
        }
    }
}
