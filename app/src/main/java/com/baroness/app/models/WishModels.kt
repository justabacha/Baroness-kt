package com.baroness.app.models

data class Wish(
    val id: Long,
    val text: String,
    val date: String,
    val status: String,
    val creator: String,
    val ratings: Map<String, Int> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

data class WishStats(
    val total: Int = 0,
    val dusted: Int = 0
)