package com.baroness.app.models

import androidx.compose.ui.graphics.Color

data class ThemeOption(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val bubbleColor: Color,
    val textColor: Color
)

data class FontOption(
    val id: String,
    val name: String
)

data class WallpaperOption(
    val id: String,
    val name: String,
    val colors: List<Color>
)

object SettingsOptions {
    val themes = listOf(
        ThemeOption("lavender", "Lavender", Color(0xFFE6E6FA), Color(0xFF9575CD), Color.White),
        ThemeOption("moonlight", "Moonlight", Color(0xFFB0C4DE), Color(0xFF546E7A), Color.White),
        ThemeOption("golden", "Golden", Color(0xFFFFD700), Color(0xFFFFA000), Color.Black),
        ThemeOption("rose", "Rose", Color(0xFFFFB6C1), Color(0xFFF06292), Color.White),
        ThemeOption("ocean", "Ocean", Color(0xFF87CEEB), Color(0xFF0288D1), Color.White)
    )

    val fonts = listOf(
        FontOption("system", "System"),
        FontOption("inter", "Inter"),
        FontOption("serif", "Serif"),
        FontOption("monospace", "Monospace")
    )

    val wallpapers = listOf(
        WallpaperOption("default", "Dark Gradient", listOf(Color(0xFF1A1A2E), Color(0xFF16213E))),
        WallpaperOption("midnight", "Midnight Blue", listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))),
        WallpaperOption("deep_space", "Deep Space", listOf(Color(0xFF000000), Color(0xFF1B1B2F))),
        WallpaperOption("slate", "Solid Slate", listOf(Color(0xFF202020), Color(0xFF202020)))
    )
}
