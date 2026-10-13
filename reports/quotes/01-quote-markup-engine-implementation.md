# Quote Card — Inline Font and Color Markup Engine Implementation Record

## Context

Following the feasibility audit (`reports/quotes/00-quote-font-engine-audit.md`), an inline markup parser (`QuoteMarkup.kt`) has been implemented to support whole-quote font overrides and per-span inline font and color styling via `<font|color>text</>` syntax without modifying data models or persistence pipelines.

---

## 1. Complete Source: `QuoteMarkup.kt`

Created `app/src/main/java/com/baroness/app/data/QuoteMarkup.kt`:

```kotlin
package com.baroness.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import com.baroness.app.ui.theme.AppFonts

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

    private val COLOR_REGISTRY: Map<String, Color> = mapOf(
        "white" to Color.White,
        "pink" to Color(0xFFFF88CC),
        "warm" to Color(0xFFFFB74D),
        "gold" to Color(0xFFFFD54F),
        "ice" to Color(0xFF9EE7FF),
        "sky" to Color(0xFF80BFFF),
        "lavender" to Color(0xFFC8A8FF),
        "rose" to Color(0xFFFF9E9E),
        "mint" to Color(0xFF9EE7B0)
    )

    private fun parseColor(colorStr: String): Color? {
        val trimmed = colorStr.trim().lowercase()
        COLOR_REGISTRY[trimmed]?.let { return it }
        if (trimmed.startsWith("#")) {
            return try {
                val hex = trimmed.removePrefix("#")
                val colorLong = when (hex.length) {
                    6 -> "FF$hex".toLong(16)
                    8 -> hex.toLong(16)
                    else -> return null
                }
                Color(colorLong)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun parseFont(fontStr: String): FontFamily? {
        return FONT_REGISTRY[fontStr.trim().lowercase()]
    }

    /**
     * Parses a quote string containing optional inline markup into an AnnotatedString.
     * - Unmarked text renders with defaultStyle.
     * - Unknown font or color names fall back to the corresponding default (not an error).
     * - Unclosed tags auto-close at end of string.
     * - A literal '<' that is not followed by a valid opening pattern is rendered as-is.
     */
    fun parse(
        source: String,
        defaultFontFamily: FontFamily?,
        defaultColor: Color
    ): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = source.length

            while (i < len) {
                val ltIdx = source.indexOf('<', i)
                if (ltIdx == -1) {
                    append(source.substring(i))
                    break
                }

                if (ltIdx > i) {
                    append(source.substring(i, ltIdx))
                }

                if (source.startsWith("</", ltIdx)) {
                    val gtIdx = source.indexOf('>', ltIdx)
                    if (gtIdx != -1) {
                        append(source.substring(ltIdx, gtIdx + 1))
                        i = gtIdx + 1
                        continue
                    } else {
                        append(source.substring(ltIdx))
                        break
                    }
                }

                val gtIdx = source.indexOf('>', ltIdx)
                if (gtIdx == -1) {
                    append(source.substring(ltIdx))
                    break
                }

                val tagContent = source.substring(ltIdx + 1, gtIdx)
                val tagParts = tagContent.split('|')
                val isValidTag = when (tagParts.size) {
                    1 -> isValidIdentifier(tagParts[0])
                    2 -> (tagParts[0].isEmpty() || isValidIdentifier(tagParts[0])) && isValidIdentifier(tagParts[1])
                    else -> false
                }

                if (!isValidTag) {
                    append(source.substring(ltIdx, gtIdx + 1))
                    i = gtIdx + 1
                    continue
                }

                val fontName = if (tagParts.isNotEmpty()) tagParts[0] else ""
                val colorName = if (tagParts.size == 2) tagParts[1] else ""

                val resolvedFont = if (fontName.isNotEmpty()) parseFont(fontName) else null
                val resolvedColor = if (colorName.isNotEmpty()) parseColor(colorName) else null

                val spanStyle = SpanStyle(
                    fontFamily = resolvedFont ?: defaultFontFamily,
                    color = resolvedColor ?: defaultColor
                )

                val closeTagIdx = source.indexOf("</", gtIdx + 1)
                val innerTextEnd = if (closeTagIdx != -1) closeTagIdx else len

                val innerText = source.substring(gtIdx + 1, innerTextEnd)

                withStyle(spanStyle) {
                    append(innerText)
                }

                i = if (closeTagIdx != -1) {
                    val closeGt = source.indexOf('>', closeTagIdx)
                    if (closeGt != -1) closeGt + 1 else closeTagIdx + 2
                } else {
                    len
                }
            }
        }
    }

    private fun isValidIdentifier(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("#")) {
            val hex = trimmed.removePrefix("#")
            return hex.length in listOf(6, 8) && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }
        return trimmed.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    }
}
```

---

## 2. `QuoteCard.kt` Change (Before / After)

### Before
```kotlin
            Text(
                text = text ?: "",
                color = Color.White,
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
            val annotated = remember(text) {
                QuoteMarkup.parse(
                    source = text ?: "",
                    defaultFontFamily = null,
                    defaultColor = Color.White
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

---

## 3. Font and Color Registries

### Font Registry (`FONT_REGISTRY`)
- `"yuyu"` -> `AppFonts.Yuyu`
- `"lifesavers"` -> `AppFonts.Lifesavers`
- `"robotomono"` -> `AppFonts.RobotoMono`
- `"gamaamli"` -> `AppFonts.Gamaamli`
- `"matemasie"` -> `AppFonts.Matemasie`
- `"dancing"` -> `AppFonts.DancingScript`
- `"marck"` -> `AppFonts.MarckScript`
- `"playfair"` -> `AppFonts.PlayfairDisplay`
- `"kaushan"` -> `AppFonts.KaushanScript`
- `"marker"` -> `AppFonts.PermanentMarker`
- `"shadows"` -> `AppFonts.ShadowsIntoLight`

### Color Registry (`COLOR_REGISTRY`)
- `"white"` -> `Color.White`
- `"pink"` -> `Color(0xFFFF88CC)`
- `"warm"` -> `Color(0xFFFFB74D)`
- `"gold"` -> `Color(0xFFFFD54F)`
- `"ice"` -> `Color(0xFF9EE7FF)`
- `"sky"` -> `Color(0xFF80BFFF)`
- `"lavender"` -> `Color(0xFFC8A8FF)`
- `"rose"` -> `Color(0xFFFF9E9E)`
- `"mint"` -> `Color(0xFF9EE7B0)`
- Plus arbitrary hex parsing (`#RRGGBB` / `#AARRGGBB`).

---

## 4. Confirmation of Scope Restrictions

Confirmed that **zero other files** were modified. `SignatureLoops.kt`, `QuoteRepository.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`, and all other screens/components remain untouched.

---

## 5. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 6. Manual Verification Checklist

- [ ] Force-stop the app. Tap the icon. Dashboard loads with unmarked quotes — renders exactly as before with zero visual regression.
- [ ] Modify a test quote in `SignatureLoops.kt` locally: `I still <kaushan|pink>WISH</> for at 11:11`.
- [ ] Rebuild and cold start. Verify "I still" renders in default font and white; "WISH" renders in Kaushan Script and pink; "for at 11:11" renders in default font and white.
- [ ] Test font-only tag: `<playfair>WISH</>` — only font changes.
- [ ] Test color-only tag: `<|gold>WISH</>` — only color changes.
- [ ] Test unknown name: `<unknownfont|pink>WISH</>` — font falls back to default, color applies.
- [ ] Test unclosed tag: `<kaushan|pink>WISH` — WISH styles extend to end of string.
- [ ] Test literal angle bracket: `"I <3 you"` — renders literally.
- [ ] Restore `SignatureLoops.kt` to original state after verification.
