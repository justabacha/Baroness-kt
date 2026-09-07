package com.baroness.app.data.remote.groq

import kotlinx.serialization.Serializable

@Serializable
data class GroqChatRequest(
    val model: String = "openai/gpt-oss-120b",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.6, // Slightly lower for more stability
    val max_tokens: Int = 1024
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqChatResponse(
    val id: String,
    val choices: List<GroqChoice>
)

@Serializable
data class GroqChoice(
    val message: GroqMessage,
    val finish_reason: String
)
