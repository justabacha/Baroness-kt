package com.baroness.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.baroness.app.components.ConversationItem
import com.baroness.app.components.MessagesTopBar

// Lightweight model for mock data
data class Conversation(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val online: Boolean = false
)

@Composable
fun MessagesScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    // Mock realistic conversations
    val conversations = remember {
        listOf(
            Conversation("1", "Sophia Miller", null, "See you at 7 — can't wait!", "2:15 PM", 2, true),
            Conversation("2", "Ethan Park", null, "Thanks — I pushed the update.", "1:04 PM", 0, false),
            Conversation("3", "Ava Thompson", null, "Haha that's hilarious 😂", "Yesterday", 1, true),
            Conversation("4", "Noah Williams", null, "Let's reschedule to Friday.", "Mon", 0, false),
            Conversation("5", "Olivia Brown", null, "Sent the photos — check your inbox.", "Sun", 0, true),
            Conversation("6", "Liam Johnson", null, "On my way.", "Sat", 5, false),
            Conversation("7", "Isabella Garcia", null, "Call me when you're free.", "Feb 10", 0, false)
        )
    }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter {
            it.name.contains(query, ignoreCase = true) || it.lastMessage.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            MessagesTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(filtered, key = { it.id }) { convo ->
                    ConversationItem(
                        conversation = convo,
                        onClick = { /* TODO: navigate to chat screen */ }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
