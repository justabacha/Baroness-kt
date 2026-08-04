package com.baroness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.baroness.app.R

// Font Family Definitions
val YuyuFont = FontFamily(
    Font(R.font.yuyu_regular, FontWeight.Normal)
)

val LifesaversFont = FontFamily(
    Font(R.font.lifesavers_regular, FontWeight.Normal),
    Font(R.font.lifesavers_bold, FontWeight.Bold),
    Font(R.font.lifesavers_extrabold, FontWeight.ExtraBold)
)

val RobotoMonoFont = FontFamily(
    Font(R.font.robotomono_thin, FontWeight.Thin),
    Font(R.font.robotomono_thinitalic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.robotomono_extralight, FontWeight.ExtraLight),
    Font(R.font.robotomono_extralightitalic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.robotomono_light, FontWeight.Light),
    Font(R.font.robotomono_lightitalic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.robotomono_regular, FontWeight.Normal),
    Font(R.font.robotomono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.robotomono_medium, FontWeight.Medium),
    Font(R.font.robotomono_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.robotomono_semibold, FontWeight.SemiBold),
    Font(R.font.robotomono_semibolditalic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.robotomono_bold, FontWeight.Bold),
    Font(R.font.robotomono_bolditalic, FontWeight.Bold, FontStyle.Italic)
)

val GamaamliFont = FontFamily(
    Font(R.font.gamaamli_regular, FontWeight.Normal)
)

val MatemasieFont = FontFamily(
    Font(R.font.matemasie_regular, FontWeight.Normal)
)

val DancingScriptFont = FontFamily(
    Font(R.font.dancingscript_regular, FontWeight.Normal),
    Font(R.font.dancingscript_medium, FontWeight.Medium),
    Font(R.font.dancingscript_semibold, FontWeight.SemiBold),
    Font(R.font.dancingscript_bold, FontWeight.Bold)
)

val MarckScriptFont = FontFamily(
    Font(R.font.marckscript_regular, FontWeight.Normal)
)

val PlayfairDisplayFont = FontFamily(
    Font(R.font.playfairdisplay_regular, FontWeight.Normal),
    Font(R.font.playfairdisplay_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.playfairdisplay_medium, FontWeight.Medium),
    Font(R.font.playfairdisplay_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.playfairdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.playfairdisplay_semibolditalic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.playfairdisplay_bold, FontWeight.Bold),
    Font(R.font.playfairdisplay_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.playfairdisplay_extrabold, FontWeight.ExtraBold),
    Font(R.font.playfairdisplay_extrabolditalic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.playfairdisplay_black, FontWeight.Black),
    Font(R.font.playfairdisplay_blackitalic, FontWeight.Black, FontStyle.Italic)
)

val KaushanScriptFont = FontFamily(
    Font(R.font.kaushanscript_regular, FontWeight.Normal)
)

val PermanentMarkerFont = FontFamily(
    Font(R.font.permanentmarker_regular, FontWeight.Normal)
)

val ShadowsIntoLightFont = FontFamily(
    Font(R.font.shadowsintolight_regular, FontWeight.Normal)
)

/**
 * Centralized registry for all custom fonts in the project.
 */
object AppFonts {
    val Yuyu = YuyuFont
    val Lifesavers = LifesaversFont
    val RobotoMono = RobotoMonoFont
    val Gamaamli = GamaamliFont
    val Matemasie = MatemasieFont
    val DancingScript = DancingScriptFont
    val MarckScript = MarckScriptFont
    val PlayfairDisplay = PlayfairDisplayFont
    val KaushanScript = KaushanScriptFont
    val PermanentMarker = PermanentMarkerFont
    val ShadowsIntoLight = ShadowsIntoLightFont

    /**
     * Retrieves a FontFamily by its name (case-insensitive).
     */
    fun byName(name: String): FontFamily? = when (name.lowercase()) {
        "yuyu" -> Yuyu
        "lifesavers" -> Lifesavers
        "robotomono" -> RobotoMono
        "gamaamli" -> Gamaamli
        "matemasie" -> Matemasie
        "dancingscript" -> DancingScript
        "marckscript" -> MarckScript
        "playfairdisplay" -> PlayfairDisplay
        "kaushanscript" -> KaushanScript
        "permanentmarker" -> PermanentMarker
        "shadowsintolight" -> ShadowsIntoLight
        else -> null
    }

    /**
     * Resolves a font ID (with optional weight) to FontFamily and FontWeight.
     *
     * @param fontId Format: "familyId" or "familyId_weightId" or "system"
     * @return Pair of FontFamily and FontWeight, or null if not found
     */
    fun resolve(fontId: String): Pair<FontFamily, FontWeight>? {
        if (fontId == "system") {
            return Pair(FontFamily.Default, FontWeight.Normal)
        }

        val familyId = if (fontId.contains("_")) fontId.substringBefore("_") else fontId
        val weightId = if (fontId.contains("_")) fontId.substringAfter("_") else null

        val fontFamily = byName(familyId) ?: return null

        val fontWeight = when (weightId) {
            "regular", null -> FontWeight.Normal
            "italic" -> FontWeight.Normal
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            "bold" -> FontWeight.Bold
            "extrabold" -> FontWeight.ExtraBold
            "black", "blackitalic" -> FontWeight.Black
            "thin" -> FontWeight.Thin
            "light" -> FontWeight.Light
            "extralight" -> FontWeight.ExtraLight
            else -> FontWeight.Normal
        }

        return Pair(fontFamily, fontWeight)
    }
}

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
