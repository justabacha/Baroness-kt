package com.baroness.app.voice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class VoiceCacheManager(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "voice_cache").apply { if (!exists()) mkdirs() }
    private val staticDir = File(cacheDir, "static_phrases").apply { if (!exists()) mkdirs() }
    private val tempDir = File(cacheDir, "temp_dynamic").apply { if (!exists()) mkdirs() }
    private val maxCacheSize = 30 * 1024 * 1024 // 30 MB

    init {
        clearTempCache()
        trimCacheIfNeeded()
        cleanupOldFiles()
    }

    private fun cleanupOldFiles() {
        try {
            val now = System.currentTimeMillis()
            val maxAge = 24 * 60 * 60 * 1000L // 24 hours
            staticDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAge) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isStaticContent(text: String): Boolean {
        val staticPatterns = listOf(
            Regex("^(good morning|good afternoon|good evening)", RegexOption.IGNORE_CASE),
            Regex("^(here's your update|on it|searching|checking)", RegexOption.IGNORE_CASE),
            Regex("^(stay in your zone|you're all set|got it)", RegexOption.IGNORE_CASE),
            Regex("^(hi|hello|yoow|quick update)", RegexOption.IGNORE_CASE)
        )
        
        val dynamicPatterns = listOf(
            Regex("\\d{1,2}:\\d{2}\\s*(AM|PM)", RegexOption.IGNORE_CASE), // Time
            Regex("\\d+\\s*degrees?", RegexOption.IGNORE_CASE), // Temperature
            Regex("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)", RegexOption.IGNORE_CASE), // Days
            Regex("(january|february|march|april|may|june|july|august|september|october|november|december)", RegexOption.IGNORE_CASE) // Months
        )
        
        if (dynamicPatterns.any { it.containsMatchIn(text) }) {
            return false
        }
        
        return staticPatterns.any { it.containsMatchIn(text) }
    }

    private fun getFileName(text: String, config: VoiceConfig): String {
        val rawKey = "${config.provider}_${config.voiceId}_${text.trim().lowercase()}"
        return md5(rawKey) + ".mp3"
    }

    fun get(text: String, config: VoiceConfig): File? {
        val fileName = getFileName(text, config)
        val file = File(staticDir, fileName)
        if (file.exists()) {
            // Touch file to update last modified for LRU
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return null
    }

    fun put(text: String, config: VoiceConfig, audioData: ByteArray): File? {
        return try {
            trimCacheIfNeeded()
            val fileName = getFileName(text, config)
            val file = File(staticDir, fileName)
            FileOutputStream(file).use { it.write(audioData) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createTempFile(audioData: ByteArray): File? {
        return try {
            val file = File(tempDir, "temp_${System.currentTimeMillis()}_${(1000..9999).random()}.mp3")
            FileOutputStream(file).use { it.write(audioData) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearTempCache() {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun trimCacheIfNeeded() {
        try {
            val files = staticDir.listFiles()?.toMutableList() ?: return
            var currentSize = files.sumOf { it.length() }
            
            if (currentSize > maxCacheSize) {
                // Sort by last modified ascending (oldest first)
                files.sortBy { it.lastModified() }
                for (file in files) {
                    val fileSize = file.length()
                    if (file.delete()) {
                        currentSize -= fileSize
                        if (currentSize <= maxCacheSize * 0.7) break // Trim down to 70% of max
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
