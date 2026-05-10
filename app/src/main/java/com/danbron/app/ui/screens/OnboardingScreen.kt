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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.BronMood
import com.danbron.app.ui.components.BronMascot
import com.danbron.app.ui.components.breathingAlpha
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import com.danbron.app.viewmodel.OnboardingChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationalOnboardingScreen(
    obVm: OnboardingChatViewModel,
    mainVm: MainViewModel,
    onComplete: () -> Unit
) {
    val messages by obVm.messages.collectAsState()
    val isTyping by obVm.isTyping.collectAsState()
    val isReady by obVm.isReady.collectAsState()
    val progressText by obVm.progressText.collectAsState()
    val suggestedChips by obVm.suggestedChips.collectAsState()
    val isRecording by obVm.isRecording.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var voiceEnabled by remember { mutableStateOf(obVm.voiceManager.isEnabled) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val breathAlpha = breathingAlpha()

    LaunchedEffect(Unit) { obVm.startConversation() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // ── Header ──
        Column(
            Modifier.fillMaxWidth().background(BgPrimary)
                .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bron mascot
                BronMascot(mood = BronMood.HAPPY, size = 44.dp, animated = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = Gold)) { append("danbron ") }
                        withStyle(SpanStyle(color = TextTertiary)) { append("by bron") }
                    }, style = DanbronType.titleSmall)
                    Text("● En línea", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = Green)
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(BgSecondary)
                        .clickable { 
                            voiceEnabled = !voiceEnabled
                            obVm.toggleVoice(voiceEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (voiceEnabled) "🔊" else "🔇", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress indicator
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSecondary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated pulsing dot
                val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "pulse_alpha"
                )
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (isReady) Teal else Gold.copy(alpha = pulseAlpha))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    progressText,
                    style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                    color = if (isReady) Teal else TextSecondary
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

        // ── Messages ──
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    if (!isUser) {
                        // Bron avatar + name
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            BronMascot(mood = BronMood.HAPPY, size = 22.dp, animated = false)
                            Spacer(Modifier.width(6.dp))
                            Text("Bron", style = DanbronType.labelSmall.copy(fontWeight = FontWeight.W600), color = Gold)
                        }
                    }

                    Box(
                        Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(18.dp, 18.dp, if (isUser) 4.dp else 18.dp, if (isUser) 18.dp else 4.dp))
                            .background(if (isUser) Gold else BgTertiary)
                            .then(if (!isUser) Modifier.border(1.dp, Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) else Modifier)
                            .padding(13.dp, 13.dp)
                    ) {
                        Text(
                            msg.content,
                            style = DanbronType.bodyMedium.copy(fontWeight = if (isUser) FontWeight.W500 else FontWeight.W400),
                            color = if (isUser) Color(0xFF0A0A0F) else TextPrimary
                        )
                    }
                    Text(timeFmt, style = DanbronType.caption, color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                }
            }

            if (isTyping) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BronMascot(mood = BronMood.THINKING, size = 22.dp, animated = true)
                        Spacer(Modifier.width(6.dp))
                        OnboardingTypingIndicator()
                    }
                }
            }
        }

        // ── Suggested Chips ──
        if (suggestedChips.isNotEmpty() && !isTyping) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(suggestedChips, key = { it }) { chip ->
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(GoldGlow)
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .clickable {
                                inputText = chip
                                obVm.sendMessage(chip)
                                inputText = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(chip, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500), color = Gold)
                    }
                }
            }
        }

        // ── "Start" button when ready ──
        if (isReady && !isTyping) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        val user = obVm.buildUser()
                        mainVm.completeConversationalOnboarding(user)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                ) {
                    BronMascot(mood = BronMood.CELEBRATING, size = 28.dp, animated = true)
                    Spacer(Modifier.width(10.dp))
                    Text("Empezar con Bron →", style = DanbronType.labelLarge.copy(fontSize = 16.sp))
                }
            }
        }

        // ── Input ──
        Box(
            Modifier.fillMaxWidth().background(BgPrimary.copy(alpha = 0.95f))
                .border(width = 1.dp, color = Border)
        ) {
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
                    maxLines = 3
                )
                Spacer(Modifier.width(10.dp))
                if (inputText.isBlank()) {
                    // Microphone button
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (isRecording) Color.Red else Gold)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        obVm.startRecording()
                                        tryAwaitRelease()
                                        obVm.stopRecordingAndSend()
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
                                    obVm.sendMessage(inputText); inputText = ""
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
private fun OnboardingTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "ob_typing")
    Row(
        Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .background(BgTertiary).border(1.dp, Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .padding(13.dp, 10.dp),
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
            Box(Modifier.size(6.dp).offset(y = offset.dp).clip(CircleShape).background(Gold.copy(alpha = 0.6f)))
        }
    }
}
