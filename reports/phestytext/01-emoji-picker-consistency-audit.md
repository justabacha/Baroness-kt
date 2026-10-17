# Emoji.kt and Emoji Picker Data — Consistency Audit Report

## 1. Executive Summary

The emoji picker (`EmojiCategories.kt`) and `PhestyText` (`EmojiMap.kt`) exhibit an extremely high degree of consistency, sharing **83 exact matching emoji entries** with **zero duplicates** in either data source. Every emoji offered by the picker is fully present in `EmojiMap.map` (0 missing). The sole discrepancy is the red heart (`"❤️"`), which is present in `EmojiMap.map` but omitted from the picker's categories list. However, a critical bug was identified in `PhestyText`'s character-scanning loop: it checks substring lengths from **3 down to 1**, failing to match multi-character ZWJ sequences (such as `"❤️‍🔥"` and `"❤️‍🩹"`, which require matching lengths up to 5).

---

## 2. Located Emoji Data Files (`components/`, `data/`, `models/`)

- `app/src/main/java/com/baroness/app/components/Emoji.kt` — Renders a single emoji using `EmojiMap.map` (custom drawable) or falls back to system `Text`.
- `app/src/main/java/com/baroness/app/components/EmojiPicker.kt` — Composable dialog displaying categorized emoji grids.
- `app/src/main/java/com/baroness/app/data/EmojiCategories.kt` — Defines `emojiCategories` (list of `EmojiCategory` objects powering the emoji picker UI).
- `app/src/main/java/com/baroness/app/data/EmojiMap.kt` — Defines `EmojiMap.map` mapping Unicode emoji strings to `R.drawable.*` resource IDs.
- `app/src/main/java/com/baroness/app/models/EmojiCategory.kt` — Data class defining `EmojiCategory(name, emojis)`.

---

## 3. Primary Data File Contents (`EmojiCategories.kt`)

- **Data Structure**: `List<EmojiCategory>`, where each category contains `name: String` and `emojis: List<String>`.
- **Total Entries**: 83 unique entries across 7 categories (Expressions, People & Gestures, Hearts, Food & Drinks, Nature, Symbols & Vibes, Dark Corner).
- **Entry Characteristics**: Mix of single Unicode characters, variation-selector sequences, and multi-character ZWJ sequences.
- **Representative Entries**:
  - Expressions: `"😁"`, `"😅"`, `"🧐"`, `"🥹"`
  - Hearts: `"❤️‍🔥"`, `"❤️‍🩹"`, `"💛"`
  - Food & Drinks: `"🍎"`, `"🍕"`, `"☕"`
  - Dark Corner: `"💀"`, `"☠️"`, `"🗿"`

---

## 4. EmojiMap Contents (`EmojiMap.kt`)

- **Data Structure**: `Map<String, Int>` (`Map<String, DrawableResId>`).
- **Total Entries**: 84 entries.
- **Entry Shape**: `UnicodeEmojiString` → `R.drawable.<drawable_name>` (e.g., `"😁" to R.drawable.beaming`).
- **Key Types**: Single characters, variation-selector sequences, and multi-character ZWJ sequences.

---

## 5. Cross-Reference Findings

- **Total Picker Emojis**: 83
- **Total EmojiMap Keys**: 84
- **In Picker Not In EmojiMap**: 0 (`[]`)
- **In EmojiMap Not In Picker**: 1 (`['❤️']`)
- **Exact Matches**: 83

---

## 6. Emoji Picker UI Behavior (`EmojiPicker.kt`)

- **Presentation**: Rendered as a bottom-aligned floating Dialog with background blur (API 31+) and vertical gradient scrim.
- **Data Source**: Reads directly from `emojiCategories` in `EmojiCategories.kt`.
- **Insertion**: Clicking an emoji invokes `onEmojiSelected(emojiChar)`, which inserts the Unicode string into the active input field or message composer.
- **Visual Rendering**: Each picker cell calls `Emoji(emoji = emojiChar, size = 30.dp)`, which checks `EmojiMap.map[emoji]` and renders the custom drawable (or system text fallback if null).

---

## 7. Chat Flow Integration

1. **Selection**: User taps emoji in `EmojiPicker`, passing the Unicode string to `ChatInput`.
2. **Transmission**: The emoji string is sent over the network as-is.
3. **Persistence**: Stored in the Room database as plain text.
4. **Rendering**: Rendered in message bubbles (`MessageBubble.kt`) via `PhestyText`, which scans the text and swaps matched emoji keys for inline drawable images.
5. **Fallback**: If an emoji is missing from `EmojiMap.map`, `PhestyText` falls back to system text rendering.

---

## 8. Duplicate Detection

- **Picker Data File (`EmojiCategories.kt`)**: 0 duplicate entries.
- **`EmojiMap.map` (`EmojiMap.kt`)**: 0 duplicate keys.

---

## 9. Multi-Character Sequence Handling & Bug Discovery

- **Multi-Character Entries**:
  - `"❤️‍🔥"` (Heart on Fire: `❤️` + ZWJ + `🔥`)
  - `"❤️‍🩹"` (Mending Heart: `❤️` + ZWJ + `🩹`)
  - `"☠️"` (Skull and Crossbones + VS16)
  - `"❤️"` (Red Heart + VS16)
  - `"🎟️"` (Ticket + VS16)
  - `"☺️"` (Smiling Face + VS16)
  - `"😮‍💨"` (Face Exhaling: face + ZWJ + puff)
- **`PhestyText` Scan Bug**: In `PhestyText.kt`, the matching loop is hardcoded as `for (len in 3 downTo 1)`. Because multi-character ZWJ sequences like `"❤️‍🔥"` and `"❤️‍🩹"` exceed UTF-16 length 3 (they have length 5), `PhestyText` fails to match them, causing them to fall back to system rendering or partial replacement. `EmojiMap` correctly contains them, but `PhestyText`'s scanning window prevents their inline drawable conversion.

---

## 10. Open Questions for the Operator

1. **`PhestyText` Scan Limit**: Should `PhestyText.kt`'s substring scan length be increased from `3 downTo 1` to `5 downTo 1` (or dynamic based on key lengths) so multi-character ZWJ emojis like `"❤️‍🔥"` and `"❤️‍🩹"` can be successfully converted to custom drawables?
2. **Missing Red Heart**: Should `"❤️"` be added to `EmojiCategories.kt` under Hearts so users can select it from the picker alongside `"💛"`, `"💜"`, etc.?
