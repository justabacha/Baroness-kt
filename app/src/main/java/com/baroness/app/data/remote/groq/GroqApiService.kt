package com.baroness.app.data.remote.groq

import android.util.Log
import com.baroness.app.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

private const val TAG = "GroqApiService"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

class GroqApiService {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            })
        }
    }

    private val apiKeys = listOf(
        BuildConfig.GROQ_API_KEY,
        BuildConfig.GROQ_API_KEY_1
    ).filter { it.isNotBlank() }

    suspend fun getChatCompletion(messages: List<GroqMessage>): String? {
        if (apiKeys.isEmpty()) {
            Log.e(TAG, "No Groq API Keys found in BuildConfig!")
            return null
        }

        for ((index, apiKey) in apiKeys.withIndex()) {
            Log.d(TAG, "Attempting request with Key #${index + 1} (length: ${apiKey.length})")
            
            try {
                val response = client.post(GROQ_URL) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(GroqChatRequest(messages = messages))
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val chatResponse: GroqChatResponse = response.body()
                        Log.d(TAG, "Key #${index + 1} SUCCESS")
                        return chatResponse.choices.firstOrNull()?.message?.content
                    }
                    HttpStatusCode.TooManyRequests -> {
                        Log.w(TAG, "Key #${index + 1} RATE LIMITED (429). Trying next key...")
                        continue 
                    }
                    else -> {
                        val errorBody = response.body<String>()
                        Log.e(TAG, "Key #${index + 1} FAILED with status ${response.status}: $errorBody")
                        // If it's a 401 or something else, we still try the next key just in case
                        continue
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key #${index + 1} CRASHED: ${e.message}", e)
                continue
            }
        }

        Log.e(TAG, "ALL keys failed to provide a completion.")
        return null
    }
}
