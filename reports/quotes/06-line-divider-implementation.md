# Quote Engine — Line Divider Implementation Record

## Context

Previous quote markup rendered horizontal dividers using Unicode box-drawing characters (e.g. `<squids>────</>`). Because Unicode characters inherit line-height and font metrics, box-drawing characters produced uneven lines and excessive vertical spacing inside quote cards.

This implementation adds native `{line}` divider support to `QuoteMarkup.kt` and `QuoteCard.kt`. When a raw quote paragraph line equals `{line}`, the parser flags `isLine = true`, and `QuoteCard` renders a Compose `HorizontalDivider` with 40% content width, centered layout, and 8dp vertical padding.

---

## 1. `QuoteParagraph` Model Update (`data/QuoteMarkup.kt`)

Added `isLine: Boolean = false` to `QuoteParagraph`:

```kotlin
data class QuoteParagraph(
    val spans: List<QuoteSpan>,
    val textAlign: TextAlign?,
    val indentDp: Int?,
    val isLine: Boolean = false
)
```

---

## 2. Parser Detection (`data/QuoteMarkup.kt`)

In `QuoteMarkup.parse()`, added exact line matching for `{line}`:

```kotlin
        for (line in rawLines) {
            if (line.isEmpty()) continue

            if (line.trim() == "{line}") {
                paragraphs.add(
                    QuoteParagraph(
                        spans = emptyList(),
                        textAlign = null,
                        indentDp = null,
                        isLine = true
                    )
                )
                continue
            }

            var firstAlign: TextAlign? = null
            var firstIndent: Int? = null
            val spans = mutableListOf<QuoteSpan>()
            ...
```

---

## 3. Renderer Divider Emission (`components/QuoteCard.kt`)

In `QuoteCard.kt`, updated the paragraph `Column` loop:

```kotlin
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    if (paragraph.isLine) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
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
                }
            }
```

---

## 4. Scope & Preservation Confirmation

- **Modified Files**: Strictly limited to `app/src/main/java/com/baroness/app/data/QuoteMarkup.kt` and `app/src/main/java/com/baroness/app/components/QuoteCard.kt`.
- **Preserved Code**: `SignatureLoops.kt`, font registries, scale multipliers, and theme files remain untouched.

---

## 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 6. Manual Verification Checklist

- [ ] Force-stop. Cold start.
- [ ] Test temporary quote in `SignatureLoops.kt`:
      - `part1`: `"<marker>Me</>\n<marker>+ U</>\n{line}\n❤️"`
      - `part2`: `"<marker>Me</>\n<marker>- U</>\n{line}\n<marker>☹</>"`
- [ ] Cold start. Card 1 renders "Me" and "+ U", followed by a centered, perfectly straight 1dp horizontal line (40% width, 60% opacity white), followed by "❤️".
- [ ] Card 2 renders "Me" and "- U", followed by the centered 1dp divider line, followed by "☹".
- [ ] Line spacing is tight (8dp padding) and independent of font metrics.
- [ ] Revert temporary test quote in `SignatureLoops.kt`.
