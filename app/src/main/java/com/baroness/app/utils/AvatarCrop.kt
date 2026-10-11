package com.baroness.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.yalantis.ucrop.UCrop
import java.io.File

object AvatarCrop {
    fun createIntent(context: Context, sourceUri: Uri): Intent {
        val destination = File(
            context.cacheDir,
            "avatar_crop_${System.currentTimeMillis()}.jpg"
        )
        val destinationUri = Uri.fromFile(destination)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
        }

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .getIntent(context)
    }
}
