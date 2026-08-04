package com.baroness.app.data

import android.content.Context
import com.baroness.app.models.VibeQuote
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.VibeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QuoteRepository(context: Context) {

    private val storage = StorageManager(context)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val imagesDir = File(context.filesDir, "quote_images").apply { mkdirs() }

    private val cacheDateKey = "quote_cache_date"
    private val quoteDataKey = "quote_data"

    init {
        println("QuoteRepository imagesDir: ${imagesDir.absolutePath}")
        println("QuoteRepository imagesDir exists: ${imagesDir.exists()}")
        println("QuoteRepository images in dir: ${imagesDir.listFiles()?.map { "${it.name} (${it.length()} bytes)" }}")
    }

    suspend fun getTodayQuote(forceRefresh: Boolean = false): VibeQuote {
        val today = dateFormat.format(Date())
        val cachedDate = storage.getString(cacheDateKey)

        println("QuoteRepository today: $today, cachedDate: $cachedDate, forceRefresh: $forceRefresh")

        if (!forceRefresh && cachedDate == today) {
            val cachedJson = storage.getString(quoteDataKey)
            println("QuoteRepository cachedJson: ${cachedJson?.take(100)}...")

            cachedJson?.let {
                try {
                    val cachedQuote = json.decodeFromString<VibeQuote>(it)
                    val currentVibe = VibeManager.getVibeOfTheDay()

                    // Check if the vibe content itself has changed (Issue #3)
                    if (cachedQuote.part1 == currentVibe.part1 && cachedQuote.part2 == currentVibe.part2) {
                        println("QuoteRepository cache matches current vibe content")
                        val image1Exists = cachedQuote.photo1.let { path -> File(path).exists() }
                        val image2Exists = cachedQuote.photo2.let { path -> File(path).exists() }

                        if (image1Exists && image2Exists) {
                            return cachedQuote
                        }
                    } else {
                        println("QuoteRepository cache content mismatch, forcing refresh")
                    }
                } catch (e: Exception) {
                    println("QuoteRepository cache parse error: ${e.message}")
                }
            }
        }

        return try {
            fetchFreshQuote(today)
        } catch (e: Exception) {
            println("QuoteRepository fetchFreshQuote failed: ${e.message}")

            val cachedJson = storage.getString(quoteDataKey)
            cachedJson?.let {
                try {
                    val quote = json.decodeFromString<VibeQuote>(it)
                    val image1Exists = quote.photo1.let { path -> File(path).exists() }
                    val image2Exists = quote.photo2.let { path -> File(path).exists() }

                    if (image1Exists || image2Exists) {
                        return quote
                    }
                    quote
                } catch (_: Exception) {
                    VibeManager.getVibeOfTheDay()
                }
            } ?: VibeManager.getVibeOfTheDay()
        }
    }

    private suspend fun fetchFreshQuote(today: String): VibeQuote {
        val vibe = VibeManager.getVibeOfTheDay()
        println("QuoteRepository vibe photo1: ${vibe.photo1}")
        println("QuoteRepository vibe photo2: ${vibe.photo2}")

        val uniqueId = today.hashCode().toString()
        val name1 = getFilenameFromUrl(vibe.photo1, "quote_1_$uniqueId")
        val name2 = getFilenameFromUrl(vibe.photo2, "quote_2_$uniqueId")

        val image1 = downloadImage(vibe.photo1, name1)
        val image2 = downloadImage(vibe.photo2, name2)

        println("QuoteRepository downloaded image1: ${image1?.absolutePath}")
        println("QuoteRepository downloaded image2: ${image2?.absolutePath}")

        val finalImage1 = when {
            image1 != null -> image1.absolutePath
            getExistingImage(name1) != null -> getExistingImage(name1)!!
            else -> vibe.photo1
        }

        val finalImage2 = when {
            image2 != null -> image2.absolutePath
            getExistingImage(name2) != null -> getExistingImage(name2)!!
            else -> vibe.photo2
        }

        println("QuoteRepository finalImage1: $finalImage1")
        println("QuoteRepository finalImage2: $finalImage2")

        val cachedQuote = vibe.copy(
            photo1 = finalImage1,
            photo2 = finalImage2
        )

        storage.saveString(cacheDateKey, today)
        storage.saveString(quoteDataKey, json.encodeToString(cachedQuote))

       cleanOldQuotes(uniqueId)

        return cachedQuote
    }

    private fun cleanOldQuotes(currentUniqueId: String) {
        try {
            val files = imagesDir.listFiles() ?: return
            files.forEach { file ->
                if (!file.name.contains(currentUniqueId)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            println("QuoteRepository cleanup error: ${e.message}")
        }
    }
    private suspend fun downloadImage(url: String, fileName: String): File? {
        if (url.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                println("QuoteRepository downloading: $url")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    println("QuoteRepository download failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    println("QuoteRepository download empty body")
                    return@withContext null
                }

                val file = File(imagesDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                println("QuoteRepository saved: ${file.absolutePath} (${bytes.size} bytes)")
                file
            } catch (e: Exception) {
                println("QuoteRepository download error: ${e.message}")
                null
            }
        }
    }

    private fun getExistingImage(fileName: String): String? {
        val file = File(imagesDir, fileName)
        return if (file.exists()) {
            println("QuoteRepository existing image found: ${file.absolutePath}")
            file.absolutePath
        } else null
    }

    private fun getFilenameFromUrl(url: String, prefix: String): String {
        val extension = url.substringAfterLast(".", "jpg").lowercase()
        val hash = try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
        return "${prefix}_$hash.$extension"
    }
}