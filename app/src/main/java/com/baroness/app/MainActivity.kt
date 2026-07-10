package com.baroness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baroness.app.components.notification.InAppNotification
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.screens.DashboardScreen
import com.baroness.app.screens.GateScreen
import com.baroness.app.screens.ProfileSetupScreen
import com.baroness.app.screens.WishlistScreen
import com.baroness.app.screens.PhotosScreen
import com.baroness.app.screens.MessagesScreen
import com.baroness.app.ui.theme.BaronessAppTheme
import com.baroness.app.utils.SessionManager
import com.baroness.app.viewmodels.NotificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestNotificationPermission()

        setContent {
            BaronessAppTheme {
                val notificationViewModel: NotificationViewModel = viewModel()
                val currentNotification by notificationViewModel.currentNotification.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(Unit) {
                    WishlistRepository.getInstance(context).setNotificationViewModel(notificationViewModel)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppEntryPoint(navController)

                        // In-App Notification Overlay
                        currentNotification?.let { data ->
                            InAppNotification(
                                data = data,
                                onDismiss = { notificationViewModel.dismiss() },
                                onClick = { route ->
                                    notificationViewModel.dismiss()
                                    route?.let { navController.navigate(it) }
                                }
                            )
                        }
                    }
                }

                LaunchedEffect(intent) {
                    intent?.getStringExtra("route")?.let { route ->
                        navController.navigate(route)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("MainActivity", "Notification permission granted")
            } else {
                android.util.Log.w("MainActivity", "Notification permission denied")
            }
        }
    }
}

@Composable
fun AppEntryPoint(navController: androidx.navigation.NavHostController) {
    var startDestination by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = SessionManager(context)

    LaunchedEffect(Unit) {
        val destination = withContext(Dispatchers.IO) {
            sessionManager.getStartDestination()
        }
        startDestination = destination

        // Register periodic background sync
        val syncRequest = PeriodicWorkRequestBuilder<com.baroness.app.workers.SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_wishlist_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AppNavigation(startDestination = startDestination!!, navController = navController)
    }
}

@Composable
fun AppNavigation(startDestination: String, navController: androidx.navigation.NavHostController) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("gate") {
            GateScreen(navController)
        }
        composable("dashboard") {
            DashboardScreen(navController)
        }
        composable(
            "profile_setup/{personaId}",
            arguments = listOf(navArgument("personaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
            ProfileSetupScreen(navController, personaId)
        }
        composable("Messages") {
            MessagesScreen(navController)
        }
        composable("Friday") {
            PlaceholderScreen(navController, "Friday (AI Companion)")
        }
        composable("Photos") {
            PhotosScreen(navController)
        }
        composable("Wishlist") {
            WishlistScreen(navController)
        }
    }
}

@Composable
private fun PlaceholderScreen(navController: NavController, title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Go Back")
            }
        }
    }
}
