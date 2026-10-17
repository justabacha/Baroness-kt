package com.baroness.app.data

sealed class EmojiSegment {
    data class Plain(val text: String) : EmojiSegment()
    data class Emoji(val resId: Int, val rawChars: String) : EmojiSegment()
}

object EmojiSegments {

    private val maxEmojiLen: Int by lazy {
        EmojiMap.map.keys.maxOfOrNull { it.length } ?: 1
    }

    /**
     * Splits [text] into a sequence of Plain and Emoji segments based on
     * the current EmojiMap.map. Unmatched characters are grouped into
     * contiguous Plain segments.
     */
    fun split(text: String): List<EmojiSegment> {
        if (text.isEmpty()) return emptyList()

        val emojiMap = EmojiMap.map
        val segments = mutableListOf<EmojiSegment>()
        val plainBuffer = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            var matched = false
            for (matchLen in maxEmojiLen downTo 1) {
                if (i + matchLen <= len) {
                    val substring = text.substring(i, i + matchLen)
                    val resId = emojiMap[substring]
                    if (resId != null) {
                        if (plainBuffer.isNotEmpty()) {
                            segments.add(EmojiSegment.Plain(plainBuffer.toString()))
                            plainBuffer.clear()
                        }
                        segments.add(EmojiSegment.Emoji(resId, substring))
                        i += matchLen
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                plainBuffer.append(text[i])
                i++
            }
        }

        if (plainBuffer.isNotEmpty()) {
            segments.add(EmojiSegment.Plain(plainBuffer.toString()))
        }

        return segments
    }
}
