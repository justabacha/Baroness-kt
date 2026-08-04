package com.baroness.app.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

@Composable
fun DrawerStorage(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    hazeState: HazeState
) {
    GlassCategoryBox(
        title = "STORAGE",
        isExpanded = isExpanded,
        onExpand = onToggle,
        hazeState = hazeState
    ) {
        Text(
            text = "Coming soon...",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}
