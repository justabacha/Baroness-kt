package com.baroness.app.models

data class Wish(
    val id: Long,
    val text: String,
    val date: String,
    val status: String,          // "planning" or "dusted"
    val creator: String,         // "P" or "B"
    val reactions: Map<String, String>, // key: "P" or "B", value: emoji
    val ratings: Map<String, Int>,      // key: "P" or "B", value: rating (1-5)
    val createdAt: Long
)

data class WishStats(
    val total: Int,
    val dusted: Int
)