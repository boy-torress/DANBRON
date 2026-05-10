package com.danbron.app.ui

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Progress : Screen("progress")
    object Notes : Screen("notes")
    object Settings : Screen("settings")
}
