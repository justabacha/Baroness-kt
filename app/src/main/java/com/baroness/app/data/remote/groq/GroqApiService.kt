package com.baroness.app.data.remote.groq

import android.util.Log
import com.baroness.app.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
        return performRequest(messages)
    }

    suspend fun translateText(text: String, targetLanguage: String = "English"): String? {
        val messages = listOf(
            GroqMessage(
                role = "system",
                content = "You are an expert translator for the Baroness app. Translate the provided text into $targetLanguage. " +
                        "Maintain the original tone, slang, and keep all emojis exactly as they are. " +
                        "Only return the translated text, nothing else."
            ),
            GroqMessage(
                role = "user",
                content = text
            )
        )
        return performRequest(messages)
    }

    suspend fun analyzeMessage(text: String, mode: String): String? {
        val systemPrompt = when (mode) {
            "Analyze Intent" -> "You are Friday, the Baroness Analyst. Analyze the intent of this message. What is the sender actually trying to achieve? Keep it concise and witty."
            "Detect Sarcasm" -> "You are Friday, the Baroness Analyst. Check this message for sarcasm or hidden subtext. Is the sender being serious? Keep it concise and witty."
            "Summarize Context" -> "You are Friday, the Baroness Analyst. Provide a brief summary of what this message means in a social context. Keep it concise and witty."
            "Suggest a Reply" -> "You are Friday, the Baroness Analyst. Suggest a high-vibe, witty reply to this message that matches the Baroness aesthetic. Keep it concise."
            else -> "You are Friday, the Baroness Analyst. Analyze this message and provide insights. Keep it concise and witty."
        }

        val messages = listOf(
            GroqMessage(role = "system", content = systemPrompt),
            GroqMessage(role = "user", content = text)
        )
        return performRequest(messages)
    }

    private suspend fun performRequest(messages: List<GroqMessage>): String? {
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
                        val errorBody = response.bodyAsText()
                        Log.e(TAG, "Key #${index + 1} FAILED with status ${response.status}: $errorBody")
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
