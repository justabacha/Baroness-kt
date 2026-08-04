package com.baroness.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baroness.app.viewmodels.SettingsViewModel

@Composable
fun rememberChatTypography(settingsViewModel: SettingsViewModel = viewModel()): ChatTypography {
    val activeFontId by settingsViewModel.activeFont.collectAsStateWithLifecycle()
    val (fontFamily, fontWeight) = AppFonts.resolve(activeFontId)
        ?: Pair(FontFamily.Default, FontWeight.Normal)

    return ChatTypography(
        fontFamily = fontFamily,
        baseWeight = fontWeight
    )
}

class ChatTypography(
    private val fontFamily: FontFamily,
    private val baseWeight: FontWeight
) {
    val title = TextStyle(
        fontFamily = fontFamily,
        fontWeight = baseWeight, // Uniform weight — hierarchy from size, not weight
        fontSize = 17.sp,
        letterSpacing = (-0.4).sp
    )

    val header = TextStyle(
        fontFamily = fontFamily,
        fontWeight = baseWeight,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    )

    val subtitle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = baseWeight,
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.6f),
        lineHeight = 20.sp
    )

    val meta = TextStyle(
        fontFamily = fontFamily,
        fontWeight = baseWeight,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.4f)
    )

    val body = TextStyle(
        fontFamily = fontFamily,
        fontWeight = baseWeight,
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.9f)
    )
}
