package com.baroness.app.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun EdgeGlowEffect(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(1500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.6f to Color.Red.copy(alpha = 0.10f),
                        0.9f to Color.Red.copy(alpha = 0.25f),
                        1.0f to Color.Red.copy(alpha = 0.35f),
                        radius = 2000f
                    )
                )
        )
    }
}
