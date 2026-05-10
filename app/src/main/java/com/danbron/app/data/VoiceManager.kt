package com.danbron.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isEnabled = true // Configurable by user from UI

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "US")) // Latin American Spanish
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try fallback to Spain Spanish
                tts?.setLanguage(Locale("es", "ES"))
            }

            configureVoice()
            isInitialized = true
            Log.d("VoiceManager", "TTS Initialized successfully")
        } else {
            Log.e("VoiceManager", "TTS Initialization failed")
        }
    }

    private fun configureVoice() {
        tts?.let { engine ->
            // Pitch slightly up to sound younger, speed slightly faster for natural flow
            engine.setPitch(1.15f)
            engine.setSpeechRate(1.05f)

            // Try to find a high quality male network voice
            val voices = engine.voices
            if (voices != null) {
                var selectedVoice: Voice? = null
                
                // 1. Try to find a male network voice
                selectedVoice = voices.find { 
                    it.locale.language == "es" && 
                    it.name.contains("male", ignoreCase = true) && 
                    it.isNetworkConnectionRequired 
                }

                // 2. Fallback to any network voice in Spanish
                if (selectedVoice == null) {
                    selectedVoice = voices.find { 
                        it.locale.language == "es" && 
                        it.isNetworkConnectionRequired 
                    }
                }

                // 3. Fallback to any male voice in Spanish
                if (selectedVoice == null) {
                    selectedVoice = voices.find { 
                        it.locale.language == "es" && 
                        it.name.contains("male", ignoreCase = true) 
                    }
                }

                if (selectedVoice != null) {
                    engine.voice = selectedVoice
                    Log.d("VoiceManager", "Selected voice: ${selectedVoice.name}")
                }
            }
        }
    }

    fun speak(text: String) {
        if (!isEnabled || !isInitialized) return
        
        // Remove emojis and common markdown symbols that might be read aloud weirdly
        val cleanText = text.replace(Regex("[*#~_]"), "").replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
        
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "bron_message")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
