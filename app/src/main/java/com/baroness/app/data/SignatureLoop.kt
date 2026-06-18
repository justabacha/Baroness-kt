package com.baroness.app.data

import com.baroness.app.models.VibeQuote

object SignatureLoops {
    private const val BASE_URL = "https://baroness-test.vercel.app"

    val quotes = listOf(
        VibeQuote(
            part1 = "Some people just make the world easier.", //Baroness Quote
            part2 = "You're exactly where you need to be.", //My Quote
            photo1 = "$BASE_URL/bucket/Image-1.jpg",
            photo2 = "$BASE_URL/bucket/Image-2.JPG"
        ),
        VibeQuote(
            part1 = "I don't really do 'ordinary'.",
            part2 = "We're on a completely different wave.",
            photo1 = "$BASE_URL/bucket/Image-3.jpg",
            photo2 = "$BASE_URL/bucket/Image-4.JPG"
        ),
        VibeQuote(
            part1 = "Everything feels a lot quieter now.",
            part2 = "I finally found a place that feels like home.",
            photo1 = "$BASE_URL/bucket/Image-5.JPG",
            photo2 = "$BASE_URL/bucket/Image-6.jpg"
        )

    )
}