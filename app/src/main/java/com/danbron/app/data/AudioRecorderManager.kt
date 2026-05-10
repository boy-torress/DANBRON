package com.danbron.app.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording(): File? {
        val outputDir = context.cacheDir
        audioFile = File.createTempFile("danbron_voice", ".m4a", outputDir)
        
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            
            try {
                prepare()
                start()
                Log.d("AudioRecorder", "Recording started: ${audioFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "prepare/start failed (perhaps missing permission)", e)
                return null
            }
        }
        return audioFile
    }

    fun stopRecording(): File? {
        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d("AudioRecorder", "Recording stopped")
        } catch (e: RuntimeException) {
            // Stop can fail if recording is too short
            Log.e("AudioRecorder", "stop() failed", e)
            audioFile?.delete()
            audioFile = null
        }
        recorder = null
        return audioFile
    }
}
