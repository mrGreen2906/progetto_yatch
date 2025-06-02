package com.example.progetto_yatch.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.progetto_yatch.screens.WelcomeScreen
import com.example.progetto_yatch.screens.LoadingScreen
import com.example.progetto_yatch.screens.HomeScreen
import com.example.progetto_yatch.screens.SecuritySystemScreen
import com.example.progetto_yatch.screens.CameraStreamScreen

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Loading : Screen("loading")
    object Home : Screen("home")
    object Security : Screen("security")
    object CameraStream : Screen("camera_stream")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToLoading = {
                    navController.navigate(Screen.Loading.route)
                }
            )
        }

        composable(Screen.Loading.route) {
            LoadingScreen(
                onLoadingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSecurity = {
                    navController.navigate(Screen.Security.route)
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.CameraStream.route)
                }
            )
        }

        composable(Screen.Security.route) {
            SecuritySystemScreen(
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CameraStream.route) {
            CameraStreamScreen(
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }
    }
}