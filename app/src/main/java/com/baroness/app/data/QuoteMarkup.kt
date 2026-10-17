package com.baroness.app.data

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.baroness.app.ui.theme.AppFonts

data class QuoteSpan(
    val text: String,
    val fontFamily: FontFamily?,   // null = inherit default
    val sizeScale: Float = 1.0f     // 1.0f = normal
)

data class QuoteParagraph(
    val spans: List<QuoteSpan>,
    val textAlign: TextAlign?,
    val indentDp: Int?,
    val isLine: Boolean = false
)

data class QuoteFont(
    val family: FontFamily,
    val sizeScale: Float = 1.0f
)

object QuoteMarkup {

    private val FONT_REGISTRY: Map<String, QuoteFont> = mapOf(
        // Existing 11 — default scale 1.0 except where noted
        "yuyu" to QuoteFont(AppFonts.Yuyu, 1.2f),
        "lifesavers" to QuoteFont(AppFonts.Lifesavers),
        "robotomono" to QuoteFont(AppFonts.RobotoMono),
        "gamaamli" to QuoteFont(AppFonts.Gamaamli),
        "matemasie" to QuoteFont(AppFonts.Matemasie),
        "dancing" to QuoteFont(AppFonts.DancingScript, 1.2f),
        "marck" to QuoteFont(AppFonts.MarckScript, 1.2f),
        "playfair" to QuoteFont(AppFonts.PlayfairDisplay),
        "kaushan" to QuoteFont(AppFonts.KaushanScript),
        "marker" to QuoteFont(AppFonts.PermanentMarker),
        "shadows" to QuoteFont(AppFonts.ShadowsIntoLight, 1.2f),

        // 24 new fonts — all default 1.0 except the scaled ones
        "xmas_bundle" to QuoteFont(AppFonts.ChristmasBundle),
        "xmas_dariona" to QuoteFont(AppFonts.ChristmasDariona),
        "cookie" to QuoteFont(AppFonts.CookieMonster),
        "crayon" to QuoteFont(AppFonts.CrayonHand),
        "evil" to QuoteFont(AppFonts.EvilScratch),
        "filx" to QuoteFont(AppFonts.FilxGirl, 1.4f),
        "god" to QuoteFont(AppFonts.GodOfWar),
        "squids" to QuoteFont(AppFonts.GameOfSquids),
        "graveyard" to QuoteFont(AppFonts.GraveyardCrimes),
        "birthday" to QuoteFont(AppFonts.HappyBirthday),
        "harukaze" to QuoteFont(AppFonts.Harukaze, 1.3f),
        "hello_xmas" to QuoteFont(AppFonts.HelloChristmas, 1.1f),
        "hoshiko" to QuoteFont(AppFonts.HoshikoSatsuki),
        "jealousy" to QuoteFont(AppFonts.Jealousy),
        "klomang" to QuoteFont(AppFonts.KlomangBrush),
        "skate" to QuoteFont(AppFonts.PowerSkate),
        "rain" to QuoteFont(AppFonts.RacingInTheRain),
        "sakelar" to QuoteFont(AppFonts.Sakelar, 1.3f),
        "shareit" to QuoteFont(AppFonts.Shareit, 1.4f),
        "asia" to QuoteFont(AppFonts.SpellOfAsia),
        "spookery" to QuoteFont(AppFonts.Spookery),
        "sprucken" to QuoteFont(AppFonts.Sprucken),
        "rules" to QuoteFont(AppFonts.TheRulesOfTheGame),
        "trash" to QuoteFont(AppFonts.TrashSecret)
    )

    fun allFonts(): Map<String, QuoteFont> = FONT_REGISTRY

    private fun parseFont(fontStr: String): QuoteFont? {
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
     * - A raw line equal to "{line}" produces a divider paragraph (isLine = true).
     * - Position attributes (align or indent) are extracted per paragraph from the FIRST position tag encountered.
     * - Font styling and size scaling are emitted as QuoteSpan elements inside each QuoteParagraph.
     * - Unknown font or position names fall back gracefully.
     * - Unclosed tags auto-close at end of string.
     */
    fun parse(
        source: String,
        defaultFontFamily: FontFamily? = null
    ): List<QuoteParagraph> {
        val rawLines = source.split('\n')
        val paragraphs = mutableListOf<QuoteParagraph>()

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

            var i = 0
            val len = line.length

            while (i < len) {
                val ltIdx = line.indexOf('<', i)
                if (ltIdx == -1) {
                    val outerText = line.substring(i)
                    if (outerText.isNotEmpty()) {
                        spans.add(QuoteSpan(outerText, null, 1.0f))
                    }
                    break
                }

                if (ltIdx > i) {
                    val outerText = line.substring(i, ltIdx)
                    if (outerText.isNotEmpty()) {
                        spans.add(QuoteSpan(outerText, null, 1.0f))
                    }
                }

                if (line.startsWith("</", ltIdx)) {
                    val gtIdx = line.indexOf('>', ltIdx)
                    if (gtIdx != -1) {
                        spans.add(QuoteSpan(line.substring(ltIdx, gtIdx + 1), null, 1.0f))
                        i = gtIdx + 1
                        continue
                    } else {
                        spans.add(QuoteSpan(line.substring(ltIdx), null, 1.0f))
                        break
                    }
                }

                val gtIdx = line.indexOf('>', ltIdx)
                if (gtIdx == -1) {
                    spans.add(QuoteSpan(line.substring(ltIdx), null, 1.0f))
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
                    spans.add(QuoteSpan(line.substring(ltIdx, gtIdx + 1), null, 1.0f))
                    i = gtIdx + 1
                    continue
                }

                val fontName = if (tagParts.isNotEmpty()) tagParts[0] else ""
                val posToken = if (tagParts.size == 2) tagParts[1] else ""

                val quoteFont = if (fontName.isNotEmpty()) parseFont(fontName) else null

                if (posToken.isNotEmpty() && firstAlign == null && firstIndent == null) {
                    val align = resolveAlignment(posToken)
                    val indent = if (align == null) resolveIndentDp(posToken) else null
                    if (align != null) {
                        firstAlign = align
                    } else if (indent != null) {
                        firstIndent = indent
                    }
                }

                val closeTagIdx = line.indexOf("</", gtIdx + 1)
                val innerTextEnd = if (closeTagIdx != -1) closeTagIdx else len
                val innerText = line.substring(gtIdx + 1, innerTextEnd)

                if (innerText.isNotEmpty()) {
                    val resolvedFamily = quoteFont?.family ?: defaultFontFamily
                    val resolvedScale = quoteFont?.sizeScale ?: 1.0f
                    spans.add(QuoteSpan(innerText, resolvedFamily, resolvedScale))
                }

                i = if (closeTagIdx != -1) {
                    val closeGt = line.indexOf('>', closeTagIdx)
                    if (closeGt != -1) closeGt + 1 else closeTagIdx + 2
                } else {
                    len
                }
            }

            if (spans.isNotEmpty()) {
                paragraphs.add(
                    QuoteParagraph(
                        spans = spans,
                        textAlign = firstAlign,
                        indentDp = firstIndent
                    )
                )
            }
        }

        return paragraphs
    }
}
