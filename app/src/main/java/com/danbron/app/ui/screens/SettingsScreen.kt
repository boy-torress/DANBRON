package com.danbron.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(vm: MainViewModel, navController: NavController? = null) {
    val user by vm.user.collectAsState()
    val usage by vm.usageMinutes.collectAsState()
    val referralCode by vm.referralCode.collectAsState()
    val referralCount by vm.referralCount.collectAsState()
    val isPro by vm.isPro.collectAsState()
    val syncCode by vm.syncCode.collectAsState()
    val u = user ?: return
    var showSyncModal by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)
        ) {
            // Header with avatar
            Column(Modifier.padding(start = 24.dp, top = 52.dp, bottom = 24.dp)) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Gold, Color(0xFFC97B2E)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(u.name.take(1).uppercase(), style = DanbronType.headlineSmall.copy(fontSize = 22.sp), color = Color(0xFF0A0A0F))
                }
                Spacer(Modifier.height(14.dp))
                Text(u.name, style = DanbronType.headlineMedium, color = TextPrimary)
                Text(
                    "Miembro desde ${SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(u.joinDate))}",
                    style = DanbronType.bodySmall, color = TextTertiary
                )
            }

            // Plan card
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0x26E8B84B), Color(0x0DE8B84B))))
                    .border(1.dp, Color(0x4DE8B84B), RoundedCornerShape(20.dp))
                    .padding(22.dp)
            ) {
                Text(
                    if (isPro) "Plan Danbron Pro ⚡" else "Plan Danbron Libre",
                    style = DanbronType.headlineSmall.copy(fontSize = 18.sp), color = Gold
                )
                Spacer(Modifier.height(6.dp))
                if (isPro) {
                    Text("Tienes acceso completo a todas las funciones de Bron.",
                        style = DanbronType.bodySmall, color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Desbloquea con el Plan Pro:", style = DanbronType.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        ProFeature("📊", "Análisis semanal personalizado por Bron")
                        ProFeature("🔔", "Notificaciones ilimitadas e inteligentes")
                        ProFeature("📤", "Exportar datos y reportes")
                        ProFeature("📝", "Notas ilimitadas con IA")
                        ProFeature("🤝", "Invita 3 amigos y gana 30 días Pro gratis")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.toast("¡Plan Pro próximamente! 🚀") },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                ) { Text(if (isPro) "Plan Activo ✓" else "Ver Plan Pro", style = DanbronType.labelLarge) }
                Spacer(Modifier.height(12.dp))
                Text("Uso total: $usage minutos · Pago por uso activo",
                    style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Spacer(Modifier.height(24.dp))

            // Settings sections
            SettingsSection("Configuración") {
                SettingRow("🔔", "Notificaciones", "Recordatorios proactivos de Bron") { vm.toast("Activadas automáticamente ✅") }
                SettingRow("✏️", "Editar perfil", "Ingresos, gastos, metas") { navController?.navigate("editProfile") ?: vm.editProfile() }
            }

            // Sync section with full pairing status
            SettingsSection("Dispositivos") {
                val paired by vm.isPaired.collectAsState()
                val pairingStatus by vm.pairingStatus.collectAsState()
                val pairedName by vm.pairedDeviceName.collectAsState()
                val lastSync by vm.lastSyncTime.collectAsState()

                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSecondary)
                        .border(1.dp, if (paired) Teal.copy(alpha = 0.4f) else Border, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    // Status header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (paired) Teal.copy(alpha = 0.15f) else Gold.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) { Text(if (paired) "✅" else "💻", fontSize = 20.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (paired) "Conectado con $pairedName" else "Sin dispositivo vinculado",
                                style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W600),
                                color = if (paired) Teal else TextPrimary
                            )
                            Text(
                                when {
                                    paired && lastSync.isNotBlank() -> "Sincronizado a las $lastSync"
                                    pairingStatus == "checking" -> "Verificando..."
                                    pairingStatus == "error" -> "Error de conexion"
                                    else -> "Vincula tu PC para sincronizar todo"
                                },
                                style = DanbronType.labelSmall.copy(letterSpacing = 0.sp),
                                color = TextTertiary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (paired) {
                        // Paired state: show sync info + unpair
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Teal.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔄", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Notas, habitos y perfil se sincronizan automaticamente",
                                style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = Teal)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { vm.checkPairingStatus(); vm.toast("Verificando conexion...") },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BgTertiary, contentColor = TextPrimary)
                            ) { Text("Verificar", style = DanbronType.labelSmall) }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { vm.unpairDevice { ok -> if (ok) vm.toast("Desvinculado") else vm.toast("Error al desvincular") } },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Red.copy(alpha = 0.15f), contentColor = Red)
                            ) { Text("Desvincular", style = DanbronType.labelSmall) }
                        }
                    } else {
                        // Not paired: show sync modal trigger + code input
                        Button(
                            onClick = { showSyncModal = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                        ) { Text("Vincular con mi PC", style = DanbronType.labelLarge) }
                    }
                }
            }

            // Accessibility section — Bron superpowers
            SettingsSection("Superpoderes de Bron") {
                val accessibilityEnabled = remember {
                    val expected = android.content.ComponentName(
                        context, com.danbron.app.system.AppTrackerAccessibilityService::class.java
                    ).flattenToString()
                    val enabledServices = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
                }
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSecondary)
                        .border(1.dp, if (accessibilityEnabled) Teal.copy(alpha = 0.3f) else Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (accessibilityEnabled) "👁️" else "⚡", fontSize = 18.sp, modifier = Modifier.width(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (accessibilityEnabled) "Bron puede ver tu pantalla" else "Activar Vision de Bron",
                                style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500),
                                color = if (accessibilityEnabled) TextPrimary else Gold
                            )
                            Text(
                                if (accessibilityEnabled) "Bron lee texto de cualquier app para ayudarte"
                                else "Permite que Bron lea tu pantalla para darte info de Uber, WhatsApp, etc.",
                                style = DanbronType.labelSmall.copy(letterSpacing = 0.sp),
                                color = if (accessibilityEnabled) Teal else TextSecondary
                            )
                        }
                        Text(if (accessibilityEnabled) "✅" else "›", color = if (accessibilityEnabled) Teal else TextTertiary, fontSize = 16.sp)
                    }
                }
            }

            SettingsSection("Compartir") {
                // Referral card
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSecondary)
                        .border(1.dp, Teal.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            val intent = vm.getShareIntent()
                            context.startActivity(android.content.Intent.createChooser(intent, "Invitar a Danbron"))
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📤", fontSize = 18.sp, modifier = Modifier.width(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Invitar a un amigo", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500), color = TextPrimary)
                            Text("Cada referido = 30 días Pro gratis", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = Teal)
                        }
                        Text("›", color = TextTertiary, fontSize = 16.sp)
                    }
                    if (referralCode.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tu código: $referralCode", style = DanbronType.caption, color = Gold)
                            Text("Referidos: $referralCount", style = DanbronType.caption, color = TextTertiary)
                        }
                    }
                }
            }

            SettingsSection("") {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp)).background(BgSecondary)
                        .border(1.dp, RedDim, RoundedCornerShape(12.dp))
                        .clickable { vm.resetApp() }.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗑️", fontSize = 18.sp, modifier = Modifier.width(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Reiniciar app", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500), color = Red)
                            Text("Borra todos los datos", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                        }
                    }
                }
            }
        }

        // Sync Code Modal with both options
        if (showSyncModal) {
            var pcCodeInput by remember { mutableStateOf("") }
            var pairResultMsg by remember { mutableStateOf("") }
            var isPairing by remember { mutableStateOf(false) }
            val codeFocusRequester = remember { FocusRequester() }

            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSyncModal = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp))
                        .background(BgSecondary)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* block dismiss */ }
                        .imePadding()
                        .padding(24.dp).padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Vincular con tu PC", style = DanbronType.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Usa el mismo email en tu telefono y en tu PC",
                        style = DanbronType.bodySmall, color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                    Spacer(Modifier.height(20.dp))

                    // Option 1: Email pairing (primary)
                    var emailInput by remember { mutableStateOf("") }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(BgTertiary).border(1.dp, Border, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Vincular con email", style = DanbronType.labelSmall.copy(fontWeight = FontWeight.W600), color = Gold)
                        Spacer(Modifier.height(4.dp))
                        Text("Escribe el mismo email que usaste en tu PC", style = DanbronType.caption, color = TextTertiary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = { Text("tu@gmail.com", color = TextTertiary, fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold, unfocusedBorderColor = Border,
                                cursorColor = Gold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            textStyle = DanbronType.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isPairing = true
                                pairResultMsg = ""
                                vm.linkWithEmail(emailInput.trim()) { ok, msg ->
                                    isPairing = false
                                    pairResultMsg = msg
                                    if (ok) showSyncModal = false
                                }
                            },
                            enabled = emailInput.contains("@") && !isPairing,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                        ) {
                            Text(
                                if (isPairing) "Conectando..." else "Vincular",
                                style = DanbronType.labelLarge
                            )
                        }
                        if (pairResultMsg.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(pairResultMsg, style = DanbronType.caption,
                                color = if (pairResultMsg.contains("Vinculado")) Teal else Red)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("— O —", style = DanbronType.labelSmall, color = TextTertiary)
                    Spacer(Modifier.height(14.dp))

                    // Option 2: Code pairing (fallback)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(BgTertiary).border(1.dp, Border, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tengo un codigo del PC", style = DanbronType.labelSmall.copy(fontWeight = FontWeight.W600), color = Gold)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pcCodeInput,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pcCodeInput = it },
                            placeholder = { Text("Codigo de 6 digitos", color = TextTertiary, fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth().focusRequester(codeFocusRequester),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold, unfocusedBorderColor = Border,
                                cursorColor = Gold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = DanbronType.bodyMedium.copy(letterSpacing = 4.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (pcCodeInput.length == 6) {
                                    isPairing = true
                                    pairResultMsg = ""
                                    vm.pairWithCode(pcCodeInput) { ok, msg ->
                                        isPairing = false
                                        pairResultMsg = msg
                                        if (ok) showSyncModal = false
                                    }
                                }
                            },
                            enabled = pcCodeInput.length == 6 && !isPairing,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                        ) {
                            Text(
                                if (isPairing) "Conectando..." else "Vincular con codigo",
                                style = DanbronType.labelLarge
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    var isVerifying by remember { mutableStateOf(false) }
                    var verifyMsg by remember { mutableStateOf("") }
                    Button(
                        onClick = {
                            isVerifying = true
                            verifyMsg = ""
                            vm.confirmPairingFromDesktop { ok, msg ->
                                isVerifying = false
                                verifyMsg = msg
                                if (ok) showSyncModal = false
                            }
                        },
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White)
                    ) { Text(if (isVerifying) "Verificando..." else "Ya vincule mi PC ✓", style = DanbronType.labelLarge) }
                    if (verifyMsg.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(verifyMsg, style = DanbronType.caption,
                            color = if (verifyMsg.contains("Vinculado")) Teal else Gold)
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showSyncModal = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BgTertiary, contentColor = TextSecondary)
                    ) { Text("Cancelar", style = DanbronType.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun ProFeature(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = DanbronType.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        if (title.isNotBlank()) {
            Text(title.uppercase(), style = DanbronType.labelSmall, color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        content()
    }
}

@Composable
private fun SettingRow(icon: String, name: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(BgSecondary)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500), color = TextPrimary)
            Text(desc, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
        }
        Text("›", color = TextTertiary, fontSize = 16.sp)
    }
}
