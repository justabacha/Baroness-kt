# Font Retirement and Quote Size Multiplier Implementation Record

## Context

Following font registration, 3 fonts (`Sakelar`, `Filx Girl`, `Gamaamli`) rendered with glyph sizes unsuitable for general app-wide typography.

This implementation phase:
1. Removed `Sakelar`, `Filx Girl`, and `Gamaamli` from the user-facing appearance font picker (`fontFamilies`), while retaining them in `AppFonts` and `FONT_REGISTRY` for quote markup rendering.
2. Reshaped `QuoteMarkup.kt` model output to `QuoteSpan` and added a `sizeScale: Float` property per font in `FONT_REGISTRY`.
3. Updated `QuoteCard.kt` to apply per-span font size scaling (`baseFontSize * span.sizeScale`) when rendering annotated quote text.

---

## 1. User Picker Retirement (`components/FontOptions.kt`)

Removed 3 entries from `fontFamilies`:
- `Sakelar` (`sakelar`)
- `Filx Girl` (`filx_girl`)
- `Gamaamli` (`gamaamli`)

**Confirmation**: All 3 fonts remain fully registered in `AppFonts` (`ui/theme/Type.kt`) and `FONT_REGISTRY` (`data/QuoteMarkup.kt`).

---

## 2. Model Reshaping & Scale Registry (`data/QuoteMarkup.kt`)

### 2.1 Models

```kotlin
data class QuoteSpan(
    val text: String,
    val fontFamily: FontFamily?,   // null = inherit default
    val sizeScale: Float = 1.0f     // 1.0f = normal
)

data class QuoteParagraph(
    val spans: List<QuoteSpan>,
    val textAlign: TextAlign?,
    val indentDp: Int?
)

data class QuoteFont(
    val family: FontFamily,
    val sizeScale: Float = 1.0f
)
```

### 2.2 `FONT_REGISTRY` Scale Table

```kotlin
    private val FONT_REGISTRY: Map<String, QuoteFont> = mapOf(
        // Existing 11 — default scale 1.0 except scaled ones
        "yuyu" to QuoteFont(AppFonts.Yuyu),
        "lifesavers" to QuoteFont(AppFonts.Lifesavers),
        "robotomono" to QuoteFont(AppFonts.RobotoMono),
        "gamaamli" to QuoteFont(AppFonts.Gamaamli),
        "matemasie" to QuoteFont(AppFonts.Matemasie),
        "dancing" to QuoteFont(AppFonts.DancingScript, 1.3f),
        "marck" to QuoteFont(AppFonts.MarckScript, 1.4f),
        "playfair" to QuoteFont(AppFonts.PlayfairDisplay),
        "kaushan" to QuoteFont(AppFonts.KaushanScript),
        "marker" to QuoteFont(AppFonts.PermanentMarker),
        "shadows" to QuoteFont(AppFonts.ShadowsIntoLight, 1.4f),

        // 24 new fonts — default scale 1.0 except scaled ones
        "xmas_bundle" to QuoteFont(AppFonts.ChristmasBundle),
        "xmas_dariona" to QuoteFont(AppFonts.ChristmasDariona),
        "cookie" to QuoteFont(AppFonts.CookieMonster),
        "crayon" to QuoteFont(AppFonts.CrayonHand),
        "evil" to QuoteFont(AppFonts.EvilScratch),
        "filx" to QuoteFont(AppFonts.FilxGirl, 1.5f),
        "god" to QuoteFont(AppFonts.GodOfWar),
        "squids" to QuoteFont(AppFonts.GameOfSquids),
        "graveyard" to QuoteFont(AppFonts.GraveyardCrimes),
        "birthday" to QuoteFont(AppFonts.HappyBirthday),
        "harukaze" to QuoteFont(AppFonts.Harukaze),
        "hello_xmas" to QuoteFont(AppFonts.HelloChristmas),
        "hoshiko" to QuoteFont(AppFonts.HoshikoSatsuki),
        "jealousy" to QuoteFont(AppFonts.Jealousy),
        "klomang" to QuoteFont(AppFonts.KlomangBrush),
        "skate" to QuoteFont(AppFonts.PowerSkate),
        "rain" to QuoteFont(AppFonts.RacingInTheRain),
        "sakelar" to QuoteFont(AppFonts.Sakelar, 1.7f),
        "shareit" to QuoteFont(AppFonts.Shareit, 1.3f),
        "asia" to QuoteFont(AppFonts.SpellOfAsia),
        "spookery" to QuoteFont(AppFonts.Spookery),
        "sprucken" to QuoteFont(AppFonts.Sprucken),
        "rules" to QuoteFont(AppFonts.TheRulesOfTheGame),
        "trash" to QuoteFont(AppFonts.TrashSecret)
    )
```

---

## 3. QuoteCard Rendering Logic (`components/QuoteCard.kt`)

**Before:**
```kotlin
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
```

**After:**
```kotlin
                paragraphs.forEach { paragraph ->
                    val paragraphStyle = paragraph.indentDp?.let {
                        TextStyle(textIndent = TextIndent(firstLine = it.sp))
                    } ?: TextStyle.Default

                    val annotated = remember(paragraph.spans, baseFontSize) {
                        buildAnnotatedString {
                            paragraph.spans.forEach { span ->
                                if (span.fontFamily == null && span.sizeScale == 1.0f) {
                                    append(span.text)
                                } else {
                                    withStyle(
                                        SpanStyle(
                                            fontFamily = span.fontFamily ?: activeFontFamily,
                                            fontSize = baseFontSize * span.sizeScale
                                        )
                                    ) {
                                        append(span.text)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
```

---

## 4. Scope & File Modification Confirmation

Modified files strictly limited to:
- `app/src/main/java/com/baroness/app/components/FontOptions.kt`
- `app/src/main/java/com/baroness/app/data/QuoteMarkup.kt`
- `app/src/main/java/com/baroness/app/components/QuoteCard.kt`

No physical font files, `Type.kt`, `SignatureLoops.kt`, or other screens were touched.

---

## 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 6. Manual Verification Checklist

- [ ] Force-stop. Cold start. Open Settings → Appearance → Font. Sakelar, Filx Girl, and Gamaamli no longer appear in the user picker (10 options remain).
- [ ] Test selecting remaining fonts in user picker — preview updates, selection pill moves, apply works.
- [ ] Add temporary test quote in `SignatureLoops.kt`: `<sakelar|center>big sakelar</>` on part1, unscaled text on part2.
- [ ] Rebuild and cold start. Confirm `<sakelar>` text renders at 1.7x base size.
- [ ] Test `<shadows>` (1.4x scale) — renders visibly larger than default.
- [ ] Test `<dancing>` (1.3x scale) — renders visibly larger than default.
- [ ] Test `<playfair>` (1.0x scale) — renders at standard base font size.
- [ ] Test mixed quote (`default <sakelar>sakelar</> default`). The middle span scales up while surrounding text retains base font size.
- [ ] Revert temporary test additions in `SignatureLoops.kt`.
