package com.baroness.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@Composable
fun rememberCaptureManager(): CaptureManager {
    val context = LocalContext.current
    return remember { CaptureManager(context) }
}

class CaptureManager(private val context: Context) {
    private val authority = "${context.packageName}.fileprovider"

    suspend fun captureAndShare(graphicsLayer: GraphicsLayer, fileName: String = "shared_image.png") {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val bitmap = imageBitmap.asAndroidBitmap()
        val uri = saveToCache(bitmap, fileName)
        shareImage(uri)
    }

    private fun saveToCache(bitmap: Bitmap, fileName: String): Uri {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun shareImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Card"))
    }
}