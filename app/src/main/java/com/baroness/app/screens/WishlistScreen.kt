package com.baroness.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

@Composable
fun WishlistScreen(
    navController: NavController,
    viewModel: WishlistViewModel = viewModel(
        factory = WishlistViewModelFactory(LocalContext.current)
    )
) {
    val wishes by viewModel.wishes.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val calendarVisible by viewModel.calendarVisible.collectAsState()
    val emojiVisible by viewModel.emojiVisible.collectAsState()
    val activeWishId by viewModel.activeWishId.collectAsState()
    val ratingVisible by viewModel.ratingVisible.collectAsState()
    val ratingWish by viewModel.ratingWish.collectAsState()
    val confirmVisible by viewModel.confirmVisible.collectAsState()
    val pendingDeleteId by viewModel.pendingDeleteId.collectAsState()
    val photoModalVisible by viewModel.photoModalVisible.collectAsState()
    val currentUserKey by viewModel.currentUserKey.collectAsState()
    val userNames by viewModel.userNames.collectAsState()
    val userAvatars by viewModel.userAvatars.collectAsState()
    val calendarAnchor by viewModel.calendarAnchor.collectAsState()

    var inputText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = BACKGROUND_IMAGE,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        AnimatedVisibility(
            visible = isInitialLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFff4d6d),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !isInitialLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                WishlistHeader(
                    stats = stats,
                    avatarP = userAvatars["P"],
                    avatarB = userAvatars["B"],
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                WishlistInput(
                    text = inputText,
                    onTextChange = { inputText = it },
                    selectedDate = selectedDate,
                    onCalendarClick = { viewModel.toggleCalendar(true) },
                    onCalendarPositioned = { position ->
                        viewModel.setCalendarAnchor(position)
                    },
                    onCast = {
                        if (inputText.isNotBlank() && selectedDate != null) {
                            val creatorId = if (currentUserKey == "P") "phesty_official" else "baroness_official"
                            viewModel.createWish(inputText.trim(), selectedDate!!, creatorId)
                            inputText = ""
                            viewModel.setSelectedDate(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = wishes,
                        key = { _, wish -> wish.id }
                    ) { index, wish ->
                        WishItem(
                            wish = wish,
                            index = index,
                            currentUserKey = currentUserKey,
                            onDust = { viewModel.dustWish(it) },
                            onDelete = { viewModel.toggleConfirmDialog(true, it) },
                            onOpenEmoji = { viewModel.toggleEmojiPicker(true, it) },
                            onOpenRating = { viewModel.toggleRatingModal(true, wish) },
                            onUploadPhotos = { viewModel.togglePhotoModal(true) }
                        )
                    }
                }
            }
        }

        if (calendarVisible) {
            CustomCalendar(
                visible = calendarVisible,
                anchorPosition = calendarAnchor,
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
                onEmojiSelected = { emoji ->
                    viewModel.saveReaction(activeWishId!!, emoji)
                    viewModel.toggleEmojiPicker(false)
                }
            )
        }

        if (ratingVisible && ratingWish != null) {
            RatingModal(
                wish = ratingWish!!,
                currentUserKey = currentUserKey,
                userNames = userNames,
                onDismiss = { viewModel.toggleRatingModal(false) },
                onRate = { rating ->
                    viewModel.saveRating(ratingWish!!.id, rating)
                    viewModel.toggleRatingModal(false)
                }
            )
        }

        if (confirmVisible && pendingDeleteId != null) {
            ConfirmModal(
                visible = confirmVisible,
                title = "Delete Wish",
                message = "Are you sure you want to delete this wish? This action cannot be undone.",
                onConfirm = {
                    viewModel.deleteWish(pendingDeleteId!!)
                    viewModel.toggleConfirmDialog(false)
                },
                onCancel = { viewModel.toggleConfirmDialog(false) }
            )
        }

        if (photoModalVisible) {
            ConfirmModal(
                visible = photoModalVisible,
                title = "Coming Soon",
                message = "Photo upload is not yet available. This feature will be added in a future update.",
                onConfirm = { viewModel.togglePhotoModal(false) },
                singleButton = true
            )
        }
    }
}

class WishlistViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return WishlistViewModel(context) as T
    }
}