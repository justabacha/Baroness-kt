package com.baroness.app.repository

import android.content.Context
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(context: Context) {
    private val storageManager = StorageManager(context.applicationContext)

    companion object {
        private const val KEY_THEME = "selected_theme"
        private const val KEY_FONT = "selected_font"
        private const val KEY_WALLPAPER = "selected_wallpaper"
    }

    fun getThemeFlow(): Flow<String> = storageManager.getStringFlow(KEY_THEME).map { it ?: "lavender" }
    suspend fun saveTheme(id: String) = storageManager.saveString(KEY_THEME, id)

    fun getFontFlow(): Flow<String> = storageManager.getStringFlow(KEY_FONT).map { it ?: "playfairdisplay_regular" }
    suspend fun saveFont(id: String) = storageManager.saveString(KEY_FONT, id)

    fun getWallpaperFlow(): Flow<String> = storageManager.getStringFlow(KEY_WALLPAPER).map { it ?: "sunrise" }
    suspend fun saveWallpaper(id: String) = storageManager.saveString(KEY_WALLPAPER, id)
}
