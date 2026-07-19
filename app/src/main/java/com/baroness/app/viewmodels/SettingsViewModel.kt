package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(context: Context) : ViewModel() {
    private val repository = SettingsRepository(context.applicationContext)

    // Warning State
    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()
    
    private val _isWarningVisible = MutableStateFlow(false)
    val isWarningVisible: StateFlow<Boolean> = _isWarningVisible.asStateFlow()

    // THEME
    val activeTheme: StateFlow<String> = repository.getThemeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "lavender")
    
    private val _previewTheme = MutableStateFlow("lavender")
    val previewTheme: StateFlow<String> = _previewTheme.asStateFlow()

    // FONT
    val activeFont: StateFlow<String> = repository.getFontFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    
    private val _previewFont = MutableStateFlow("system")
    val previewFont: StateFlow<String> = _previewFont.asStateFlow()

    // WALLPAPER
    val activeWallpaper: StateFlow<String> = repository.getWallpaperFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "default")
    
    private val _previewWallpaper = MutableStateFlow("default")
    val previewWallpaper: StateFlow<String> = _previewWallpaper.asStateFlow()

    init {
        // Initialize preview states with active values when they are first loaded
        viewModelScope.launch {
            activeTheme.collectLatest { _previewTheme.value = it }
        }
        viewModelScope.launch {
            activeFont.collectLatest { _previewFont.value = it }
        }
        viewModelScope.launch {
            activeWallpaper.collectLatest { _previewWallpaper.value = it }
        }
    }

    fun showWarning(message: String) {
        _warningMessage.value = message
        _isWarningVisible.value = true
    }

    fun dismissWarning() {
        _isWarningVisible.value = false
    }

    // THEME Actions
    companion object {
        const val DEFAULT_THEME = "lavender"
    }

    fun previewTheme(id: String) { _previewTheme.value = id }
    fun applyTheme() {
        viewModelScope.launch {
            repository.saveTheme(_previewTheme.value)
        }
    }
    fun revertTheme() { _previewTheme.value = activeTheme.value }

    fun revertToDefault() {
        viewModelScope.launch {
            repository.saveTheme(DEFAULT_THEME)
            _previewTheme.value = DEFAULT_THEME
        }
    }

    // FONT Actions
    fun previewFont(id: String) { _previewFont.value = id }
    fun applyFont() {
        viewModelScope.launch {
            repository.saveFont(_previewFont.value)
        }
    }
    fun revertFont() { _previewFont.value = activeFont.value }

    // WALLPAPER Actions
    fun previewWallpaper(id: String) { _previewWallpaper.value = id }
    fun applyWallpaper() {
        viewModelScope.launch {
            repository.saveWallpaper(_previewWallpaper.value)
        }
    }
    fun revertWallpaper() { _previewWallpaper.value = activeWallpaper.value }
}
