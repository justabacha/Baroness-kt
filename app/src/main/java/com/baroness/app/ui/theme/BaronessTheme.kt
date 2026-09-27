package com.baroness.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.baroness.app.models.AppTheme
import com.baroness.app.models.SettingsOptions

/**
 * CompositionLocal provider for Baroness-specific visual theme tokens
 * (glowColor, bubbleFridayColor, bubbleUserColor, bgStart, bgEnd)
 * that standard Material 3 ColorScheme does not natively represent.
 */
val LocalBaronessTheme = staticCompositionLocalOf<AppTheme> {
    SettingsOptions.LavenderTheme
}

/**
 * CompositionLocal provider for Baroness-specific ChatTypography tokens.
 */
val LocalBaronessTypography = staticCompositionLocalOf<ChatTypography> {
    ChatTypography(
        fontFamily = AppFonts.PlayfairDisplay,
        baseWeight = androidx.compose.ui.text.font.FontWeight.Normal
    )
}
