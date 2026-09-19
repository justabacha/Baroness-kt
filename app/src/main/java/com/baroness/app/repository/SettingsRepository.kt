package com.baroness.app.repository

import android.content.Context
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class SettingsRepository(context: Context) {
    private val storageManager = StorageManager(context.applicationContext)

    companion object {
        private const val KEY_THEME = "selected_theme"
        private const val KEY_FONT = "selected_font"
        private const val KEY_WALLPAPER = "selected_wallpaper"
        private const val KEY_RECENT_EMOJIS = "recent_emojis"
        
        // Voice Settings
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_VOICE_PROVIDER = "voice_provider"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_DIRECTOR_NOTE = "voice_director_note"

        private const val DEFAULT_EMOJIS = "❤️,👍,👎,😂,‼️,❓,🤌"
    }

    fun getInitialTheme(): String = runBlocking { storageManager.getString(KEY_THEME) ?: "lavender" }
    fun getThemeFlow(): Flow<String> = storageManager.getStringFlow(KEY_THEME).map { it ?: "lavender" }
    suspend fun saveTheme(id: String) = storageManager.saveString(KEY_THEME, id)

    fun getInitialFont(): String = runBlocking { storageManager.getString(KEY_FONT) ?: "playfairdisplay_regular" }
    fun getFontFlow(): Flow<String> = storageManager.getStringFlow(KEY_FONT).map { it ?: "playfairdisplay_regular" }
    suspend fun saveFont(id: String) = storageManager.saveString(KEY_FONT, id)

    fun getInitialWallpaper(): String = runBlocking { storageManager.getString(KEY_WALLPAPER) ?: "sunrise" }
    fun getWallpaperFlow(): Flow<String> = storageManager.getStringFlow(KEY_WALLPAPER).map { it ?: "sunrise" }
    suspend fun saveWallpaper(id: String) = storageManager.saveString(KEY_WALLPAPER, id)

    fun getRecentEmojisFlow(): Flow<String> = storageManager.getStringFlow(KEY_RECENT_EMOJIS).map { it ?: DEFAULT_EMOJIS }
    suspend fun saveRecentEmojis(emojis: String) = storageManager.saveString(KEY_RECENT_EMOJIS, emojis)

    // Voice Settings Accessors
    fun getInitialVoiceEnabled(): Boolean = runBlocking { storageManager.getBoolean(KEY_VOICE_ENABLED) ?: true }
    fun getVoiceEnabledFlow(): Flow<Boolean> = storageManager.getBooleanFlow(KEY_VOICE_ENABLED).map { it ?: true }
    suspend fun saveVoiceEnabled(enabled: Boolean) = storageManager.saveBoolean(KEY_VOICE_ENABLED, enabled)

    fun getInitialVoiceProvider(): String = runBlocking { storageManager.getString(KEY_VOICE_PROVIDER) ?: "deepgram" }
    fun getVoiceProviderFlow(): Flow<String> = storageManager.getStringFlow(KEY_VOICE_PROVIDER).map { it ?: "deepgram" }
    suspend fun saveVoiceProvider(provider: String) = storageManager.saveString(KEY_VOICE_PROVIDER, provider)

    fun getInitialVoiceId(): String = runBlocking { storageManager.getString(KEY_VOICE_ID) ?: "aura-asteria-en" }
    fun getVoiceIdFlow(): Flow<String> = storageManager.getStringFlow(KEY_VOICE_ID).map { it ?: "aura-asteria-en" }
    suspend fun saveVoiceId(id: String) = storageManager.saveString(KEY_VOICE_ID, id)

    fun getInitialVoiceSpeed(): Float = runBlocking { storageManager.getFloat(KEY_VOICE_SPEED) ?: 1.0f }
    fun getVoiceSpeedFlow(): Flow<Float> = storageManager.getFloatFlow(KEY_VOICE_SPEED).map { it ?: 1.0f }
    suspend fun saveVoiceSpeed(speed: Float) = storageManager.saveFloat(KEY_VOICE_SPEED, speed)

    fun getInitialVoicePitch(): Float = runBlocking { storageManager.getFloat(KEY_VOICE_PITCH) ?: 1.0f }
    fun getVoicePitchFlow(): Flow<Float> = storageManager.getFloatFlow(KEY_VOICE_PITCH).map { it ?: 1.0f }
    suspend fun saveVoicePitch(pitch: Float) = storageManager.saveFloat(KEY_VOICE_PITCH, pitch)

    fun getInitialDirectorNote(): String = runBlocking { storageManager.getString(KEY_VOICE_DIRECTOR_NOTE) ?: "Speak in a clear, natural, and expressive tone." }
    fun getDirectorNoteFlow(): Flow<String> = storageManager.getStringFlow(KEY_VOICE_DIRECTOR_NOTE).map { it ?: "Speak in a clear, natural, and expressive tone." }
    suspend fun saveDirectorNote(note: String) = storageManager.saveString(KEY_VOICE_DIRECTOR_NOTE, note)
}
