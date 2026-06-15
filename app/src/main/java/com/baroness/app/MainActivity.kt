package com.baroness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baroness.app.screens.DashboardScreen
import com.baroness.app.screens.GateScreen
import com.baroness.app.screens.ProfileSetupScreen
import com.baroness.app.ui.theme.BaronessAppTheme
import com.baroness.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaronessAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppEntryPoint()
                }
            }
        }
    }
}

@Composable
fun AppEntryPoint() {
    var startDestination by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = SessionManager(context)

    LaunchedEffect(Unit) {
        val destination = withContext(Dispatchers.IO) {
            sessionManager.getStartDestination()
        }
        startDestination = destination
    }

    if (startDestination == null) {
        // Loading screen while checking session
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AppNavigation(startDestination = startDestination!!)
    }
}

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable("gate") {
            GateScreen(navController)
        }
        composable("dashboard") {
            DashboardScreen(navController)
        }
        composable("profile_setup/{personaId}", arguments = listOf(navArgument("personaId") { type = NavType.StringType })) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
            ProfileSetupScreen(navController, personaId)
        }
    }
}