package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.models.Wish
import com.baroness.app.models.WishStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WishlistViewModel(context: Context) : ViewModel() {
    private val repository = WishlistRepository(context)

    // UI state – explicitly typed
    private val _wishes = MutableStateFlow<List<Wish>>(emptyList())
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()

    private val _stats = MutableStateFlow(WishStats(0, 0))
    val stats: StateFlow<WishStats> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _calendarVisible = MutableStateFlow(false)
    val calendarVisible: StateFlow<Boolean> = _calendarVisible.asStateFlow()

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

    private val _currentUserKey = MutableStateFlow("P")
    val currentUserKey: StateFlow<String> = _currentUserKey.asStateFlow()

    init {
        loadWishes()
    }

    fun loadWishes() {
        viewModelScope.launch {
            _loading.value = true
            repository.getAllWishes().collect { wishList ->
                _wishes.value = wishList
                _stats.value = repository.getStats()
                _loading.value = false
            }
        }
    }

    fun createWish(text: String, date: String, creatorId: String) {
        viewModelScope.launch {
            val newWish = Wish(
                id = System.currentTimeMillis(),
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

    fun setSelectedDate(date: String?) {
        _selectedDate.value = date
    }

    fun toggleCalendar(show: Boolean) {
        _calendarVisible.value = show
    }

    fun toggleEmojiPicker(show: Boolean, wishId: Long? = null) {
        _emojiVisible.value = show
        _activeWishId.value = wishId
    }

    fun toggleRatingModal(show: Boolean, wish: Wish? = null) {
        _ratingVisible.value = show
        _ratingWish.value = wish
    }

    fun toggleConfirmDialog(show: Boolean, wishId: Long? = null) {
        _confirmVisible.value = show
        _pendingDeleteId.value = wishId
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
}