package com.danbron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
    var workStyle by remember { mutableStateOf(u.workStyle) }
    var age by remember { mutableStateOf(if (u.age > 0) u.age.toString() else "") }
    var stressLevel by remember { mutableStateOf(u.stressLevel.toString()) }
    var sleepGoal by remember { mutableStateOf(u.sleepGoal) }
    var wakeGoal by remember { mutableStateOf(u.wakeGoal) }
    var lifeSummary by remember { mutableStateOf(u.lifeSummary) }
    var isSaving by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun closeScreen() {
        if (!navController.popBackStack()) {
            navController.navigate("settings") { launchSingleTop = true }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 100.dp)
    ) {
        StaggeredEntrance(0) {
            Row(
                Modifier
                    .padding(start = 24.dp, top = 52.dp, end = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Editar perfil", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Estos datos alimentan las decisiones de Bron.", style = DanbronType.bodySmall, color = TextTertiary)
                }
                Text("x", fontSize = 24.sp, color = TextSecondary, modifier = Modifier.clickable { closeScreen() })
            }
        }

        Spacer(Modifier.height(24.dp))

        StaggeredEntrance(1) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Nombre", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Tu nombre", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = profileTextFieldColors(),
                    singleLine = true
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(2) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Situacion financiera", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(12.dp))

                NumberProfileField(
                    value = income,
                    onValueChange = { income = it },
                    placeholder = "Ingresos mensuales"
                )
                Spacer(Modifier.height(10.dp))
                NumberProfileField(
                    value = expenses,
                    onValueChange = { expenses = it },
                    placeholder = "Gastos mensuales"
                )
                Spacer(Modifier.height(10.dp))
                NumberProfileField(
                    value = debt,
                    onValueChange = { debt = it },
                    placeholder = "Deuda total"
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(3) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Trabajo y rutina", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = workStyle,
                    onValueChange = { workStyle = it },
                    placeholder = { Text("Ej: driver, full_time, freelance", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = profileTextFieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberProfileField(
                        value = age,
                        onValueChange = { age = it },
                        placeholder = "Edad",
                        modifier = Modifier.weight(1f)
                    )
                    NumberProfileField(
                        value = stressLevel,
                        onValueChange = { stressLevel = it },
                        placeholder = "Estres 1-10",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeProfileField(
                        value = wakeGoal,
                        onValueChange = { wakeGoal = it },
                        placeholder = "Despertar",
                        modifier = Modifier.weight(1f)
                    )
                    TimeProfileField(
                        value = sleepGoal,
                        onValueChange = { sleepGoal = it },
                        placeholder = "Dormir",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(4) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Contexto para Bron", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lifeSummary,
                    onValueChange = { lifeSummary = it.take(700) },
                    placeholder = { Text("Ej: Soy chofer de Uber, quiero controlar ganancias, horas y gastos.", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = profileTextFieldColors()
                )
            }
        }

        validationError?.let { error ->
            Spacer(Modifier.height(14.dp))
            Text(
                error,
                color = Red,
                style = DanbronType.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(Modifier.height(30.dp))

        StaggeredEntrance(5) {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { closeScreen() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgTertiary, contentColor = TextSecondary)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        isSaving = true
                        validationError = null
                        try {
                            val parsedStress = (stressLevel.toIntOrNull() ?: u.stressLevel).coerceIn(1, 10)
                            val parsedAge = (age.toIntOrNull() ?: u.age).coerceIn(0, 120)
                            if (name.isBlank()) {
                                validationError = "El nombre no puede quedar vacio."
                                isSaving = false
                                return@Button
                            }
                            val updatedUser = u.copy(
                                name = name.trim(),
                                income = parseProfileNumber(income) ?: u.income,
                                expenses = parseProfileNumber(expenses) ?: u.expenses,
                                debt = parseProfileNumber(debt) ?: u.debt,
                                workStyle = workStyle.ifBlank { u.workStyle }.trim(),
                                age = parsedAge,
                                stressLevel = parsedStress,
                                sleepGoal = sleepGoal.ifBlank { u.sleepGoal }.trim(),
                                wakeGoal = wakeGoal.ifBlank { u.wakeGoal }.trim(),
                                lifeSummary = lifeSummary.trim()
                            )
                            vm.updateUserProfile(updatedUser)
                            vm.toast("Perfil actualizado")
                            closeScreen()
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF0A0A0F)
                        )
                    } else {
                        Text("Guardar", style = DanbronType.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' || it == ',' }) },
        placeholder = { Text(placeholder, color = TextTertiary) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = profileTextFieldColors(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun TimeProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == ':' }.take(5)) },
        placeholder = { Text(placeholder, color = TextTertiary) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = profileTextFieldColors(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun parseProfileNumber(value: String): Double? {
    return value.trim()
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()
}

@Composable
private fun profileTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = Border,
    cursorColor = Gold,
    focusedContainerColor = BgTertiary,
    unfocusedContainerColor = BgTertiary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
