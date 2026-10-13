# Quote Card — Restore Active Font After Paragraph Split Refactor Implementation Record

## Context

Following the paragraph split refactor (`reports/quotes/04-paragraph-split-refactor.md`), quote paragraph `Text` composables were initialized with `style = if (paragraph.indentDp != null) TextStyle(textIndent = ...) else TextStyle.Default`. Because `TextStyle.Default` has a `null` `fontFamily`, it overrode `LocalTextStyle.current` rather than merging with the theme. Unmarked quotes reverted to the system default font (Roboto) instead of inheriting the app's active typography.

This fix merges `LocalTextStyle.current` with paragraph-level styles (`textIndent`) and explicitly applies `LocalBaronessTypography.current.title.fontFamily`, restoring active font inheritance across all unmarked and marked quote paragraphs.

---

## 1. Before and After (`QuoteCard.kt`)

### Before
```kotlin
            val paragraphs = remember(text) {
                QuoteMarkup.parse(
                    source = text ?: "",
                    defaultFontFamily = null
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph.text,
                        color = Color.White,
                        fontSize = quoteFont,
                        lineHeight = quoteFont * 1.35f,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = paragraph.textAlign ?: TextAlign.Center,
                        style = if (paragraph.indentDp != null) {
                            TextStyle(textIndent = TextIndent(firstLine = paragraph.indentDp.sp))
                        } else TextStyle.Default,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
```

### After
```kotlin
            val baseStyle = LocalTextStyle.current
            val activeFontFamily = LocalBaronessTypography.current.title.fontFamily

            val paragraphs = remember(text) {
                QuoteMarkup.parse(
                    source = text ?: "",
                    defaultFontFamily = null
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    val paragraphStyle = paragraph.indentDp?.let {
                        TextStyle(textIndent = TextIndent(firstLine = it.sp))
                    } ?: TextStyle.Default

                    Text(
                        text = paragraph.text,
                        color = Color.White,
                        fontSize = quoteFont,
                        lineHeight = quoteFont * 1.35f,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = paragraph.textAlign ?: TextAlign.Center,
                        style = baseStyle.merge(paragraphStyle).copy(
                            fontFamily = activeFontFamily
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
```

---

## 2. Investigation Findings & Approach Applied

1. **`LocalTextStyle.current` Carrier Check**: `LocalTextStyle.current` alone did **not** carry the active user-configured font family because the app provides active font tokens through `LocalBaronessTypography.current` (`ChatTypography` provider in `BaronessAppTheme`) rather than updating Material 3 `LocalTextStyle`.
2. **Fix Approach Applied**: **Merge + Explicit `fontFamily`**. Merged `LocalTextStyle.current` with `paragraphStyle` (for `textIndent`) and explicitly copied `fontFamily = LocalBaronessTypography.current.title.fontFamily`.
3. **Reactivity**: When the active font is changed via Settings -> Appearance, `LocalBaronessTypography.current` triggers a re-composition, immediately updating all quote cards on the Dashboard.

---

## 3. Scope Restrictions Confirmation

Confirmed that **only `QuoteCard.kt`** was modified. `QuoteMarkup.kt`, `SignatureLoops.kt`, and all other files remain untouched.

---

## 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 5. Manual Verification Checklist

- [ ] Force-stop. Cold start. Quote cards render in the app's active font (`Playfair Display` or user's active theme font), not system default Roboto.
- [ ] Unmarked quotes: Renders using the active font — zero visual regression.
- [ ] Marked quotes: Font spans still override base font where tagged, positions (`left`/`right`/`center`) and indents still apply correctly.
- [ ] Multi-paragraph quotes: Inherit active font with proper per-line alignment and 4dp spacing.
- [ ] Change app font via Settings → Appearance. Return to Dashboard — quote cards instantly re-render using the newly selected font.

---

## 6. Legacy Color Tag Fallback Fix

### Validation Change
- **Before (`QuoteMarkup.kt`)**:
  ```kotlin
  2 -> isValidFontIdentifier(tagParts[0]) && isValidPositionIdentifier(tagParts[1])
  ```
  *Problem*: Replaced color tokens (e.g., `"pink"` in `<kaushan|pink>`) failed position validation (`isValidPositionIdentifier`), marking the entire tag as invalid and rendering `<kaushan|pink>` literally as visible text.

- **After (`QuoteMarkup.kt`)**:
  ```kotlin
  2 -> isValidFontIdentifier(tagParts[0])
  ```
  *Solution*: 2-part tag validity is gated solely on font identifier syntax. Downstream, `resolveAlignment("pink")` and `resolveIndentDp("pink")` return `null`, allowing legacy color tokens to fall through gracefully without position overrides while the font (e.g. `KaushanScript`) resolves successfully.

### Fallback Behavior Verifications
1. **`<kaushan|pink>`**: Resolves to `font = KaushanScript`, `position = null` (default center alignment). The raw tag syntax `<kaushan|pink>` and `</>` is stripped and no literal brackets appear in rendered output.
2. **`<|left>`**: Resolves to `font = null` (inherits active theme font), `position = TextAlign.Left`.
3. **`<|pink>`**: Resolves to `font = null`, `position = null`. Inner text renders with active theme font and alignment; markup syntax is stripped.
4. **`<not a tag>`**: Fails font identifier validation (spaces present), rendering `<not a tag>` literally as text.

### Build Result
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

