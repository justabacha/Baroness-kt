package com.baroness.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.models.PhotoItem
import com.baroness.app.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface PhotosUiState {
    object Loading : PhotosUiState
    data class Success(
        val groupedPhotos: Map<String, List<PhotoItem>>,
        val totalCount: Int,
        val flatList: List<PhotoItem>
    ) : PhotosUiState
    data class Error(val message: String) : PhotosUiState
}

class PhotosViewModel : ViewModel() {

    private val repository = PhotoRepository()

    private val _uiState = MutableStateFlow<PhotosUiState>(PhotosUiState.Loading)
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    // Tracks selected photo index in the flat list for the full screen viewer
    private val _selectedPhotoIndex = MutableStateFlow<Int?>(null)
    val selectedPhotoIndex: StateFlow<Int?> = _selectedPhotoIndex.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = PhotosUiState.Loading
            repository.fetchPhotos()
                .catch { exception ->
                    _uiState.value = PhotosUiState.Error(exception.message ?: "Unknown Error occurred")
                }
                .collect { photos ->
                    val grouped = groupPhotosByPeriod(photos)
                    _uiState.value = PhotosUiState.Success(
                        groupedPhotos = grouped,
                        totalCount = photos.size,
                        flatList = photos
                    )
                }
        }
    }

    fun selectPhoto(index: Int?) {
        _selectedPhotoIndex.value = index
    }

    private fun groupPhotosByPeriod(photos: List<PhotoItem>): Map<String, List<PhotoItem>> {
        val groups = photos.groupBy { getCategory(it.dateString) }
        
        // Return a linked map matching chronological order: Today, Yesterday, This Week, Earlier
        val orderedKeys = listOf("Today", "Yesterday", "This Week", "Earlier")
        val result = LinkedHashMap<String, List<PhotoItem>>()
        for (key in orderedKeys) {
            val list = groups[key]
            if (!list.isNullOrEmpty()) {
                result[key] = list
            }
        }
        // Fallback for any other keys not covered in standard list
        groups.keys.forEach { key ->
            if (key !in orderedKeys) {
                result[key] = groups[key]!!
            }
        }
        return result
    }

    private fun getCategory(dateString: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val photoDate = try {
            sdf.parse(dateString)
        } catch (_: Exception) {
            null
        } ?: return "Earlier"

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val sevenDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = Calendar.getInstance().apply {
            time = photoDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when {
            target.timeInMillis >= today.timeInMillis -> "Today"
            target.timeInMillis >= yesterday.timeInMillis -> "Yesterday"
            target.timeInMillis >= sevenDaysAgo.timeInMillis -> "This Week"
            else -> "Earlier"
        }
    }
}
