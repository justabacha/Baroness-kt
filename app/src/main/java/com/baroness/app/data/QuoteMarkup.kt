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
        // 11 Original Fonts
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
        "shadows" to AppFonts.ShadowsIntoLight,

        // New Fonts
        "xmas_bundle" to AppFonts.ChristmasBundle,
        "xmas_dariona" to AppFonts.ChristmasDariona,
        "cookie" to AppFonts.CookieMonster,
        "crayon" to AppFonts.CrayonHand,
        "evil" to AppFonts.EvilScratch,
        "filx" to AppFonts.FilxGirl,
        "god" to AppFonts.GodOfWar,
        "squids" to AppFonts.GameOfSquids,
        "graveyard" to AppFonts.GraveyardCrimes,
        "birthday" to AppFonts.HappyBirthday,
        "harukaze" to AppFonts.Harukaze,
        "hello_xmas" to AppFonts.HelloChristmas,
        "hoshiko" to AppFonts.HoshikoSatsuki,
        "jealousy" to AppFonts.Jealousy,
        "klomang" to AppFonts.KlomangBrush,
        "skate" to AppFonts.PowerSkate,
        "rain" to AppFonts.RacingInTheRain,
        "sakelar" to AppFonts.Sakelar,
        "shareit" to AppFonts.Shareit,
        "asia" to AppFonts.SpellOfAsia,
        "spookery" to AppFonts.Spookery,
        "sprucken" to AppFonts.Sprucken,
        "rules" to AppFonts.TheRulesOfTheGame,
        "trash" to AppFonts.TrashSecret
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
        return trimmed.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
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
                        2 -> isValidFontIdentifier(tagParts[0])
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

                    // Extract the FIRST position marker encountered in this paragraph. Subsequent position markers in the same paragraph are ignored.
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
