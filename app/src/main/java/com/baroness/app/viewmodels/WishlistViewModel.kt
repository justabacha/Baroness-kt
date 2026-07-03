package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.models.Wish
import com.baroness.app.models.WishStats
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset

class WishlistViewModel(context: Context) : ViewModel() {

    private val repository = WishlistRepository(context.applicationContext)
    private val storageManager = StorageManager(context.applicationContext)

    val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val stats: StateFlow<WishStats> = repository.getStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WishStats(0, 0)
        )

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    // Modal States
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

    private val _currentUserId = MutableStateFlow("phesty_official")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    val userNames: StateFlow<Map<String, String>> = repository.profiles.map { profileMap ->
        mapOf(
            "P" to (profileMap["phesty_official"]?.displayName ?: "Phesty"),
            "B" to (profileMap["baroness_official"]?.displayName ?: "Baroness")
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = mapOf("P" to "Phesty", "B" to "Baroness")
    )

    val userAvatars: StateFlow<Map<String, String?>> = repository.profiles.map { profileMap ->
        mapOf(
            "P" to profileMap["phesty_official"]?.avatarUrl,
            "B" to profileMap["baroness_official"]?.avatarUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = mapOf("P" to null, "B" to null)
    )

    init {
        viewModelScope.launch {
            loadUserIdentity()
        }

        viewModelScope.launch {
            wishes.collect { list ->
                if (list.isNotEmpty()) {
                    _isInitialLoading.value = false
                }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _isInitialLoading.value = false
        }
    }

    private suspend fun loadUserIdentity() {
        val personaId = storageManager.getString("currentPersonaId") ?: "phesty_official"
        _currentUserId.value = personaId
        _currentUserKey.value = if (personaId == "phesty_official") "P" else "B"
        Log.d("WishlistVM", "Loaded identity: $personaId -> key=${_currentUserKey.value}")
    }

    // UI Actions
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

    fun createWish(text: String, date: String) {
        viewModelScope.launch {
            val tempId = -System.currentTimeMillis()
            val userId = _currentUserId.value
            val creator = _currentUserKey.value

            Log.d("WishlistVM", "Creating wish as $userId (key=$creator)")

            val newWish = Wish(
                id = tempId,
                text = text,
                date = date,
                status = "planning",
                creator = creator,
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
            val personaId = _currentUserId.value
            repository.saveReaction(wishId, personaId, emoji)
        }
    }

    fun saveRating(wishId: Long, rating: Int) {
        viewModelScope.launch {
            val personaId = _currentUserId.value
            repository.saveRating(wishId, personaId, rating)
        }
    }

}