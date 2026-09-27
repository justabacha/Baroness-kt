package com.baroness.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baroness.app.models.AppColors
import com.baroness.app.models.SettingsOptions
import com.baroness.app.viewmodels.SettingsViewModel

@Composable
fun BaronessAppTheme(
    settingsViewModel: SettingsViewModel? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val activeThemeId = settingsViewModel?.activeTheme?.collectAsStateWithLifecycle()?.value
        ?: SettingsOptions.DEFAULT_THEME_ID

    val activeFontId = settingsViewModel?.activeFont?.collectAsStateWithLifecycle()?.value
        ?: SettingsViewModel.DEFAULT_FONT

    val currentTheme = remember(activeThemeId) {
        SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    }

    val (fontFamily, fontWeight) = remember(activeFontId) {
        AppFonts.resolve(activeFontId) ?: Pair(FontFamily.Default, FontWeight.Normal)
    }

    val typography = remember(fontFamily, fontWeight) {
        buildTypography(fontFamily, fontWeight)
    }

    val chatTypography = remember(fontFamily, fontWeight) {
        ChatTypography(fontFamily = fontFamily, baseWeight = fontWeight)
    }

    val colorScheme = remember(currentTheme, darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = currentTheme.glowColor,
                secondary = currentTheme.glowColor,
                tertiary = currentTheme.bubbleFridayColor,
                background = currentTheme.bgStart,
                surface = currentTheme.bgStart,
                onPrimary = Color.White,
                onBackground = AppColors.textPrimary,
                onSurface = AppColors.textPrimary,
                primaryContainer = currentTheme.bgEnd,
                surfaceVariant = currentTheme.bgEnd
            )
        } else {
            lightColorScheme(
                primary = currentTheme.glowColor,
                secondary = currentTheme.glowColor,
                tertiary = currentTheme.bubbleFridayColor,
                background = currentTheme.bgStart,
                surface = currentTheme.bgStart,
                onPrimary = Color.White,
                onBackground = AppColors.textPrimary,
                onSurface = AppColors.textPrimary,
                primaryContainer = currentTheme.bgEnd,
                surfaceVariant = currentTheme.bgEnd
            )
        }
    }

    CompositionLocalProvider(
        LocalBaronessTheme provides currentTheme,
        LocalBaronessTypography provides chatTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
