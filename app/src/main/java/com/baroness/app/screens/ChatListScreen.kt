package com.baroness.app.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.baroness.app.components.ChatEntry
import com.baroness.app.components.ChatTypography
import com.baroness.app.models.Conversation
import com.baroness.app.utils.UserSessionManager

@Composable
fun ChatListScreen(navController: NavController) {
    val context = LocalContext.current
    val sessionManager = remember { UserSessionManager(context) }
    
    val otherPersonaName = if (sessionManager.isBaroness) "Phesty" else "Baroness"
    val otherPersonaAvatar = if (sessionManager.isBaroness) 
        "https://baroness-test.vercel.app/bucket/Image-12.jpg" 
    else 
        "https://baroness-test.vercel.app/bucket/Image-11.jpg"

    // Initial mock data
    val conversations = remember(otherPersonaName) {
        listOf(
            Conversation(
                id = "other_persona",
                displayName = otherPersonaName,
                avatarUrl = otherPersonaAvatar,
                lastMessage = "Hey! How's your day going?",
                lastMessageTimestamp = System.currentTimeMillis() - 120000,
                type = "human"
            ),
            Conversation(
                id = "friday",
                displayName = "Friday",
                avatarUrl = "https://baroness-test.vercel.app/bucket/Image-2.jpg",
                lastMessage = "I've analyzed your schedule for tomorrow.",
                lastMessageTimestamp = System.currentTimeMillis() - 3600000,
                type = "ai"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Messages",
            color = Color.White,
            style = ChatTypography.nameStyle.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(conversations) { conversation ->
                ChatEntry(
                    conversation = conversation,
                    onClick = {
                        Toast.makeText(context, "Chat with ${conversation.displayName}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
