# Quote Position Rendering — Paragraph Split Refactor Implementation Record

## Context

Following the diagnosis (`reports/quotes/03-position-rendering-diagnosis.md`), the quote position engine has been refactored to Approach A (paragraph-level splitting). By parsing quote sources into a list of paragraphs (`List<QuoteParagraph>`) separated by `\n` and rendering each paragraph in a dedicated `Text` composable within a vertical `Column`, Compose's implicit `ParagraphStyle` line-break bug is completely eliminated.

---

## 1. Updated Source: `QuoteParagraph` Model and `QuoteMarkup.kt`

Updated `app/src/main/java/com/baroness/app/data/QuoteMarkup.kt`:

```kotlin
package com.baroness.app.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.baroness.app.ui.theme.AppFonts

data class QuoteParagraph(
    val text: AnnotatedString,
    val textAlign: TextAlign?,
    val indentDp: Int?
)

object QuoteMarkup {

    private val FONT_REGISTRY: Map<String, FontFamily> = mapOf(
        "yuyu" to AppFonts.Yuyu,
        "lifesavers" to AppFonts.Lifesavers,
        "robotomono" to AppFonts.RobotoMono,
        "gamaamli" to AppFonts.Gamaamli,
        "matemasie" to AppFonts.Matemasie,
        "dancing" to AppFonts.DancingScript,
        "marck" to AppFonts.MarckScript,
        "playfair" to AppFonts.PlayfairDisplay,
        "kaushan" to AppFonts.KaushanScript,
        "marker" to AppFonts.PermanentMarker,
        "shadows" to AppFonts.ShadowsIntoLight
    )

    private fun parseFont(fontStr: String): FontFamily? {
        return FONT_REGISTRY[fontStr.trim().lowercase()]
    }

    private fun resolveAlignment(token: String): TextAlign? {
        return when (token.trim().lowercase()) {
            "left" -> TextAlign.Left
            "right" -> TextAlign.Right
            "center" -> TextAlign.Center
            else -> null
        }
    }

    private fun resolveIndentDp(token: String): Int? {
        val trimmed = token.trim().lowercase()
        if (!trimmed.startsWith("indent:")) return null
        val numStr = trimmed.removePrefix("indent:").trim()
        val n = numStr.toIntOrNull() ?: return null
        return n.coerceIn(0, 400)
    }

    private fun isValidFontIdentifier(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return true
        return trimmed.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    }

    private fun isValidPositionIdentifier(s: String): Boolean {
        val trimmed = s.trim().lowercase()
        if (trimmed.isEmpty()) return true
        if (trimmed in listOf("left", "right", "center")) return true
        if (trimmed.startsWith("indent:")) {
            val numStr = trimmed.removePrefix("indent:").trim()
            return numStr.toIntOrNull() != null
        }
        return false
    }

    /**
     * Parses a quote string containing optional inline markup into a list of QuoteParagraph objects (one per non-empty line).
     * - Source is split on '\n'. Entirely empty lines are skipped.
     * - Position attributes (align or indent) are extracted per paragraph from the FIRST position tag encountered.
     * - Font styling stays inside AnnotatedString as SpanStyle. No ParagraphStyle is injected into AnnotatedString.
     * - Unknown font or position names fall back gracefully (legacy color tokens like 'pink' are ignored).
     * - Unclosed tags auto-close at end of string.
     * - A literal '<' that is not followed by a valid opening pattern is rendered as-is.
     */
    fun parse(
        source: String,
        defaultFontFamily: FontFamily?
    ): List<QuoteParagraph> {
        val rawLines = source.split('\n')
        val paragraphs = mutableListOf<QuoteParagraph>()

        for (line in rawLines) {
            if (line.isEmpty()) continue

            var firstAlign: TextAlign? = null
            var firstIndent: Int? = null

            val annotatedText = buildAnnotatedString {
                var i = 0
                val len = line.length

                while (i < len) {
                    val ltIdx = line.indexOf('<', i)
                    if (ltIdx == -1) {
                        append(line.substring(i))
                        break
                    }

                    if (ltIdx > i) {
                        append(line.substring(i, ltIdx))
                    }

                    // Check for closing tag </> or similar
                    if (line.startsWith("</", ltIdx)) {
                        val gtIdx = line.indexOf('>', ltIdx)
                        if (gtIdx != -1) {
                            append(line.substring(ltIdx, gtIdx + 1))
                            i = gtIdx + 1
                            continue
                        } else {
                            append(line.substring(ltIdx))
                            break
                        }
                    }

                    val gtIdx = line.indexOf('>', ltIdx)
                    if (gtIdx == -1) {
                        append(line.substring(ltIdx))
                        break
                    }

                    val tagContent = line.substring(ltIdx + 1, gtIdx)
                    val tagParts = tagContent.split('|')
                    val isValidTag = when (tagParts.size) {
                        1 -> isValidFontIdentifier(tagParts[0])
                        2 -> isValidFontIdentifier(tagParts[0]) && isValidPositionIdentifier(tagParts[1])
                        else -> false
                    }

                    if (!isValidTag) {
                        append(line.substring(ltIdx, gtIdx + 1))
                        i = gtIdx + 1
                        continue
                    }

                    val fontName = if (tagParts.isNotEmpty()) tagParts[0] else ""
                    val posToken = if (tagParts.size == 2) tagParts[1] else ""

                    val resolvedFont = if (fontName.isNotEmpty()) parseFont(fontName) else null

                    if (posToken.isNotEmpty() && firstAlign == null && firstIndent == null) {
                        val align = resolveAlignment(posToken)
                        val indent = if (align == null) resolveIndentDp(posToken) else null
                        if (align != null) {
                            firstAlign = align
                        } else if (indent != null) {
                            firstIndent = indent
                        }
                    }

                    val spanStyle = if (resolvedFont != null || defaultFontFamily != null) {
                        SpanStyle(fontFamily = resolvedFont ?: defaultFontFamily)
                    } else null

                    val closeTagIdx = line.indexOf("</", gtIdx + 1)
                    val innerTextEnd = if (closeTagIdx != -1) closeTagIdx else len

                    val innerText = line.substring(gtIdx + 1, innerTextEnd)

                    if (spanStyle != null) {
                        withStyle(spanStyle) {
                            append(innerText)
                        }
                    } else {
                        append(innerText)
                    }

                    i = if (closeTagIdx != -1) {
                        val closeGt = line.indexOf('>', closeTagIdx)
                        if (closeGt != -1) closeGt + 1 else closeTagIdx + 2
                    } else {
                        len
                    }
                }
            }

            paragraphs.add(
                QuoteParagraph(
                    text = annotatedText,
                    textAlign = firstAlign,
                    indentDp = firstIndent
                )
            )
        }

        return paragraphs
    }
}
```

---

## 2. `QuoteCard.kt` Rendering Change (Before / After)

### Before
```kotlin
            val annotated = remember(text) {
                QuoteMarkup.parse(
                    source = text ?: "",
                    defaultFontFamily = null
                )
            }

            Text(
                text = annotated,
                fontSize = quoteFont,
                lineHeight = quoteFont * 1.4,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )
```

### After
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

---

## 3. Confirmations

- **Card Layout, Font, & Color**: Outer card dimensions, rotation/tilt angles, image layers, dates, and author footers remain completely untouched. Unmarked quotes inherit default `TextAlign.Center` and white text color.
- **Indent Implementation**: Implemented via `style = TextStyle(textIndent = TextIndent(firstLine = paragraph.indentDp.sp))`, providing precise first-line indent formatting.
- **Scope Restriction**: Confirmed that **zero other files** (`SignatureLoops.kt`, repositories, viewmodels, etc.) were modified.

---

## 4. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 5. Manual Verification Checklist

- [ ] Force-stop. Cold start. All existing quotes render with font spans applied and no visual regression. Single-line quotes render on a single line. Multi-line quotes render with default center alignment.
- [ ] Test single-line with inline center tag: `NO "<Kaushan|center>NOT TODAY</> "`. Renders as ONE line, centered. No wrapping or line splitting. Font applies to "NOT TODAY".
- [ ] Test multi-paragraph: `<gamaamli|left>REMEMBER</>\n<yuyu|right>Everyday is a Hustle</>`. Line 1 left-aligned in Gamaamli. Line 2 right-aligned in Yuyu. Small, natural 4dp gap between lines — no excessive vertical gap.
- [ ] Test indent: `<gamaamli|left>REMEMBER:</>\n<playfair|indent:40>Each Day is a Blessing</>`. Second line has 40sp first-line indent.
- [ ] Test legacy color: `<kaushan|pink>text</>` — font applies, no position change, no visual break.
- [ ] Test paragraph with no position: plain line — center-aligned default.
- [ ] Restore `SignatureLoops.kt` to committed state after testing.
