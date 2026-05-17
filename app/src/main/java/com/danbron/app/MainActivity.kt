package com.danbron.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.danbron.app.system.AppTrackerAccessibilityService
import com.danbron.app.ui.components.BottomNavBar
import com.danbron.app.ui.screens.*
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.ChatViewModel
import com.danbron.app.viewmodel.MainViewModel
import com.danbron.app.viewmodel.OnboardingChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var permissionRefresh by mutableStateOf(0)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRefresh++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DanbronTheme {
                permissionRefresh
                if (hasRequiredMandatoryPermissions()) {
                    DanbronAppScreen()
                } else {
                    MandatoryPermissionsScreen(
                        missingRuntimePermissions = missingRuntimePermissions().isNotEmpty(),
                        accessibilityEnabled = isAccessibilityServiceEnabled(),
                        onRequestRuntimePermissions = { requestRuntimePermissions() },
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefresh++
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun missingRuntimePermissions(): List<String> =
        requiredRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

    private fun requestRuntimePermissions() {
        val missing = missingRuntimePermissions()
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            permissionRefresh++
        }
    }

    private fun hasRequiredMandatoryPermissions(): Boolean =
        missingRuntimePermissions().isEmpty() && isAccessibilityServiceEnabled()

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AppTrackerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { service ->
            service.equals(expected, ignoreCase = true)
        }
    }
}

@Composable
private fun MandatoryPermissionsScreen(
    missingRuntimePermissions: Boolean,
    accessibilityEnabled: Boolean,
    onRequestRuntimePermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgSecondary)
                .border(1.dp, Border, RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Permisos obligatorios", style = DanbronType.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Para usar Danbron debes autorizar microfono, notificaciones y Accesibilidad. Sin estos permisos la aplicacion no continuara.",
                style = DanbronType.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            PermissionRow("Microfono y notificaciones", !missingRuntimePermissions)
            Spacer(Modifier.height(8.dp))
            PermissionRow("Accesibilidad para detectar apps abiertas", accessibilityEnabled)

            Spacer(Modifier.height(18.dp))
            if (missingRuntimePermissions) {
                Button(
                    onClick = onRequestRuntimePermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = BgPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Conceder permisos del sistema")
                }
            }

            if (!accessibilityEnabled) {
                Button(
                    onClick = onOpenAccessibility,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = BgPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir Accesibilidad")
                }
            }

            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("No aceptar y cerrar", color = TextTertiary)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgTertiary)
            .border(1.dp, if (granted) Gold.copy(alpha = 0.4f) else Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (granted) "OK" else "Pendiente", style = DanbronType.labelSmall, color = if (granted) Gold else TextTertiary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = DanbronType.bodySmall, color = TextPrimary)
    }
}

@Composable
fun DanbronAppScreen() {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()
    val obVm: OnboardingChatViewModel = viewModel()
    val onboarded by vm.onboarded.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: "splash"

    // Toast state
    var toastMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.toastMsg.collectLatest { msg ->
            toastMsg = msg
            delay(3000)
            toastMsg = null
        }
    }

    Box(Modifier.fillMaxSize().background(BgPrimary).systemBarsPadding().imePadding()) {
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.fillMaxSize().padding(bottom = if (currentRoute in listOf("home", "chat", "notes", "progress", "settings")) 80.dp else 0.dp)
        ) {
            composable("splash") {
                SplashScreen {
                    if (onboarded) {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                        vm.fetchDailyBronMsg()
                    } else {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            }

            composable("onboarding") {
                ConversationalOnboardingScreen(obVm, vm) {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }

            composable("home") {
                HomeScreen(vm) {
                    navController.navigate("chat") {
                        launchSingleTop = true
                    }
                }
            }

            composable("chat") {
                ChatScreen(chatVm)
            }

            composable("notes") {
                NotesScreen(vm)
            }

            composable("progress") {
                ProgressScreen(vm)
            }

            composable("settings") {
                SettingsScreen(vm, navController)
            }

            composable("editProfile") {
                EditProfileScreen(vm, navController)
            }
        }

        // Bottom Nav (only on main screens)
        val mainRoutes = listOf("home", "chat", "notes", "progress", "settings")
        val showNav = currentRoute in mainRoutes
        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            BottomNavBar(currentRoute) { route ->
                if (route != currentRoute) {
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toastMsg != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(BgTertiary)
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(toastMsg ?: "", style = DanbronType.bodySmall, color = TextPrimary)
            }
        }
    }
}
