package com.baroness.app.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.baroness.app.R
import java.io.File

enum class WallpaperSource { PREBUNDLED, USER_GALLERY }

data class WallpaperOption(
    val id: String,
    val name: String,
    val source: WallpaperSource,
    val resId: Int? = null,
    val filePath: String? = null
)

val prebundledWallpapers = listOf(
    WallpaperOption("sunrise", "Sunrise", WallpaperSource.PREBUNDLED, R.drawable.image_39),
    WallpaperOption("light_hours", "Light Hours", WallpaperSource.PREBUNDLED, R.drawable.light_hours),
    WallpaperOption("accent_bulb", "Accent Bulb", WallpaperSource.PREBUNDLED, R.drawable.accent_bulb),
    WallpaperOption("green_street", "Green Street", WallpaperSource.PREBUNDLED, R.drawable.green_street),
    WallpaperOption("sky_street", "Sky Street", WallpaperSource.PREBUNDLED, R.drawable.sky_street),
    WallpaperOption("beautiful_skies", "Beautiful Skies", WallpaperSource.PREBUNDLED, R.drawable.beautiful_skies),
    WallpaperOption("mountain_view", "Mountain View", WallpaperSource.PREBUNDLED, R.drawable.mountain_view),
    WallpaperOption("phesty_point", "Phesty Point", WallpaperSource.PREBUNDLED, R.drawable.phesty_point)
)

@Composable
fun DynamicBackground(activeWallpaperId: String) {
    val context = LocalContext.current
    val wallpaper = remember(activeWallpaperId) {
        val prebundled = prebundledWallpapers.find { it.id == activeWallpaperId }
        if (prebundled != null) prebundled else {
            val userFile = File(context.filesDir, "wallpapers/user_wallpaper.jpg")
            if (userFile.exists() && activeWallpaperId == "user_wallpaper") {
                WallpaperOption("user_wallpaper", "Custom", WallpaperSource.USER_GALLERY, filePath = userFile.absolutePath)
            } else {
                prebundledWallpapers.first()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val painter = when (wallpaper.source) {
            WallpaperSource.PREBUNDLED -> painterResource(id = wallpaper.resId!!)
            WallpaperSource.USER_GALLERY -> {
                val bitmap = BitmapFactory.decodeFile(wallpaper.filePath!!)
                if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
            }
        }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark Overlay Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )
    }
}
