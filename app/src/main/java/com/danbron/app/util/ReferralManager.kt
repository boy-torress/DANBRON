package com.danbron.app.util

import android.content.Context
import android.content.Intent
import com.danbron.app.data.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Referral system — users ARE the marketing.
 * Generates unique codes and share intents with viral messaging.
 */
object ReferralManager {

    /** Generate a referral code from the user's name */
    fun generateCode(name: String): String {
        val clean = name.trim().lowercase().replace(" ", "")
        val suffix = (System.currentTimeMillis() % 10000).toString()
        return "BRON-${clean.take(5).uppercase()}$suffix"
    }

    /** Create a share intent with viral copy */
    fun createShareIntent(userName: String, referralCode: String): Intent {
        val message = """🚀 *Estoy usando Danbron para organizar mi vida al 100%*

Bron es un asistente de IA que me dice exactamente qué hacer cada día:
💰 Para salir de deudas
⏰ Para recuperar mi tiempo
🔥 Para crear hábitos que peguen

No es otra app genérica — se adapta a MIS números, MI horario, MI realidad.

📲 Descárgala: https://play.google.com/store/apps/details?id=com.danbron.app

Usa mi código *$referralCode* y ambos ganamos 30 días Pro gratis ✨

- $userName"""

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Prueba Danbron — Tu vida, optimizada")
        }
    }
}
