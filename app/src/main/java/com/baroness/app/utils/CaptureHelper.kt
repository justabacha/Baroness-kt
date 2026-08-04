package com.baroness.app.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

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

    suspend fun captureAndSave(graphicsLayer: GraphicsLayer, fileName: String = "baroness_card.png") {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val bitmap = imageBitmap.asAndroidBitmap()
        val success = saveToGallery(bitmap, fileName)
        
        withContext(Dispatchers.Main) {
            if (success) {
                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveToGallery(bitmap: Bitmap, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val nameWithoutExtension = fileName.substringBeforeLast(".")
        val finalFileName = "${nameWithoutExtension}_${System.currentTimeMillis()}.png"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Baroness")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        return@withContext if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            false
        }
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