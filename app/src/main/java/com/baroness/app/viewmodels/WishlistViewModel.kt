package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.models.Wish
import com.baroness.app.models.WishStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset

class WishlistViewModel(context: Context) : ViewModel() {

    private val repository = WishlistRepository(context.applicationContext)

    // ─── Data from Room (persists across navigation) ───
    // Using stateIn with WhileSubscribed means the Flow stays alive while UI is active
    // and re-emits latest value when UI comes back
    val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep alive 5s after UI leaves
            initialValue = emptyList()
        )

    // Stats derived from wishes Flow - always in sync
    val stats: StateFlow<WishStats> = wishes.map { list ->
        WishStats(
            total = list.size,
            dusted = list.count { it.status == "dusted" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WishStats(0, 0)
    )

    // Loading only for initial empty state (Room is fast)
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    // ─── Modal States (ephemeral, don't need persistence) ───
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _calendarVisible = MutableStateFlow(false)
    val calendarVisible: StateFlow<Boolean> = _calendarVisible.asStateFlow()

    private val _calendarAnchor = MutableStateFlow<Offset?>(null)
    val calendarAnchor: StateFlow<Offset?> = _calendarAnchor.asStateFlow()

    private val _emojiVisible = MutableStateFlow(false)
    val emojiVisible: StateFlow<Boolean> = _emojiVisible.asStateFlow()

    private val _activeWishId = MutableStateFlow<Long?>(null)
    val activeWishId: StateFlow<Long?> = _activeWishId.asStateFlow()

    private val _ratingVisible = MutableStateFlow(false)
    val ratingVisible: StateFlow<Boolean> = _ratingVisible.asStateFlow()

    private val _ratingWish = MutableStateFlow<Wish?>(null)
    val ratingWish: StateFlow<Wish?> = _ratingWish.asStateFlow()

    private val _confirmVisible = MutableStateFlow(false)
    val confirmVisible: StateFlow<Boolean> = _confirmVisible.asStateFlow()

    private val _pendingDeleteId = MutableStateFlow<Long?>(null)
    val pendingDeleteId: StateFlow<Long?> = _pendingDeleteId.asStateFlow()

    private val _photoModalVisible = MutableStateFlow(false)
    val photoModalVisible: StateFlow<Boolean> = _photoModalVisible.asStateFlow()

    private val _currentUserKey = MutableStateFlow("P")
    val currentUserKey: StateFlow<String> = _currentUserKey.asStateFlow()

    private val _userNames = MutableStateFlow<Map<String, String>>(
        mapOf("P" to "Phesty", "B" to "Baroness")
    )
    val userNames: StateFlow<Map<String, String>> = _userNames.asStateFlow()

    private val _userAvatars = MutableStateFlow<Map<String, String?>>(
        mapOf("P" to null, "B" to null)
    )
    val userAvatars: StateFlow<Map<String, String?>> = _userAvatars.asStateFlow()

    init {
        // Turn off loading once we have data (Room is instant after first load)
        viewModelScope.launch {
            wishes.collect { list ->
                if (list.isNotEmpty()) {
                    _isInitialLoading.value = false
                }
            }
        }
        // Also turn off after short delay if empty (first launch)
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _isInitialLoading.value = false
        }
    }

    // ─── UI Actions ───
    fun setSelectedDate(date: String?) { _selectedDate.value = date }
    fun toggleCalendar(visible: Boolean) { _calendarVisible.value = visible }
    fun setCalendarAnchor(position: Offset) { _calendarAnchor.value = position }

    fun toggleEmojiPicker(visible: Boolean, wishId: Long? = null) {
        _emojiVisible.value = visible
        _activeWishId.value = wishId
    }

    fun toggleRatingModal(visible: Boolean, wish: Wish? = null) {
        _ratingVisible.value = visible
        _ratingWish.value = wish
    }

    fun toggleConfirmDialog(visible: Boolean, wishId: Long? = null) {
        _confirmVisible.value = visible
        _pendingDeleteId.value = wishId
    }

    fun togglePhotoModal(visible: Boolean) { _photoModalVisible.value = visible }

    // ─── CRUD (all go to Repository → Room → Sync) ───
    fun createWish(text: String, date: String, creatorId: String) {
        viewModelScope.launch {
            val tempId = -System.currentTimeMillis() // Negative temp ID
            val newWish = Wish(
                id = tempId,
                text = text,
                date = date,
                status = "planning",
                creator = if (creatorId == "phesty_official") "P" else "B",
                reactions = emptyMap(),
                ratings = emptyMap(),
                createdAt = System.currentTimeMillis()
            )
            repository.insertWish(newWish)
        }
    }

    fun deleteWish(wishId: Long) {
        viewModelScope.launch {
            repository.deleteWish(wishId)
        }
    }

    fun dustWish(wishId: Long) {
        viewModelScope.launch {
            repository.updateWishStatus(wishId, "dusted")
        }
    }

    fun saveReaction(wishId: Long, emoji: String) {
        viewModelScope.launch {
            val personaId = if (_currentUserKey.value == "P") "phesty_official" else "baroness_official"
            repository.saveReaction(wishId, personaId, emoji)
        }
    }

    fun saveRating(wishId: Long, rating: Int) {
        viewModelScope.launch {
            val personaId = if (_currentUserKey.value == "P") "phesty_official" else "baroness_official"
            repository.saveRating(wishId, personaId, rating)
        }
    }

    fun setCurrentUserKey(key: String) {
        _currentUserKey.value = key
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}