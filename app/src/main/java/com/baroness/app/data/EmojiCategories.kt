package com.baroness.app.data

import com.baroness.app.models.EmojiCategory

val emojiCategories = listOf(
    EmojiCategory(
        name = "Expressions",
        emojis = listOf(
            "😁", "😅", "😆", "😇", "🧐", "😢", "👀", "😕", "👻", "😉", "😋", "😎",
            "😏", "😘", "😤", "😫", "🤕", "🤗", "🤣", "🤧", "🥹", "🥴", "🫠", "🤭",
            "🫤", "🫡", "😮‍💨", "☺️"
        )
    ),
    EmojiCategory(
        name = "People & Gestures",
        emojis = listOf("👸", "🤴", "🤌", "🫴", "🫶", "🤙", "🤏", "🤞")
    ),
    EmojiCategory(
        name = "Hearts",
        emojis = listOf("❤️‍🔥", "❤️‍🩹", "💛", "💜", "💝", "🤍")
    ),
    EmojiCategory(
        name = "Food & Drinks",
        emojis = listOf("🍎", "🍊", "🍋", "🍒", "🌭", "🍔", "🍟", "🍕", "🌮", "🍿", "🥤", "☕", "🍹")
    ),
    EmojiCategory(
        name = "Nature",
        emojis = listOf("🌍", "🌚", "🌴", "🌷", "🌹", "🍂", "🍃", "🦦", "🫧")
    ),
    EmojiCategory(
        name = "Symbols & Vibes",
        emojis = listOf("🔥", "💰", "💸", "💭", "💬", "📌", "🚩", "🔖", "📍", "📑", "🚨", "🎧", "🎟️", "🚿", "🚮", "🚼")
    ),
    EmojiCategory(
        name = "Dark Corner",
        emojis = listOf("💀", "☠️", "🗿")
    )
)