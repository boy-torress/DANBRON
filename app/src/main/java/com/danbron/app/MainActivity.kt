package com.danbron.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.danbron.app.ui.components.BottomNavBar
import com.danbron.app.ui.screens.*
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.ChatViewModel
import com.danbron.app.viewmodel.MainViewModel
import com.danbron.app.viewmodel.OnboardingChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* granted or not, features might gracefully degrade */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            DanbronTheme {
                DanbronAppScreen()
            }
        }
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
                        saveState = true
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
        val showNav = currentRoute in listOf("home", "chat", "notes", "progress", "settings")
        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            BottomNavBar(currentRoute) { route ->
                navController.navigate(route) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
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
