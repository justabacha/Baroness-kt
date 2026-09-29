package com.baroness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baroness.app.components.notification.InAppNotification
import com.baroness.app.phestydrop.data.PhestyDropInfo
import com.baroness.app.phestydrop.manager.PhestyDropManager
import com.baroness.app.phestydrop.ui.PhestyDropSheet
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.screens.DashboardScreen
import com.baroness.app.screens.GateScreen
import com.baroness.app.screens.ProfileSetupScreen
import com.baroness.app.screens.WishlistScreen
import com.baroness.app.screens.PhotosScreen
import com.baroness.app.screens.ChatListScreen
import com.baroness.app.screens.ChatRoomScreen
import com.baroness.app.screens.settings.*
import com.baroness.app.ui.theme.BaronessAppTheme
import com.baroness.app.utils.SessionManager
import com.baroness.app.utils.StorageManager
import com.baroness.app.viewmodels.NotificationViewModel
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.viewmodels.SettingsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestNotificationPermission()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(this)
            )

            BaronessAppTheme(settingsViewModel = settingsViewModel) {
                val notificationViewModel: NotificationViewModel = viewModel()
                
                val currentNotification by notificationViewModel.currentNotification.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(Unit) {
                    com.baroness.app.utils.NotificationCenter.setViewModel(notificationViewModel)
                    WishlistRepository.getInstance(context).setNotificationViewModel(notificationViewModel)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppEntryPoint(navController, settingsViewModel)

                        val inAppBannersEnabled by settingsViewModel.inAppBannersEnabled.collectAsStateWithLifecycle()

                        // In-App Notification Overlay
                        if (inAppBannersEnabled) {
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
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
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
fun AppEntryPoint(
    navController: androidx.navigation.NavHostController,
    settingsViewModel: SettingsViewModel
) {
    var startDestination by remember { mutableStateOf<String?>(null) }
    var pendingPhestyDrop by remember { mutableStateOf<PhestyDropInfo?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = SessionManager(context)
    val storageManager = remember { StorageManager(context) }

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

        // PhestyDrop Startup Auto-Check
        val autoUpdateEnabled = storageManager.getBoolean("phestydrop_auto_update") ?: true
        val drop = PhestyDropManager.checkForDrop()
        if (drop != null) {
            if (drop.isMandatory || autoUpdateEnabled) {
                pendingPhestyDrop = drop
            }
        }
    }

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AppNavigation(
            startDestination = startDestination!!,
            navController = navController,
            settingsViewModel = settingsViewModel
        )

        // Display PhestyDrop Sheet if update detected
        pendingPhestyDrop?.let { dropInfo ->
            PhestyDropSheet(
                dropInfo = dropInfo,
                onDismiss = { pendingPhestyDrop = null }
            )
        }
    }
}

@Composable
fun AppNavigation(
    startDestination: String,
    navController: androidx.navigation.NavHostController,
    settingsViewModel: SettingsViewModel
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("gate") {
            GateScreen(navController)
        }
        composable("dashboard") {
            DashboardScreen(navController, settingsViewModel = settingsViewModel)
        }
        composable(
            "profile_setup/{personaId}",
            arguments = listOf(navArgument("personaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
            ProfileSetupScreen(navController, personaId)
        }
        composable("chat_list") {
            ChatListScreen(navController, settingsViewModel = settingsViewModel)
        }
        composable(
            "chat_room/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatRoomScreen(navController, id, settingsViewModel = settingsViewModel)
        }
        composable("Friday") {
            ChatRoomScreen(navController, "friday", settingsViewModel = settingsViewModel)
        }
        composable("Photos") {
            PhotosScreen(navController)
        }
        composable("Wishlist") {
            WishlistScreen(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings") {
            SettingsCenterScreen(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/profile") {
            ProfileSettingsPage(navController)
        }
        composable("settings/appearance") {
            AppearanceSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/appearance/theme") {
            ThemeSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/appearance/font") {
            FontSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/appearance/wallpaper") {
            WallpaperSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/sound") {
            SoundHapticsSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
        composable("settings/friday") {
            FridaySettingsPage(navController)
        }
        composable("settings/notifications") {
            NotificationSettingsPage(navController, settingsViewModel = settingsViewModel)
        }
    }
}
