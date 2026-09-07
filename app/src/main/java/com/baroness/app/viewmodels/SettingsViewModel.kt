package com.baroness.app.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = SettingsRepository(appContext)

    // Warning State
    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()
    
    private val _isWarningVisible = MutableStateFlow(false)
    val isWarningVisible: StateFlow<Boolean> = _isWarningVisible.asStateFlow()

    private val _showWarningIcon = MutableStateFlow(true)
    val showWarningIcon: StateFlow<Boolean> = _showWarningIcon.asStateFlow()

    // THEME
    val activeTheme: StateFlow<String> = repository.getThemeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "lavender")
    
    private val _previewTheme = MutableStateFlow("lavender")
    val previewTheme: StateFlow<String> = _previewTheme.asStateFlow()

    // FONT
    val activeFont: StateFlow<String> = repository.getFontFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_FONT)
    
    private val _previewFont = MutableStateFlow(DEFAULT_FONT)
    val previewFont: StateFlow<String> = _previewFont.asStateFlow()

    // WALLPAPER
    val activeWallpaper: StateFlow<String> = repository.getWallpaperFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "sunrise")
    
    private val _previewWallpaper = MutableStateFlow("sunrise")
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

    fun showWarning(message: String, showIcon: Boolean = true) {
        _warningMessage.value = message
        _showWarningIcon.value = showIcon
        _isWarningVisible.value = true
    }

    fun dismissWarning() {
        _isWarningVisible.value = false
    }

    // THEME Actions
    companion object {
        const val DEFAULT_THEME = "lavender"
        const val DEFAULT_FONT = "playfairdisplay_regular"
        const val DEFAULT_WALLPAPER = "sunrise"
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
    fun previewFont(familyId: String, weightId: String? = null) {
        _previewFont.value = if (weightId != null) "${familyId}_$weightId" else familyId
    }

    fun applyFont() {
        viewModelScope.launch {
            repository.saveFont(_previewFont.value)
        }
    }

    fun revertFont() {
        _previewFont.value = activeFont.value
    }

    fun revertFontToDefault() {
        viewModelScope.launch {
            repository.saveFont(DEFAULT_FONT)
            _previewFont.value = DEFAULT_FONT
        }
    }

    // WALLPAPER Actions
    fun previewWallpaper(id: String) { _previewWallpaper.value = id }
    fun applyWallpaper() {
        viewModelScope.launch {
            repository.saveWallpaper(_previewWallpaper.value)
        }
    }
    fun revertWallpaper() { _previewWallpaper.value = activeWallpaper.value }

    fun revertWallpaperToDefault() {
        viewModelScope.launch {
            repository.saveWallpaper(DEFAULT_WALLPAPER)
            _previewWallpaper.value = DEFAULT_WALLPAPER
        }
    }

    fun setUserWallpaper(uri: Uri) {
        val wallpapersDir = File(appContext.filesDir, "wallpapers")
        if (!wallpapersDir.exists()) wallpapersDir.mkdirs()
        
        // Delete old
        val userWallpaperFile = File(wallpapersDir, "user_wallpaper.jpg")
        if (userWallpaperFile.exists()) userWallpaperFile.delete()
        
        // Save new
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                userWallpaperFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Set active immediately
            viewModelScope.launch {
                repository.saveWallpaper("user_wallpaper")
                _previewWallpaper.value = "user_wallpaper"
            }
        } catch (e: Exception) {
            showWarning("Failed to save wallpaper: ${e.localizedMessage}")
        }
    }
}

class SettingsViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(context.applicationContext as Application) as T
    }
}
