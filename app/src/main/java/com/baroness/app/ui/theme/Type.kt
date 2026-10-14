package com.baroness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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

// New Fonts
val ChristmasBundleFont = FontFamily(Font(R.font.christmas_bundle, FontWeight.Normal))
val ChristmasDarionaFont = FontFamily(Font(R.font.christmas_dariona, FontWeight.Normal))
val CookieMonsterFont = FontFamily(Font(R.font.cookie_monster, FontWeight.Normal))
val CrayonHandFont = FontFamily(Font(R.font.crayon_hand, FontWeight.Normal))
val EvilScratchFont = FontFamily(Font(R.font.evil_scratch, FontWeight.Normal))
val FilxGirlFont = FontFamily(Font(R.font.filx_girl, FontWeight.Normal))
val GodOfWarFont = FontFamily(Font(R.font.god_of_war, FontWeight.Normal))
val GameOfSquidsFont = FontFamily(Font(R.font.game_of_squids, FontWeight.Normal))
val GraveyardCrimesFont = FontFamily(Font(R.font.graveyard_crimes, FontWeight.Normal))
val HappyBirthdayFont = FontFamily(Font(R.font.happy_birthday, FontWeight.Normal))
val HarukazeFont = FontFamily(Font(R.font.harukaze, FontWeight.Normal))
val HelloChristmasFont = FontFamily(Font(R.font.hello_christmas, FontWeight.Normal))
val HoshikoSatsukiFont = FontFamily(Font(R.font.hoshiko_satsuki, FontWeight.Normal))
val JealousyFont = FontFamily(Font(R.font.jealousy, FontWeight.Normal))
val KlomangBrushFont = FontFamily(Font(R.font.klomang_brush, FontWeight.Normal))
val PowerSkateFont = FontFamily(Font(R.font.power_skate, FontWeight.Normal))
val RacingInTheRainFont = FontFamily(Font(R.font.racing_in_the_rain, FontWeight.Normal))
val SakelarFont = FontFamily(Font(R.font.sakelar, FontWeight.Normal))
val ShareitFont = FontFamily(Font(R.font.shareit, FontWeight.Normal))
val SpellOfAsiaFont = FontFamily(Font(R.font.spell_of_asia, FontWeight.Normal))
val SpookeryFont = FontFamily(Font(R.font.spookery, FontWeight.Normal))
val SpruckenFont = FontFamily(Font(R.font.sprucken, FontWeight.Normal))
val TheRulesOfTheGameFont = FontFamily(Font(R.font.the_rules_of_the_game, FontWeight.Normal))
val TrashSecretFont = FontFamily(Font(R.font.trash_secret, FontWeight.Normal))

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

    // New Fonts
    val ChristmasBundle = ChristmasBundleFont
    val ChristmasDariona = ChristmasDarionaFont
    val CookieMonster = CookieMonsterFont
    val CrayonHand = CrayonHandFont
    val EvilScratch = EvilScratchFont
    val FilxGirl = FilxGirlFont
    val GodOfWar = GodOfWarFont
    val GameOfSquids = GameOfSquidsFont
    val GraveyardCrimes = GraveyardCrimesFont
    val HappyBirthday = HappyBirthdayFont
    val Harukaze = HarukazeFont
    val HelloChristmas = HelloChristmasFont
    val HoshikoSatsuki = HoshikoSatsukiFont
    val Jealousy = JealousyFont
    val KlomangBrush = KlomangBrushFont
    val PowerSkate = PowerSkateFont
    val RacingInTheRain = RacingInTheRainFont
    val Sakelar = SakelarFont
    val Shareit = ShareitFont
    val SpellOfAsia = SpellOfAsiaFont
    val Spookery = SpookeryFont
    val Sprucken = SpruckenFont
    val TheRulesOfTheGame = TheRulesOfTheGameFont
    val TrashSecret = TrashSecretFont

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
        "christmasbundle", "christmas_bundle", "xmas_bundle" -> ChristmasBundle
        "christmasdariona", "christmas_dariona", "xmas_dariona" -> ChristmasDariona
        "cookiemonster", "cookie_monster", "cookie" -> CookieMonster
        "crayonhand", "crayon_hand", "crayon" -> CrayonHand
        "evilscratch", "evil_scratch", "evil" -> EvilScratch
        "filxgirl", "filx_girl", "filx" -> FilxGirl
        "godofwar", "god_of_war", "god" -> GodOfWar
        "gameofsquids", "game_of_squids", "squids" -> GameOfSquids
        "graveyardcrimes", "graveyard_crimes", "graveyard" -> GraveyardCrimes
        "happybirthday", "happy_birthday", "birthday" -> HappyBirthday
        "harukaze" -> Harukaze
        "hellochristmas", "hello_christmas", "hello_xmas" -> HelloChristmas
        "hoshikosatsuki", "hoshiko_satsuki", "hoshiko" -> HoshikoSatsuki
        "jealousy" -> Jealousy
        "klomangbrush", "klomang_brush", "klomang" -> KlomangBrush
        "powerskate", "power_skate", "skate" -> PowerSkate
        "racingintherain", "racing_in_the_rain", "rain" -> RacingInTheRain
        "sakelar" -> Sakelar
        "shareit" -> Shareit
        "spellofasia", "spell_of_asia", "asia" -> SpellOfAsia
        "spookery" -> Spookery
        "sprucken" -> Sprucken
        "therulesofthegame", "the_rules_of_the_game", "rules" -> TheRulesOfTheGame
        "trashsecret", "trash_secret", "trash" -> TrashSecret
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

/**
 * Builds a dynamic Material 3 Typography suite using the given FontFamily and base FontWeight.
 */
fun buildTypography(fontFamily: FontFamily, baseWeight: FontWeight = FontWeight.Normal): Typography {
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily, fontWeight = baseWeight),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily, fontWeight = baseWeight)
    )
}

// Default Material typography fallback
val Typography = buildTypography(FontFamily.Default, FontWeight.Normal)
