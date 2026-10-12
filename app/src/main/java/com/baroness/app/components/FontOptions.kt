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
    FontFamilyOption("yuyu", "Yuyu", AppFonts.Yuyu, "Y", emptyList()),
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
    FontFamilyOption("matemasie", "Matemasie", AppFonts.Matemasie, "M", emptyList()),
    FontFamilyOption("dancingscript", "Dancing Script", AppFonts.DancingScript, "D", listOf(
        FontWeightOption("dancingscript_regular", "Regular", FontWeight.Normal),
        FontWeightOption("dancingscript_medium", "Medium", FontWeight.Medium),
        FontWeightOption("dancingscript_semibold", "SemiBold", FontWeight.SemiBold),
        FontWeightOption("dancingscript_bold", "Bold", FontWeight.Bold)
    )),
    FontFamilyOption("marckscript", "Marck Script", AppFonts.MarckScript, "M", emptyList()),
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
    FontFamilyOption("permanentmarker", "Permanent Marker", AppFonts.PermanentMarker, "P", emptyList()),
    FontFamilyOption("shadowsintolight", "Shadows Into Light", AppFonts.ShadowsIntoLight, "S", emptyList())
)
