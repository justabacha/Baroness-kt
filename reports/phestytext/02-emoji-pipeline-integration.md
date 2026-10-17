# Emoji Pipeline Integration Record

## Context

Previous audits identified three emoji system limitations:
1. `PhestyText.kt` scanned substring lengths from `3 downTo 1`, failing to match multi-character ZWJ sequence emojis (lengths 4–5) like `"❤️‍🔥"` and `"❤️‍🩹"`.
2. The red heart (`"❤️"`) was defined in `EmojiMap.map` but missing from `EmojiCategories.kt`, preventing selection in `EmojiPicker`.
3. `QuoteCard` did not utilize the custom emoji drawable pipeline for quote text.

This implementation completed 5 stages:
1. Dynamic max length scanning in `PhestyText.kt`.
2. Added `"❤️"` to `EmojiCategories.kt`.
3. Extracted pure-Kotlin `EmojiSegments.kt` parser helper.
4. Refactored `PhestyText.kt` to consume `EmojiSegments.split()`.
5. Integrated custom emoji rendering into `QuoteCard.kt` with per-span scaling support.

---

## Stage 1 — ZWJ Scan Fix (`components/PhestyText.kt`)

**Before:**
```kotlin
            // Try matching multi-character emojis first (length 3 down to 1)
            for (len in 3 downTo 1) {
                if (currentIndex + len <= chars.size) {
                    val substring = text.substring(currentIndex, currentIndex + len)
                    if (emojiMap.containsKey(substring)) {
```

**After:**
```kotlin
private val maxEmojiLen: Int by lazy {
    EmojiMap.map.keys.maxOfOrNull { it.length } ?: 1
}

...
            // Try matching multi-character emojis first (dynamic maxEmojiLen down to 1)
            for (len in maxEmojiLen downTo 1) {
                if (currentIndex + len <= text.length) {
                    val substring = text.substring(currentIndex, currentIndex + len)
                    if (emojiMap.containsKey(substring)) {
```

- **Dynamic Derivation**: `maxEmojiLen` dynamically checks `EmojiMap.map.keys.maxOfOrNull { it.length }`, successfully capturing length-4 and length-5 ZWJ sequences.
- **Stage 1 Build**: **SUCCESSFUL**

---

## Stage 2 — Emoji Picker Red Heart Addition (`data/EmojiCategories.kt`)

Added `"❤️"` as the first element of the `"Hearts"` category in `EmojiCategories.kt`:

```kotlin
    EmojiCategory(
        name = "Hearts",
        emojis = listOf("❤️", "❤️‍🔥", "❤️‍🩹", "💛", "💜", "💝", "🤍")
    )
```

- **Stage 2 Build**: **SUCCESSFUL**

---

## Stage 3 — Extracted Emoji Parser Helper (`data/EmojiSegments.kt`)

Created pure Kotlin parser without Compose dependencies:

```kotlin
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
```

- **Stage 3 Build**: **SUCCESSFUL**

---

## Stage 4 — `PhestyText` Refactor (`components/PhestyText.kt`)

**Before:**
Inline character loop scanning `chars.toCharArray()` and appending inline content manually.

**After:**
```kotlin
    val density = LocalDensity.current
    val segments = EmojiSegments.split(text)
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    val annotatedString = buildAnnotatedString {
        segments.forEach { segment ->
            when (segment) {
                is EmojiSegment.Plain -> append(segment.text)
                is EmojiSegment.Emoji -> {
                    val key = "emoji_${segment.resId}_${segment.rawChars.hashCode()}"
                    appendInlineContent(id = key, alternateText = segment.rawChars)

                    if (!inlineContent.containsKey(key)) {
                        val sizeInDp = with(density) { (fontSize * 1.2f).toDp() }
                        inlineContent[key] = InlineTextContent(
                            placeholder = Placeholder(
                                width = fontSize * 1.2f,
                                height = fontSize * 1.2f,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Image(
                                painter = painterResource(id = segment.resId),
                                contentDescription = segment.rawChars,
                                modifier = Modifier.size(sizeInDp)
                            )
                        }
                    }
                }
            }
        }
    }
```

- **Stage 4 Build**: **SUCCESSFUL**

---

## Stage 5 — `QuoteCard` Emoji Integration (`components/QuoteCard.kt`)

Integrated `EmojiSegments.split()` inside `QuoteCard`'s paragraph span loop:

```kotlin
                        val inlineContentMap = remember(paragraph.spans, baseFontSize) {
                            mutableMapOf<String, InlineTextContent>()
                        }

                        val annotated = remember(paragraph.spans, baseFontSize) {
                            buildAnnotatedString {
                                paragraph.spans.forEach { span ->
                                    if (span.text.isEmpty()) return@forEach
                                    val segments = EmojiSegments.split(span.text)
                                    segments.forEach { segment ->
                                        when (segment) {
                                            is EmojiSegment.Plain -> {
                                                if (span.fontFamily == null && span.sizeScale == 1.0f) {
                                                    append(segment.text)
                                                } else {
                                                    withStyle(
                                                        SpanStyle(
                                                            fontFamily = span.fontFamily ?: activeFontFamily,
                                                            fontSize = baseFontSize * span.sizeScale
                                                        )
                                                    ) {
                                                        append(segment.text)
                                                    }
                                                }
                                            }
                                            is EmojiSegment.Emoji -> {
                                                val emojiSizeSp = baseFontSize.value * span.sizeScale * 1.2f
                                                val key = "emoji_${segment.resId}_${emojiSizeSp.toInt()}"
                                                appendInlineContent(id = key, alternateText = segment.rawChars)

                                                if (!inlineContentMap.containsKey(key)) {
                                                    val sizeDp = with(density) { emojiSizeSp.sp.toDp() }
                                                    inlineContentMap[key] = InlineTextContent(
                                                        placeholder = Placeholder(
                                                            width = emojiSizeSp.sp,
                                                            height = emojiSizeSp.sp,
                                                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                                        )
                                                    ) {
                                                        Image(
                                                            painter = painterResource(id = segment.resId),
                                                            contentDescription = segment.rawChars,
                                                            modifier = Modifier.size(sizeDp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = annotated,
                            color = Color.White,
                            fontSize = baseFontSize,
                            lineHeight = baseFontSize * 1.35f,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            textAlign = paragraph.textAlign ?: TextAlign.Center,
                            style = baseStyle.merge(paragraphStyle).copy(
                                fontFamily = activeFontFamily
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            inlineContent = inlineContentMap
                        )
```

- **Stage 5 Build**: **SUCCESSFUL**

---

## Scope & File Modification Confirmation

Modified files strictly limited to:
- `app/src/main/java/com/baroness/app/components/PhestyText.kt`
- `app/src/main/java/com/baroness/app/data/EmojiCategories.kt`
- `app/src/main/java/com/baroness/app/data/EmojiSegments.kt` (New)
- `app/src/main/java/com/baroness/app/components/QuoteCard.kt`

No other files (e.g. `EmojiMap.kt`, `SignatureLoop.kt`) were modified.

---

## Manual Verification Checklist

- [ ] Send `"❤️‍🔥"` in chat — renders custom drawable, not broken glyph.
- [ ] Send `"❤️‍🩹"` and `"😮‍💨"` in chat — render custom drawables.
- [ ] Open EmojiPicker — confirm red heart `"❤️"` appears first in Hearts category.
- [ ] Test quote with emojis in `SignatureLoop.kt`: `part1 = "test <playfair>❤️‍🔥</>"`, `part2 = "test <sakelar>❤️‍🔥</>"`.
- [ ] Cold start Dashboard — Card 1 renders heart-on-fire as drawable at base size; Card 2 renders heart-on-fire as drawable scaled at 1.2x.
- [ ] Revert test quotes in `SignatureLoop.kt`.
