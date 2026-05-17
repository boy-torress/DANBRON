package com.danbron.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.BronMood
import com.danbron.app.ui.components.BronMascot
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(chatVm: ChatViewModel) {
    val messages by chatVm.messages.collectAsState()
    val isTyping by chatVm.isTyping.collectAsState()
    val isRecording by chatVm.isRecording.collectAsState()
    val partialSpeech by chatVm.speechRecognizer.partialResult.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var voiceEnabled by remember { mutableStateOf(chatVm.voiceManager.isEnabled) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { chatVm.initWelcome() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // ── Premium Header ──
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BgSecondary, BgPrimary)))
                .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BronMascot(mood = BronMood.HAPPY, size = 46.dp, animated = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bron", style = DanbronType.titleMedium, color = Gold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(Green)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Siempre contigo", style = DanbronType.caption, color = TextTertiary)
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Border, CircleShape)
                        .clickable {
                            voiceEnabled = !voiceEnabled
                            chatVm.toggleVoice(voiceEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (voiceEnabled) "🔊" else "🔇", fontSize = 16.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.15f), Color.Transparent))
        ))

        // ── Messages ──
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Box(
                        Modifier.widthIn(max = 300.dp)
                            .shadow(
                                elevation = if (isUser) 6.dp else 2.dp,
                                shape = RoundedCornerShape(20.dp, 20.dp, if (isUser) 6.dp else 20.dp, if (isUser) 20.dp else 6.dp),
                                ambientColor = if (isUser) Gold.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f),
                                spotColor = if (isUser) Gold.copy(alpha = 0.1f) else Color.Transparent
                            )
                            .clip(RoundedCornerShape(20.dp, 20.dp, if (isUser) 6.dp else 20.dp, if (isUser) 20.dp else 6.dp))
                            .background(
                                if (isUser) Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E)))
                                else Brush.linearGradient(listOf(Color(0xFF131320), Color(0xFF0E0E18)))
                            )
                            .then(
                                if (!isUser) Modifier.border(
                                    0.5.dp, Color.White.copy(alpha = 0.04f),
                                    RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                                ) else Modifier
                            )
                            .padding(14.dp, 12.dp)
                    ) {
                        Text(
                            msg.content,
                            style = DanbronType.bodyMedium.copy(
                                fontWeight = if (isUser) FontWeight.W500 else FontWeight.W400,
                                lineHeight = 22.sp
                            ),
                            color = if (isUser) Color(0xFF0A0A0F) else TextPrimary
                        )
                    }
                    Text(
                        timeFmt,
                        style = DanbronType.caption.copy(letterSpacing = 0.sp),
                        color = if (isUser) TextTertiary.copy(alpha = 0.6f) else TextTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            if (isTyping) {
                item { TypingIndicator() }
            }
        }

        // ── Quick Suggestions ──
        if (messages.size <= 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                val chips = listOf(
                    "💡" to "Agregame una nota rapida",
                    "🚗" to "Cuanto gane hoy en Uber",
                    "📱" to "Que hay en mi pantalla",
                    "🎯" to "Crea un plan para hoy"
                )
                items(chips, key = { it.second }) { (emoji, chip) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Border, RoundedCornerShape(24.dp))
                            .clickable { chatVm.sendMessage(chip) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "$emoji $chip",
                            style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── Premium Input Bar ──
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .border(width = 0.5.dp, color = Border)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    placeholder = { Text("Escríbele a Bron...", color = TextTertiary, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold.copy(alpha = 0.4f),
                        unfocusedBorderColor = Border,
                        cursorColor = Gold,
                        focusedContainerColor = Color.White.copy(alpha = 0.03f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 4,
                    textStyle = DanbronType.bodyMedium
                )
                Spacer(Modifier.width(10.dp))
                if (inputText.isBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isRecording && partialSpeech.isNotBlank()) {
                            Text(
                                partialSpeech,
                                style = DanbronType.caption,
                                color = Gold.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Box(
                            Modifier.size(50.dp).clip(CircleShape)
                                .background(
                                    if (isRecording) Brush.radialGradient(listOf(Color(0xFFFF4444), Color(0xFFCC0000)))
                                    else Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E)))
                                )
                                .shadow(8.dp, CircleShape, ambientColor = if (isRecording) Red.copy(alpha = 0.3f) else Gold.copy(alpha = 0.2f))
                                .clickable {
                                    if (isRecording) chatVm.stopRecordingAndSend()
                                    else chatVm.startRecording()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isRecording) "⏹" else "🎙", fontSize = 22.sp)
                        }
                    }
                } else {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E))))
                            .shadow(8.dp, CircleShape, ambientColor = Gold.copy(alpha = 0.2f))
                            .clickable {
                                if (inputText.isNotBlank() && !isTyping) {
                                    chatVm.sendMessage(inputText); inputText = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("➤", color = Color(0xFF0A0A0F), fontSize = 20.sp, fontWeight = FontWeight.W700) }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF131320), Color(0xFF0E0E18))))
            .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .padding(16.dp, 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { i ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes { durationMillis = 1200; -5f at 300 + i * 200 },
                    repeatMode = RepeatMode.Reverse
                ), label = "dot$i"
            )
            Box(
                Modifier.size(7.dp).offset(y = offset.dp).clip(CircleShape)
                    .background(Gold.copy(alpha = 0.5f))
            )
        }
    }
}
