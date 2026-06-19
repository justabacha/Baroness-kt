package com.baroness.app.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.baroness.app.components.EmojiPicker
import com.baroness.app.components.PhestyText

private const val TAG = "WishlistScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(navController: NavController) {
    var textInput by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Log when emoji picker visibility changes
    LaunchedEffect(showEmojiPicker) {
        Log.d(TAG, "Emoji picker visibility: $showEmojiPicker")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wishlist (Emoji Test)",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = {
                Log.d(TAG, "Text input: $it")
                textInput = it
            },
            label = { Text("Enter text with emojis") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFff4d6d),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                Log.d(TAG, "Open Emoji Picker button clicked")
                showEmojiPicker = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFff4d6d))
        ) {
            Text("Open Emoji Picker")
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (textInput.isNotEmpty()) {
            Text(
                text = "Rendered text:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhestyText(
                text = textInput,
                fontSize = 20.sp,
                color = Color.White
            )
        } else {
            Text(
                text = "Type something with emojis to see them rendered",
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        EmojiPicker(
            visible = showEmojiPicker,
            onDismiss = {
                Log.d(TAG, "Emoji picker dismissed")
                showEmojiPicker = false
            },
            onEmojiSelected = { emoji ->
                Log.d(TAG, "Emoji selected: $emoji")
                textInput += emoji
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                Log.d(TAG, "Go Back clicked")
                navController.popBackStack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
        ) {
            Text("Go Back")
        }
    }
}