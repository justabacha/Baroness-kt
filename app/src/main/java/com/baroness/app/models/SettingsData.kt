package com.baroness.app.models

import androidx.compose.ui.graphics.Color

data class AppTheme(
    val id: String,
    val name: String,
    val bgStart: Color,           // For room screen background later
    val bgEnd: Color,             // For room screen background later
    val glowColor: Color,         // Primary accent (solid, for swatches, active indicator, Apply button)
    val bubbleFridayColor: Color, // Receiver bubble (alpha ~0.5) — "other" person
    val bubbleUserColor: Color,   // Sender bubble (alpha ~0.1) — "own" messages
    val label: String             // Display name with emoji
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

object AppColors {
    val textPrimary = Color(0xFFF3EEFE)
    val textSoft = Color(0xFFCDC6E6)
    val white10 = Color(0x1AFFFFFF)
    val white20 = Color(0x33FFFFFF)
}

object SettingsOptions {
    const val DEFAULT_THEME_ID = "lavender"

    val LavenderTheme = AppTheme(
        id = "lavender",
        name = "Lavender",
        bgStart = Color(0xFF211D40),
        bgEnd = Color(0xFF2E2A5A),
        glowColor = Color(0xFFC6ABFF),
        bubbleFridayColor = Color(0x80BDA8FF),
        bubbleUserColor = Color(0x1AFFFFFF),
        label = "💜 Lavender Dream"
    )

    // Using the exact values from the prompt code block:
    val themes = listOf(
        AppTheme(
            id = "lavender",
            name = "Lavender",
            bgStart = Color(0xFF211D40),
            bgEnd = Color(0xFF2E2A5A),
            glowColor = Color(0xA8C6ABFF),        
            bubbleFridayColor = Color(0x80BDA8FF),
            bubbleUserColor = Color(0x1AFFFFFF),
            label = "💜 Lavender Dream"
        ),
        AppTheme(
            id = "moonlight",
            name = "Moonlight",
            bgStart = Color(0xFF0B1120),
            bgEnd = Color(0xFF1A1F2E),
            glowColor = Color(0x8786A0DD),        
            bubbleFridayColor = Color(0x806C81B5),
            bubbleUserColor = Color(0x1AFFFFFF),
            label = "🌙 Moonlight"
        ),
        AppTheme(
            id = "golden",
            name = "Golden",
            bgStart = Color(0xFF2F241B),
            bgEnd = Color(0xFF4A3727),
            glowColor = Color(0xAAF7CD7E),        
            bubbleFridayColor = Color(0x80EFC48C),
            bubbleUserColor = Color(0x1AFFFFFF),
            label = "🌅 Golden Hour"
        ),
        AppTheme(
            id = "rose",
            name = "Rose",
            bgStart = Color(0xFF3F1E2E),
            bgEnd = Color(0xFF2F1423),
            glowColor = Color(0xAAFFB7D0),        
            bubbleFridayColor = Color(0x80F5ADCA),
            bubbleUserColor = Color(0x1AFFFFFF),
            label = "🌹 Rose Glass"
        ),
        AppTheme(
            id = "ocean",
            name = "Ocean",
            bgStart = Color(0xFF0A2A2F),
            bgEnd = Color(0xFF0A1D28),
            glowColor = Color(0xAA70D0DD),        
            bubbleFridayColor = Color(0x8063BFC7),
            bubbleUserColor = Color(0x1AFFFFFF),
            label = "🌊 Deep Ocean"
        )
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
