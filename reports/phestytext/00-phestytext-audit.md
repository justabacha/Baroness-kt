# PhestyText — Quote Card Integration Audit Report

## 1. Executive Summary

`PhestyText` is a custom Compose text wrapper designed to intercept specific Unicode emoji strings and replace them with inline drawable image resources (`R.drawable.*`) using Compose's `InlineTextContent`. While `PhestyText` successfully replaces supported emojis with custom app-themed icons in chat and wishlist components, it accepts only `String` input and does not support span-level `AnnotatedString` stylings, font family overrides, or size scaling. Therefore, the operator's assumption that `PhestyText` can be dropped into `QuoteCard` to handle emojis is **not feasible** without completely rewriting `PhestyText` to support `AnnotatedString` and quote markup rendering.

---

## 2. Declaration and Signature (`components/PhestyText.kt`)

- **Type**: Composable function (`@Composable fun PhestyText(...)`).
- **Signature & Parameters**:
  ```kotlin
  @Composable
  fun PhestyText(
      text: String,
      modifier: Modifier = Modifier,
      style: TextStyle = TextStyle.Default,
      color: Color = Color.Unspecified,
      fontSize: TextUnit = 16.sp,
      fontFamily: FontFamily? = null,
      textAlign: TextAlign? = null,
      maxLines: Int = Int.MAX_VALUE,
      overflow: TextOverflow = TextOverflow.Clip
  )
  ```
- **Internal Implementation**: Wraps standard Material 3 `Text`. It scans `text` character-by-character (matching multi-character emojis from `EmojiMap.map` of length 3 down to 1), builds an `AnnotatedString` using `appendInlineContent`, and constructs an `inlineContent` map mapping each emoji to an `Image` composable (backed by local `R.drawable.*` resources) sized at `fontSize * 1.2f`.
- **Input Type**: Accepts `String` only (`text: String`). Does not accept `AnnotatedString`.
- **Styling Overrides**: Accepts `style`, `color`, `fontSize`, `fontFamily`, and `textAlign`.

---

## 3. Emoji Handling Findings (`data/EmojiMap.kt`)

- **Custom Emoji Mapping**: `PhestyText` references `EmojiMap.map`, which maps ~100 Unicode emoji strings (expressions, hearts, gestures, food, nature, symbols, dark corner) to local drawable resource IDs in `R.drawable.*`.
- **Implementation Mechanism**: Replaces matched emoji unicode characters with inline placeholder spans (`PlaceholderVerticalAlign.TextCenter`) rendering local vector/raster drawables via `Image`.
- **No EmojiCompat**: Does not utilize Android's `EmojiCompat` library.
- **No Network Assets**: All emoji drawables are bundled locally in the APK resources (`res/drawable/`).

---

## 4. Call-Site Inventory (`PhestyText` Usage)

- **Chat Feature**:
  - `ChatEntry.kt` (lines 68, 88, 105) — chat previews and message strings.
  - `MessageBubble.kt` (line 214, 426) — chat message text.
  - `AskFridaySheet.kt` (line 212) — AI prompts and answers.
  - `ChatInput.kt` (line 220) — input field display.
  - `MessageInfoSheet.kt` (line 214) — message details.
  - `PinnedMessagesPill.kt` (line 137) — pinned snippet text.
  - `TranslationSheet.kt` (line 191) — translated text display.
- **Wishlist & Media Feature**:
  - `WishItem.kt` (lines 146, 192) — wish title and description.
  - `SongQueueSheet.kt` (lines 64, 73, 138, 148) — queue track titles and counts.
  - `InAppMiniPlayer.kt` (lines 149, 160, 207, 212) — now-playing song title and artist.

All call sites pass user-generated or server-synced `String` text.

---

## 5. Quote Card Rendering Today (`components/QuoteCard.kt`)

- **Current Renderer**: Uses standard Material 3 `Text` composable.
- **Input Binding**: Consumes `AnnotatedString` produced by `QuoteMarkup.parse()`, where each paragraph holds a list of `QuoteSpan` objects (`text`, `fontFamily`, `sizeScale`).
- **Emoji Handling**: Emojis in quote text currently render via Android's system emoji font (NotoColorEmoji) through standard `Text`.

---

## 6. Emoji Style Comparison

- **`PhestyText` Emojis**: Rendered as custom app drawables (`Image` via inline content, styled to match app aesthetic).
- **Standard `Text` Emojis**: Rendered as standard system font glyphs (system emoji presentation).

---

## 7. AnnotatedString Compatibility

- **Input Mismatch**: `PhestyText` accepts `String` only, whereas `QuoteCard` requires `AnnotatedString` with span-level `SpanStyle` overrides (`fontFamily`, `fontSize` scaling per span).
- **Feature Loss**: Adopting `PhestyText` in `QuoteCard` as-is would completely destroy quote markup features: multiple font families within a single quote, per-span size scaling (`sizeScale`), paragraph alignment, indentation, and `{line}` horizontal dividers.

---

## 8. Emojis Present in Quotes (`data/SignatureLoop.kt`)

- Quotes contain emojis such as ❤️, 🤒, 🙂, 🥂, etc.
- Emojis appear inline with text or on dedicated lines.
- No complex multi-person ZWJ or modifier sequences are heavily used, but standard unicode emojis are present.

---

## 9. Fallback Behavior

- **`PhestyText`**: If an emoji is absent from `EmojiMap.map`, `PhestyText` appends the raw unicode characters normally, falling back to system text rendering.
- **`QuoteCard` (`Text`)**: Relies entirely on Android system text and emoji font rendering.

---

## 10. Performance Considerations

- **Parsing Cost**: `PhestyText` performs a substring loop checking `EmojiMap.map` (lengths 3 down to 1) for every character position during string iteration on every composition pass.
- **Dashboard Impact**: Rendering multiple `PhestyText` components inside cached/scrolling `QuoteCard` instances on the Dashboard could introduce unnecessary CPU overhead during rapid composition/scrolling.

---

## 11. Open Questions for the Operator

1. **Markup Preservation**: Since `PhestyText` only accepts plain `String` and lacks span styling, should `PhestyText` be refactored to accept `AnnotatedString` and support inline image replacement alongside span styles, or should custom emoji replacement be integrated directly into `QuoteMarkup` / `QuoteCard`?
2. **Performance Scope**: Given that `PhestyText` scans strings character-by-character on every composition, is inline emoji drawing desired for high-frequency dashboard quote cards?
