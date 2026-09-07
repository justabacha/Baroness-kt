package com.baroness.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.baroness.app.components.ChatEntry
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.components.GlobalDrawer
import com.baroness.app.components.TopWarningBanner
import com.baroness.app.models.PersonaType
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.ChatListViewModel
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.viewmodels.SettingsViewModelFactory

@Composable
fun ChatListScreen(
    navController: NavController,
    viewModel: ChatListViewModel = viewModel(
        factory = ChatListViewModelFactory(LocalContext.current)
    ),
    settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current)
    )
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    var isDrawerVisible by remember { mutableStateOf(false) }

    val chatTypography = rememberChatTypography(settingsViewModel)

    val warningMessage by settingsViewModel.warningMessage.collectAsStateWithLifecycle()
    val isWarningVisible by settingsViewModel.isWarningVisible.collectAsStateWithLifecycle()
    val showWarningIcon by settingsViewModel.showWarningIcon.collectAsStateWithLifecycle()

    LaunchedEffect(isWarningVisible) {
        if (isWarningVisible) {
            kotlinx.coroutines.delay(2500)
            settingsViewModel.dismissWarning()
        }
    }

    BackHandler(enabled = isDrawerVisible) {
        isDrawerVisible = false
    }

    if (isInitialLoading && conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inbox",
                        style = chatTypography.header,
                        color = Color.White
                    )
                    Text(
                        text = "Connect with your favorites",
                        style = chatTypography.subtitle,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { isDrawerVisible = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }

            // Conversation List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(conversations) { conversation ->
                    ChatEntry(
                        conversation = conversation,
                        onClick = {
                            val conversationId = if (conversation.personaType == PersonaType.AI) "friday" 
                                                else if (conversation.id == "baroness_official") "baroness" 
                                                else "phesty"
                            navController.navigate("chat_room/$conversationId")
                        },
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }

        GlobalDrawer(
            isVisible = isDrawerVisible,
            onDismiss = { isDrawerVisible = false },
            viewModel = settingsViewModel
        )

        TopWarningBanner(
            visible = isWarningVisible,
            message = warningMessage ?: "",
            showIcon = showWarningIcon,
            onDismiss = { settingsViewModel.dismissWarning() }
        )
    }
}

class ChatListViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ChatListViewModel(context) as T
    }
}
