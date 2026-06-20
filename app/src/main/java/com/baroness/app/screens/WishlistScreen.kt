package com.baroness.app.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.baroness.app.components.EmojiPicker
import com.baroness.app.components.wishlist.*
import com.baroness.app.models.Wish
import com.baroness.app.viewmodels.WishlistViewModel

private val BACKGROUND_IMAGE = "https://baroness-test.vercel.app/bucket/Image-15.jpg"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    navController: NavController,
    viewModel: WishlistViewModel = viewModel(
        factory = WishlistViewModelFactory(LocalContext.current)
    )
) {
    val wishes by viewModel.wishes.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val calendarVisible by viewModel.calendarVisible.collectAsState()
    val emojiVisible by viewModel.emojiVisible.collectAsState()
    val activeWishId by viewModel.activeWishId.collectAsState()
    val ratingVisible by viewModel.ratingVisible.collectAsState()
    val ratingWish by viewModel.ratingWish.collectAsState()
    val confirmVisible by viewModel.confirmVisible.collectAsState()
    val pendingDeleteId by viewModel.pendingDeleteId.collectAsState()
    val currentUserKey by viewModel.currentUserKey.collectAsState()

    var inputText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = BACKGROUND_IMAGE,
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            WishlistHeader(
                stats = stats,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            WishlistInput(
                text = inputText,
                onTextChange = { inputText = it },
                selectedDate = selectedDate,
                onCalendarClick = { viewModel.toggleCalendar(true) },
                onCast = {
                    if (inputText.isNotBlank() && selectedDate != null) {
                        val creatorId = if (currentUserKey == "P") "phesty_official" else "baroness_official"
                        viewModel.createWish(inputText, selectedDate!!, creatorId)
                        inputText = ""
                        viewModel.setSelectedDate(null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFff4d6d))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(wishes, key = { it.id }) { wish ->
                        WishItem(
                            wish = wish,
                            index = wishes.indexOf(wish),
                            currentUserKey = currentUserKey,
                            onDust = { viewModel.dustWish(it) },
                            onDelete = { viewModel.toggleConfirmDialog(true, it) },
                            onOpenEmoji = { viewModel.toggleEmojiPicker(true, it) },
                            onOpenRating = { viewModel.toggleRatingModal(true, wish) },
                            onUploadPhotos = { /* TODO: photo upload */ }
                        )
                    }
                }
            }
        }

        // Modals
        if (calendarVisible) {
            CustomCalendar(
                selectedDate = selectedDate,
                onSelectDate = { date ->
                    viewModel.setSelectedDate(date)
                    viewModel.toggleCalendar(false)
                },
                onDismiss = { viewModel.toggleCalendar(false) }
            )
        }

        if (emojiVisible && activeWishId != null) {
            EmojiPicker(
                visible = emojiVisible,
                onDismiss = { viewModel.toggleEmojiPicker(false) },
                onEmojiSelected = { emoji: String ->   // <-- explicit type
                    viewModel.saveReaction(activeWishId!!, emoji)
                    viewModel.toggleEmojiPicker(false)
                }
            )
        }

        if (ratingVisible && ratingWish != null) {
            RatingModal(
                wish = ratingWish!!,
                currentUserKey = currentUserKey,
                userNames = mapOf("P" to "Phesty", "B" to "Baroness"),
                onDismiss = { viewModel.toggleRatingModal(false) },
                onRate = { rating ->
                    viewModel.saveRating(ratingWish!!.id, rating)
                    viewModel.toggleRatingModal(false)
                }
            )
        }

        if (confirmVisible && pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleConfirmDialog(false) },
                title = { Text("Delete Wish") },
                text = { Text("Are you sure you want to delete this wish? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteWish(pendingDeleteId!!)
                            viewModel.toggleConfirmDialog(false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFff4d6d))
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleConfirmDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ViewModel Factory
class WishlistViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return WishlistViewModel(context) as T
    }
}