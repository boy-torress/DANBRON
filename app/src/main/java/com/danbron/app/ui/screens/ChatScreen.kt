package com.danbron.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(chatVm: ChatViewModel) {
    val messages by chatVm.messages.collectAsState()
    val isTyping by chatVm.isTyping.collectAsState()
    val isRecording by chatVm.isRecording.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var voiceEnabled by remember { mutableStateOf(chatVm.voiceManager.isEnabled) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { chatVm.initWelcome() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(BgPrimary)
                .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 16.dp)
                .border(width = 0.dp, color = Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Gold, Color(0xFFC97B2E)))),
                contentAlignment = Alignment.Center) {
                Text("B", style = DanbronType.titleSmall.copy(fontSize = 14.sp), color = Color(0xFF0A0A0F))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bron", style = DanbronType.titleSmall, color = TextPrimary)
                Text("● En línea", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = Green)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(BgSecondary)
                    .clickable { 
                        voiceEnabled = !voiceEnabled
                        chatVm.toggleVoice(voiceEnabled)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (voiceEnabled) "🔊" else "🔇", fontSize = 16.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            .clip(RoundedCornerShape(18.dp, 18.dp, if (isUser) 4.dp else 18.dp, if (isUser) 18.dp else 4.dp))
                            .background(if (isUser) Gold else BgTertiary)
                            .then(if (!isUser) Modifier.border(1.dp, Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) else Modifier)
                            .padding(13.dp, 13.dp)
                    ) {
                        Text(msg.content,
                            style = DanbronType.bodyMedium.copy(fontWeight = if (isUser) FontWeight.W500 else FontWeight.W400),
                            color = if (isUser) Color(0xFF0A0A0F) else TextPrimary)
                    }
                    Text(timeFmt, style = DanbronType.caption, color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                }
            }
            if (isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Suggestions
        if (messages.size <= 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                val chips = listOf("¿Cómo salgo de mis deudas?", "Dame mi plan de hoy", "¿En qué puedo ahorrar?", "Ayúdame con un hábito")
                items(chips, key = { it }) { chip ->
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(BgSecondary)
                            .border(1.dp, Border, RoundedCornerShape(20.dp))
                            .clickable { inputText = chip; chatVm.sendMessage(chip); inputText = "" }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(chip, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500), color = TextSecondary)
                    }
                }
            }
        }

        // Input
        Box(Modifier.fillMaxWidth().background(BgPrimary.copy(alpha = 0.95f)).border(width = 1.dp, color = Border)) {
            Row(Modifier.padding(12.dp, 12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    placeholder = { Text("Escríbele a Bron...", color = TextTertiary) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Border,
                        cursorColor = Gold, focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 4
                )
                Spacer(Modifier.width(10.dp))
                if (inputText.isBlank()) {
                    // Microphone button
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (isRecording) Color.Red else Gold)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        chatVm.startRecording()
                                        tryAwaitRelease()
                                        chatVm.stopRecordingAndSend()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { 
                        Text("🎙️", fontSize = 18.sp) 
                    }
                } else {
                    // Send button
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Gold)
                            .clickable {
                                if (inputText.isNotBlank() && !isTyping) {
                                    chatVm.sendMessage(inputText); inputText = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("➤", color = Color(0xFF0A0A0F), fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .background(BgTertiary).border(1.dp, Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .padding(13.dp, 13.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { i ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes { durationMillis = 1200; -4f at 300 + i * 200 },
                    repeatMode = RepeatMode.Reverse
                ), label = "dot$i"
            )
            Box(Modifier.size(6.dp).offset(y = offset.dp).clip(CircleShape).background(TextTertiary))
        }
    }
}
