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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.BronMood
import com.danbron.app.ui.components.BronMascot
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import com.danbron.app.viewmodel.OnboardingChatViewModel
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

    LaunchedEffect(Unit) { obVm.startConversation() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // ── Premium Header ──
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BgSecondary, BgPrimary)))
                .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BronMascot(mood = BronMood.HAPPY, size = 46.dp, animated = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.W700)) { append("danbron ") }
                        withStyle(SpanStyle(color = TextTertiary)) { append("by bron") }
                    }, style = DanbronType.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                        Spacer(Modifier.width(5.dp))
                        Text("En linea", style = DanbronType.caption, color = TextTertiary)
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Border, CircleShape)
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(0.5.dp, Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.15f), Color.Transparent))
        ))

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
                            .shadow(
                                elevation = if (isUser) 6.dp else 2.dp,
                                shape = RoundedCornerShape(20.dp, 20.dp, if (isUser) 6.dp else 20.dp, if (isUser) 20.dp else 6.dp),
                                ambientColor = if (isUser) Gold.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(20.dp, 20.dp, if (isUser) 6.dp else 20.dp, if (isUser) 20.dp else 6.dp))
                            .background(
                                if (isUser) Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E)))
                                else Brush.linearGradient(listOf(Color(0xFF131320), Color(0xFF0E0E18)))
                            )
                            .then(if (!isUser) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)) else Modifier)
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
                    Text(timeFmt, style = DanbronType.caption.copy(letterSpacing = 0.sp), color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = {
                        val user = obVm.buildUser()
                        mainVm.completeConversationalOnboarding(user)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Gold.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF0A0A0F)
                    )
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BronMascot(mood = BronMood.CELEBRATING, size = 28.dp, animated = true)
                            Spacer(Modifier.width(10.dp))
                            Text("Empezar con Bron", style = DanbronType.labelLarge.copy(fontSize = 16.sp), color = Color(0xFF0A0A0F))
                        }
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
                    maxLines = 3,
                    textStyle = DanbronType.bodyMedium
                )
                Spacer(Modifier.width(10.dp))
                if (inputText.isBlank()) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape)
                            .background(
                                if (isRecording) Brush.radialGradient(listOf(Color(0xFFFF4444), Color(0xFFCC0000)))
                                else Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E)))
                            )
                            .shadow(8.dp, CircleShape, ambientColor = if (isRecording) Red.copy(alpha = 0.3f) else Gold.copy(alpha = 0.2f))
                            .clickable { obVm.toggleRecording() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isRecording) "⏹" else "🎙", fontSize = 22.sp)
                    }
                } else {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Gold, Color(0xFFD4A33E))))
                            .shadow(8.dp, CircleShape, ambientColor = Gold.copy(alpha = 0.2f))
                            .clickable {
                                if (inputText.isNotBlank() && !isTyping) {
                                    obVm.sendMessage(inputText); inputText = ""
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
private fun OnboardingTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "ob_typing")
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
            Box(Modifier.size(7.dp).offset(y = offset.dp).clip(CircleShape).background(Gold.copy(alpha = 0.5f)))
        }
    }
}
