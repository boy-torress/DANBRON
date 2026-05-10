package com.danbron.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val SyneFamily = FontFamily.Default
val DMSansFamily = FontFamily.Default

object DanbronType {
    val displayLarge = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W800,
        fontSize = 64.sp, letterSpacing = (-3).sp
    )
    val headlineLarge = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 32.sp, letterSpacing = (-1).sp, lineHeight = 37.sp
    )
    val headlineMedium = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 26.sp, letterSpacing = (-0.8).sp, lineHeight = 31.sp
    )
    val headlineSmall = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 20.sp
    )
    val titleMedium = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 17.sp, letterSpacing = (-0.3).sp
    )
    val titleSmall = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 15.sp
    )
    val bodyLarge = TextStyle(
        fontFamily = DMSansFamily, fontWeight = FontWeight.W400,
        fontSize = 15.sp, lineHeight = 24.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = DMSansFamily, fontWeight = FontWeight.W400,
        fontSize = 14.sp, lineHeight = 20.sp
    )
    val bodySmall = TextStyle(
        fontFamily = DMSansFamily, fontWeight = FontWeight.W400,
        fontSize = 13.sp, lineHeight = 19.sp
    )
    val labelLarge = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 15.sp, letterSpacing = 0.3.sp
    )
    val labelSmall = TextStyle(
        fontFamily = DMSansFamily, fontWeight = FontWeight.W500,
        fontSize = 11.sp, letterSpacing = 0.8.sp
    )
    val caption = TextStyle(
        fontFamily = DMSansFamily, fontWeight = FontWeight.W500,
        fontSize = 10.sp, letterSpacing = 0.3.sp
    )
    val statValue = TextStyle(
        fontFamily = SyneFamily, fontWeight = FontWeight.W700,
        fontSize = 22.sp, letterSpacing = (-1).sp
    )
}
