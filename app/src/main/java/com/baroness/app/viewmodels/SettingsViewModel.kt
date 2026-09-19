package com.baroness.app.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.repository.SettingsRepository
import com.baroness.app.voice.VoiceCenter
import com.baroness.app.voice.VoiceConfig
import com.baroness.app.voice.VoiceContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = SettingsRepository(appContext)
    private val voiceCenter = VoiceCenter(appContext)

    // Warning State
    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()
    
    private val _isWarningVisible = MutableStateFlow(false)
    val isWarningVisible: StateFlow<Boolean> = _isWarningVisible.asStateFlow()

    private val _showWarningIcon = MutableStateFlow(true)
    val showWarningIcon: StateFlow<Boolean> = _showWarningIcon.asStateFlow()

    // THEME
    val activeTheme: StateFlow<String> = repository.getThemeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialTheme())
    
    private val _previewTheme = MutableStateFlow(repository.getInitialTheme())
    val previewTheme: StateFlow<String> = _previewTheme.asStateFlow()

    // FONT
    val activeFont: StateFlow<String> = repository.getFontFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialFont())
    
    private val _previewFont = MutableStateFlow(repository.getInitialFont())
    val previewFont: StateFlow<String> = _previewFont.asStateFlow()

    // WALLPAPER
    val activeWallpaper: StateFlow<String> = repository.getWallpaperFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialWallpaper())
    
    private val _previewWallpaper = MutableStateFlow(repository.getInitialWallpaper())
    val previewWallpaper: StateFlow<String> = _previewWallpaper.asStateFlow()

    // VOICE
    val voiceEnabled: StateFlow<Boolean> = repository.getVoiceEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialVoiceEnabled())

    val voiceProvider: StateFlow<String> = repository.getVoiceProviderFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialVoiceProvider())

    val voiceId: StateFlow<String> = repository.getVoiceIdFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialVoiceId())

    val voiceSpeed: StateFlow<Float> = repository.getVoiceSpeedFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialVoiceSpeed())

    val voicePitch: StateFlow<Float> = repository.getVoicePitchFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialVoicePitch())

    val directorNote: StateFlow<String> = repository.getDirectorNoteFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getInitialDirectorNote())

    // Voice State
    val voiceState = voiceCenter.state

    // EMOJIS
    val recentEmojis: StateFlow<List<String>> = repository.getRecentEmojisFlow()
        .map { it.split(",") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf("❤️", "👍", "👎", "😂", "‼️", "❓", "🤌"))

    private val emojiFrequencyMap = mutableMapOf<String, Int>()

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

    // VOICE Actions
    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.saveVoiceEnabled(enabled) }
    }

    fun setVoiceProvider(provider: String) {
        viewModelScope.launch { 
            repository.saveVoiceProvider(provider)
            // Set sensible default voice for the provider
            val defaultId = when(provider) {
                "murf" -> "en-US-marcus"
                "deepgram" -> "aura-asteria-en"
                "edge" -> "en-US-JennyNeural"
                else -> "aura-asteria-en"
            }
            repository.saveVoiceId(defaultId)
        }
    }

    fun setVoiceId(id: String) {
        viewModelScope.launch { repository.saveVoiceId(id) }
    }

    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch { repository.saveVoiceSpeed(speed) }
    }

    fun setVoicePitch(pitch: Float) {
        viewModelScope.launch { repository.saveVoicePitch(pitch) }
    }

    fun setDirectorNote(note: String) {
        viewModelScope.launch { repository.saveDirectorNote(note) }
    }

    fun previewVoice() {
        if (voiceState.value != com.baroness.app.voice.VoiceState.IDLE) return
        
        val config = VoiceConfig(
            voiceId = voiceId.value,
            speed = voiceSpeed.value,
            pitch = voicePitch.value,
            provider = voiceProvider.value,
            directorNote = directorNote.value
        )
        voiceCenter.speak("This is a preview of AVIA with your current AVIS settings. How do I sound?", VoiceContext(config))
    }

    fun stopVoice() {
        voiceCenter.stop()
    }

    override fun onCleared() {
        voiceCenter.shutdown()
        super.onCleared()
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

    // EMOJI Actions
    fun onEmojiUsed(emoji: String, isDirect: Boolean = true) {
        viewModelScope.launch {
            if (!isDirect) {
                // For text input, only add if frequency > 3
                val count = (emojiFrequencyMap[emoji] ?: 0) + 1
                if (count < 3) {
                    emojiFrequencyMap[emoji] = count
                    return@launch
                } else {
                    // Threshold reached, reset count for this emoji
                    emojiFrequencyMap[emoji] = 0
                }
            }

            val current = recentEmojis.value.toMutableList()
            // Remove if already exists, then add to front
            current.remove(emoji)
            current.add(0, emoji)
            // Keep top 7
            val updated = current.take(7).joinToString(",")
            repository.saveRecentEmojis(updated)
        }
    }
}

class SettingsViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(context.applicationContext as Application) as T
    }
}
