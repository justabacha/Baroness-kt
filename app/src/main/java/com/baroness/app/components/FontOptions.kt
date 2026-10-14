package com.baroness.app.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.baroness.app.ui.theme.AppFonts

data class FontFamilyOption(
    val id: String,
    val familyName: String,
    val fontFamily: FontFamily,
    val initial: String,
    val weights: List<FontWeightOption>
)

data class FontWeightOption(
    val id: String,
    val displayName: String,
    val fontWeight: FontWeight
)

val fontFamilies = listOf(
    FontFamilyOption("lifesavers", "Lifesavers", AppFonts.Lifesavers, "L", listOf(
        FontWeightOption("lifesavers_regular", "Regular", FontWeight.Normal),
        FontWeightOption("lifesavers_bold", "Bold", FontWeight.Bold),
        FontWeightOption("lifesavers_extrabold", "ExtraBold", FontWeight.ExtraBold)
    )),
    FontFamilyOption("robotomono", "RobotoMono", AppFonts.RobotoMono, "R", listOf(
        FontWeightOption("robotomono_thin", "Thin", FontWeight.Thin),
        FontWeightOption("robotomono_light", "Light", FontWeight.Light),
        FontWeightOption("robotomono_regular", "Regular", FontWeight.Normal),
        FontWeightOption("robotomono_medium", "Medium", FontWeight.Medium),
        FontWeightOption("robotomono_semibold", "SemiBold", FontWeight.SemiBold),
        FontWeightOption("robotomono_bold", "Bold", FontWeight.Bold)
    )),
    FontFamilyOption("gamaamli", "Gamaamli", AppFonts.Gamaamli, "G", emptyList()),
    FontFamilyOption("playfairdisplay", "Playfair Display", AppFonts.PlayfairDisplay, "P", listOf(
        FontWeightOption("playfairdisplay_regular", "Regular", FontWeight.Normal),
        FontWeightOption("playfairdisplay_italic", "Italic", FontWeight.Normal),
        FontWeightOption("playfairdisplay_medium", "Medium", FontWeight.Medium),
        FontWeightOption("playfairdisplay_bold", "Bold", FontWeight.Bold),
        FontWeightOption("playfairdisplay_extrabold", "ExtraBold", FontWeight.ExtraBold),
        FontWeightOption("playfairdisplay_black", "Black", FontWeight.Black),
        FontWeightOption("playfairdisplay_blackitalic", "Black Italic", FontWeight.Black)
    )),
    FontFamilyOption("kaushanscript", "Kaushan Script", AppFonts.KaushanScript, "K", emptyList()),
    FontFamilyOption("gameofsquids", "Game of Squids", AppFonts.GameOfSquids, "G", emptyList()),
    FontFamilyOption("shareit", "Shareit", AppFonts.Shareit, "S", emptyList()),
    FontFamilyOption("hellochristmas", "Hello Christmas", AppFonts.HelloChristmas, "H", emptyList()),
    FontFamilyOption("hoshikosatsuki", "Hoshiko Satsuki", AppFonts.HoshikoSatsuki, "H", emptyList()),
    FontFamilyOption("sakelar", "Sakelar", AppFonts.Sakelar, "S", emptyList()),
    FontFamilyOption("spellofasia", "Spell of Asia", AppFonts.SpellOfAsia, "S", emptyList()),
    FontFamilyOption("cookiemonster", "Cookie Monster", AppFonts.CookieMonster, "C", emptyList()),
    FontFamilyOption("filxgirl", "Filx Girl", AppFonts.FilxGirl, "F", emptyList())
)
