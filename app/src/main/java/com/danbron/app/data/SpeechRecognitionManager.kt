package com.danbron.app.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Native Android SpeechRecognizer wrapper that works without API keys.
 * Uses Google's on-device or cloud speech recognition.
 */
class SpeechRecognitionManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onResultCallback: (String) -> Unit,
        onErrorCallback: (String) -> Unit
    ) {
        if (!isAvailable) {
            onErrorCallback("Reconocimiento de voz no disponible en este dispositivo.")
            return
        }

        onResult = onResultCallback
        onError = onErrorCallback

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(createListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-419") // Latin American Spanish
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-419")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }

            recognizer?.startListening(intent)
            _isListening.value = true
            _partialResult.value = ""
            Log.d("SpeechRecognition", "Started listening")
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Failed to start", e)
            _isListening.value = false
            onErrorCallback("Error iniciando reconocimiento: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun destroy() {
        try {
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {}
        _isListening.value = false
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognition", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognition", "User started speaking")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognition", "User stopped speaking")
                _isListening.value = false
            }

            override fun onError(error: Int) {
                _isListening.value = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No entendí. Intenta de nuevo."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada. Habla más fuerte."
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio. Verifica el micrófono."
                    SpeechRecognizer.ERROR_NETWORK -> "Sin conexión para reconocimiento en la nube. Intenta de nuevo."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red. Intenta de nuevo."
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de voz."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado. Espera un momento."
                    else -> "Error de voz ($error). Intenta de nuevo."
                }
                Log.e("SpeechRecognition", "Error: $error - $msg")
                onError?.invoke(msg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                _partialResult.value = ""
                if (text.isNotBlank()) {
                    Log.d("SpeechRecognition", "Result: $text")
                    onResult?.invoke(text)
                } else {
                    onError?.invoke("No entendí lo que dijiste. Intenta de nuevo.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    _partialResult.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
