package com.baroness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baroness.app.screens.DashboardScreen
import com.baroness.app.screens.GateScreen
import com.baroness.app.ui.theme.BaronessAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaronessAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "gate") {
        composable("gate") {
            GateScreen(navController)
        }
        composable("dashboard") {
            DashboardScreen()
        }
        composable("profile_setup/{personaId}", arguments = listOf(navArgument("personaId") { type = NavType.StringType })) { backStackEntry ->
            // ProfileSetupScreen will be implemented later
            @Suppress("unused")
            val personaId = backStackEntry.arguments?.getString("personaId")
            DashboardScreen()
        }
    }
}