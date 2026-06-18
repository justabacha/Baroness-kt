package com.baroness.app.data

import android.content.Context
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AvatarRepository(private val context: Context) {

    private val storage = StorageManager(context)
    private val client = OkHttpClient()
    private val avatarPathKey = "avatar_local_path"
    private val avatarUrlKey = "avatar_cached_url"

    suspend fun getCachedAvatar(avatarUrl: String?): String? {
        if (avatarUrl == null) return null

        val cachedUrl = storage.getString(avatarUrlKey)
        val cachedPath = storage.getString(avatarPathKey)

        if (cachedUrl == avatarUrl && cachedPath != null && File(cachedPath).exists()) {
            return cachedPath
        }

        return downloadAvatar(avatarUrl)
    }

    private suspend fun downloadAvatar(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@withContext null


                cleanOldAvatars()

                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                val avatarDir = File(context.filesDir, "user_avatars").apply { mkdirs() }
                val file = File(avatarDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }

                storage.saveString(avatarUrlKey, url)
                storage.saveString(avatarPathKey, file.absolutePath)

                file.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun cleanOldAvatars() {
        val avatarDir = File(context.filesDir, "user_avatars")
        val files = avatarDir.listFiles()?.filter { it.name.startsWith("avatar_") } ?: return
        files.forEach { it.delete() }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            val avatarDir = File(context.filesDir, "user_avatars")
            val files = avatarDir.listFiles()?.filter { it.name.startsWith("avatar_") } ?: return@withContext
            files.forEach { it.delete() }
            storage.remove(avatarPathKey)
            storage.remove(avatarUrlKey)
        }
    }
}