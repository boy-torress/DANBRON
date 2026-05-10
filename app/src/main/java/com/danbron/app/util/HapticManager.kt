package com.danbron.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback manager — makes every interaction feel premium.
 * Uses contextual vibration patterns for different actions.
 */
object HapticManager {

    private fun vibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Light tap — for buttons, nav items, toggles */
    fun tap(context: Context) {
        vibrate(context, 15L, 80)
    }

    /** Task completed — satisfying double pulse */
    fun taskComplete(context: Context) {
        val v = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 40), intArrayOf(0, 120, 0, 180), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(80)
        }
    }

    /** All tasks done / streak milestone — celebration burst */
    fun celebration(context: Context) {
        val v = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 30, 40, 60), intArrayOf(0, 200, 0, 150, 0, 255), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(200)
        }
    }

    /** Error / invalid action — short buzz */
    fun error(context: Context) {
        vibrate(context, 40L, 255)
    }

    private fun vibrate(context: Context, duration: Long, amplitude: Int) {
        val v = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(duration)
        }
    }
}
