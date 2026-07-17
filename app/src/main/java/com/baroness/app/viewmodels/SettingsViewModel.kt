package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(context: Context) : ViewModel() {
    private val storageManager = StorageManager(context.applicationContext)

    val selectedTheme: StateFlow<String> = storageManager.getStringFlow("selected_theme")
        .map { it ?: "lavender" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "lavender")

    val selectedFont: StateFlow<String> = storageManager.getStringFlow("selected_font")
        .map { it ?: "system" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val selectedWallpaper: StateFlow<String> = storageManager.getStringFlow("selected_wallpaper")
        .map { it ?: "default" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "default")

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            storageManager.saveString("selected_theme", themeId)
        }
    }

    fun setFont(fontId: String) {
        viewModelScope.launch {
            storageManager.saveString("selected_font", fontId)
        }
    }

    fun setWallpaper(wallpaperId: String) {
        viewModelScope.launch {
            storageManager.saveString("selected_wallpaper", wallpaperId)
        }
    }
}
