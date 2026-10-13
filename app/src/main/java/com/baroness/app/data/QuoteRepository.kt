package com.baroness.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.baroness.app.models.VibeQuote
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.VibeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    companion object {
        private const val TAG = "QuoteRepository"

        @Volatile
        private var INSTANCE: QuoteRepository? = null

        fun getInstance(context: Context): QuoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuoteRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _cachedQuoteSnapshot = MutableStateFlow<VibeQuote?>(null)
    val cachedQuoteSnapshot: StateFlow<VibeQuote?> = _cachedQuoteSnapshot.asStateFlow()

    private val _cachedImage1 = MutableStateFlow<Bitmap?>(null)
    val cachedImage1: StateFlow<Bitmap?> = _cachedImage1.asStateFlow()

    private val _cachedImage2 = MutableStateFlow<Bitmap?>(null)
    val cachedImage2: StateFlow<Bitmap?> = _cachedImage2.asStateFlow()

    fun getCachedQuoteSnapshot(): VibeQuote? = _cachedQuoteSnapshot.value
    fun getCachedImage1(): Bitmap? = _cachedImage1.value
    fun getCachedImage2(): Bitmap? = _cachedImage2.value

    fun warmLocalCache() {
        try {
            val today = dateFormat.format(Date())
            val cachedDate = StorageManager.getStringSync(cacheDateKey)
            if (cachedDate == today) {
                val cachedJson = StorageManager.getStringSync(quoteDataKey)
                cachedJson?.let {
                    val cachedQuote = json.decodeFromString<VibeQuote>(it)
                    val currentVibe = VibeManager.getVibeOfTheDay()
                    if (cachedQuote.part1 == currentVibe.part1 && cachedQuote.part2 == currentVibe.part2) {
                        val file1 = File(cachedQuote.photo1)
                        val file2 = File(cachedQuote.photo2)
                        val b1 = decodeImageFile(file1)
                        val b2 = decodeImageFile(file2)
                        if (b1 != null && b2 != null) {
                            _cachedImage1.value = b1
                            _cachedImage2.value = b2
                            _cachedQuoteSnapshot.value = cachedQuote
                            Log.d(TAG, "Pre-decoded quote images: photo1=${b1.width}x${b1.height}, photo2=${b2.width}x${b2.height}")
                            return
                        }
                    }
                }
            }
            _cachedImage1.value = null
            _cachedImage2.value = null
            _cachedQuoteSnapshot.value = null
        } catch (e: Exception) {
            _cachedImage1.value = null
            _cachedImage2.value = null
            _cachedQuoteSnapshot.value = null
            Log.e(TAG, "warmLocalCache error: ${e.message}", e)
        }
    }

    private fun decodeImageFile(file: File, targetWidthPx: Int = 800, targetHeightPx: Int = 1000): Bitmap? {
        return try {
            if (!file.exists() || file.length() == 0L) return null
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            while (width / (sampleSize * 2) >= targetWidthPx && height / (sampleSize * 2) >= targetHeightPx) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode quote image file: ${file.name}", e)
            null
        }
    }

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
                        val file1 = File(cachedQuote.photo1)
                        val file2 = File(cachedQuote.photo2)

                        if (file1.exists() && file2.exists()) {
                            if (_cachedImage1.value == null) _cachedImage1.value = decodeImageFile(file1)
                            if (_cachedImage2.value == null) _cachedImage2.value = decodeImageFile(file2)
                            _cachedQuoteSnapshot.value = cachedQuote
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
                    val file1 = File(quote.photo1)
                    val file2 = File(quote.photo2)

                    if (file1.exists() || file2.exists()) {
                        if (file1.exists() && _cachedImage1.value == null) _cachedImage1.value = decodeImageFile(file1)
                        if (file2.exists() && _cachedImage2.value == null) _cachedImage2.value = decodeImageFile(file2)
                        _cachedQuoteSnapshot.value = quote
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

        val f1 = File(finalImage1)
        val f2 = File(finalImage2)
        if (f1.exists()) _cachedImage1.value = decodeImageFile(f1)
        if (f2.exists()) _cachedImage2.value = decodeImageFile(f2)
        _cachedQuoteSnapshot.value = cachedQuote

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
