package com.baroness.app.components

sealed class ThemeBoxState {
    object Collapsed : ThemeBoxState()
    object Expanded : ThemeBoxState()
    data class Preview(val themeId: String) : ThemeBoxState()
}

sealed class FontBoxState {
    object Collapsed : FontBoxState()
    object Expanded : FontBoxState()
    data class WeightSelect(val familyId: String) : FontBoxState()
    data class Preview(val familyId: String, val weightId: String?) : FontBoxState()
}

sealed class WallpaperBoxState {
    object Collapsed : WallpaperBoxState()
    object Expanded : WallpaperBoxState()
    data class Preview(val wallpaperId: String) : WallpaperBoxState()
}
