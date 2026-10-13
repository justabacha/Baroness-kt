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

                // Check for closing tag </> or similar
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
