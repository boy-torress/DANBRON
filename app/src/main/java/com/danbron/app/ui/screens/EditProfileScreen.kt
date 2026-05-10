package com.danbron.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.danbron.app.data.models.User
import com.danbron.app.ui.components.StaggeredEntrance
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel

@Composable
fun EditProfileScreen(vm: MainViewModel, navController: NavController) {
    val user by vm.user.collectAsState()
    val u = user ?: return

    var name by remember { mutableStateOf(u.name) }
    var income by remember { mutableStateOf(u.income.toString()) }
    var expenses by remember { mutableStateOf(u.expenses.toString()) }
    var debt by remember { mutableStateOf(u.debt.toString()) }
    var employment by remember { mutableStateOf(u.employment) }
    var healthFocus by remember { mutableStateOf(u.healthFocus) }
    var isSaving by remember { mutableStateOf(false) }

    val employmentOptions = listOf("unemployed", "part_time", "full_time", "entrepreneur", "retired")
    val healthOptions = listOf("none", "weight_loss", "fitness", "nutrition", "mental_health", "disease_management")

    Column(
        Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        // Header
        StaggeredEntrance(0) {
            Row(
                Modifier.padding(start = 24.dp, top = 52.dp, end = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Editar Perfil", style = DanbronType.headlineMedium, color = TextPrimary)
                Text("✕", fontSize = 24.sp, color = TextSecondary, modifier = Modifier.clickable { navController.popBackStack() })
            }
        }

        Spacer(Modifier.height(24.dp))

        // Name field
        StaggeredEntrance(1) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Nombre", style = DanbronType.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("Tu nombre", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Border,
                        cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ), singleLine = true
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Financial fields
        StaggeredEntrance(2) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Situación Financiera", style = DanbronType.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(12.dp))

                // Income
                OutlinedTextField(
                    value = income, onValueChange = { income = it },
                    placeholder = { Text("Ingresos mensuales", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Border,
                        cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ), singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                // Expenses
                OutlinedTextField(
                    value = expenses, onValueChange = { expenses = it },
                    placeholder = { Text("Gastos mensuales", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Border,
                        cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ), singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                // Debt
                OutlinedTextField(
                    value = debt, onValueChange = { debt = it },
                    placeholder = { Text("Deuda total", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = Border,
                        cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ), singleLine = true
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Employment
        StaggeredEntrance(3) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Situación Laboral", style = DanbronType.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    employmentOptions.forEach { opt ->
                        val isSelected = employment == opt
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Gold else BgTertiary)
                                .border(1.dp, if (isSelected) Gold else Border, RoundedCornerShape(12.dp))
                                .clickable { employment = opt }
                                .padding(12.dp, 8.dp)
                        ) {
                            val label = when (opt) {
                                "unemployed" -> "Desempleado"
                                "part_time" -> "Media Jornada"
                                "full_time" -> "Tiempo Completo"
                                "entrepreneur" -> "Emprendedor"
                                "retired" -> "Jubilado"
                                else -> opt
                            }
                            Text(label, style = DanbronType.labelSmall, color = if (isSelected) Color(0xFF0A0A0F) else TextTertiary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Health Focus
        StaggeredEntrance(4) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Enfoque de Salud", style = DanbronType.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    healthOptions.forEach { opt ->
                        val isSelected = healthFocus == opt
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Gold.copy(alpha = 0.2f) else BgTertiary)
                                .border(1.dp, if (isSelected) Gold else Border, RoundedCornerShape(12.dp))
                                .clickable { healthFocus = opt }
                                .padding(16.dp, 12.dp)
                        ) {
                            val label = when (opt) {
                                "none" -> "Sin enfoque"
                                "weight_loss" -> "Pérdida de peso"
                                "fitness" -> "Fitness"
                                "nutrition" -> "Nutrición"
                                "mental_health" -> "Salud Mental"
                                "disease_management" -> "Manejo de Enfermedad"
                                else -> opt
                            }
                            Text(label, style = DanbronType.labelSmall, color = TextPrimary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        // Action buttons
        StaggeredEntrance(5) {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgTertiary, contentColor = TextSecondary)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        isSaving = true
                        try {
                            val updatedUser = u.copy(
                                name = name.ifBlank { u.name },
                                income = income.toDoubleOrNull() ?: u.income,
                                expenses = expenses.toDoubleOrNull() ?: u.expenses,
                                debt = debt.toDoubleOrNull() ?: u.debt,
                                employment = employment,
                                healthFocus = healthFocus
                            )
                            vm.updateUserProfile(updatedUser)
                            vm.toast("Perfil actualizado ✓")
                            navController.popBackStack()
                        } catch (e: Exception) {
                            vm.toast("Error: ${e.message}")
                        } finally {
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                ) { 
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF0A0A0F))
                    } else {
                        Text("Guardar", style = DanbronType.labelLarge)
                    }
                }
            }
        }
    }
}
