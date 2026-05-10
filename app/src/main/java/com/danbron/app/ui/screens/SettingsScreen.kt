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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

            // Sync section
            SettingsSection("Dispositivos") {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSecondary)
                        .border(1.dp, Teal.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showSyncModal = true }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💻", fontSize = 18.sp, modifier = Modifier.width(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Vincular con PC", style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W500), color = TextPrimary)
                            Text("Usa Bron en tu computador con la misma cuenta", style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = Teal)
                        }
                        Text("›", color = TextTertiary, fontSize = 16.sp)
                    }
                    if (syncCode.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Código activo: $syncCode", style = DanbronType.caption, color = Gold)
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

        // Sync Code Modal
        if (showSyncModal) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { showSyncModal = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp))
                        .background(BgSecondary).clickable(enabled = false) {}
                        .padding(24.dp).padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💻 Vincular con tu PC", style = DanbronType.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Abre Danbron en tu computador e ingresa este código para sincronizar tu cuenta.",
                        style = DanbronType.bodySmall, color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))

                    // Sync code display
                    val code = syncCode.ifBlank { vm.generateSyncCode() }
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(BgTertiary)
                            .border(2.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 32.dp, vertical = 20.dp)
                    ) {
                        Text(
                            code.chunked(3).joinToString(" – "),
                            style = DanbronType.headlineLarge.copy(fontSize = 28.sp, letterSpacing = 2.sp),
                            color = Gold
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("🔒 El código expira en 10 minutos",
                        style = DanbronType.labelSmall.copy(letterSpacing = 0.sp), color = TextTertiary)
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { showSyncModal = false },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                    ) { Text("Listo", style = DanbronType.labelLarge) }
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
