package com.baroness.app.models

import androidx.compose.ui.graphics.Color

data class ThemeOption(
    val id: String,
    val name: String,
    val primaryColor: Color
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
        ThemeOption("lavender", "Lavender", Color(0xFFE6E6FA)),
        ThemeOption("moonlight", "Moonlight", Color(0xFFB0C4DE)),
        ThemeOption("golden", "Golden", Color(0xFFFFD700)),
        ThemeOption("rose", "Rose", Color(0xFFFFB6C1)),
        ThemeOption("ocean", "Ocean", Color(0xFF87CEEB))
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
