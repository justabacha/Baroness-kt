package com.baroness.app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.data.models.NotificationData
import com.baroness.app.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val _currentNotification = MutableStateFlow<NotificationData?>(null)
    val currentNotification = _currentNotification.asStateFlow()

    private val queue = mutableListOf<NotificationData>()
    private var isDisplaying = false

    fun showInAppNotification(data: NotificationData) {
        viewModelScope.launch {
            val enabled = repository.getInAppBannersEnabledFlow().first()
            if (!enabled) return@launch

            queue.add(data)
            processQueue()
        }
    }

    private suspend fun processQueue() {
        if (isDisplaying || queue.isEmpty()) return

        isDisplaying = true
        val next = queue.removeAt(0)
        _currentNotification.value = next

        // Auto-dismiss after 5 seconds
        delay(5000)
        dismiss()
    }

    fun dismiss() {
        _currentNotification.value = null
        isDisplaying = false
        viewModelScope.launch {
            processQueue()
        }
    }
}
