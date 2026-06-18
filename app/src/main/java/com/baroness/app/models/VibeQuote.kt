package com.baroness.app.models

import kotlinx.serialization.Serializable

@Serializable
data class VibeQuote(
    val part1: String,
    val part2: String,
    val photo1: String,
    val photo2: String
)