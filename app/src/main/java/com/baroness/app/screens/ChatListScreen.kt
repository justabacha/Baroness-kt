package com.baroness.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.baroness.app.components.ChatEntry
import com.baroness.app.components.InAppMiniPlayer
import com.baroness.app.components.TopWarningBanner
import com.baroness.app.models.PersonaType
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.ChatListViewModel
import com.baroness.app.viewmodels.SettingsViewModel

@Composable
fun ChatListScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    viewModel: ChatListViewModel = viewModel(
        factory = ChatListViewModelFactory(LocalContext.current)
    )
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
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

        TopWarningBanner(
            visible = isWarningVisible,
            message = warningMessage ?: "",
            showIcon = showWarningIcon,
            onDismiss = { settingsViewModel.dismissWarning() }
        )

        InAppMiniPlayer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

class ChatListViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ChatListViewModel(context) as T
    }
}
