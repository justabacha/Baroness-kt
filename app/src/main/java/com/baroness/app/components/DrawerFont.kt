package com.baroness.app.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.AppColors
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

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

@Composable
fun DrawerFont(
    state: FontBoxState,
    onStateChange: (FontBoxState) -> Unit,
    viewModel: SettingsViewModel,
    hazeState: HazeState
) {
    val activeFontId by viewModel.activeFont.collectAsState()
    val previewFontId by viewModel.previewFont.collectAsState()

    val title = when (state) {
        is FontBoxState.Preview -> "FONT PREVIEW"
        is FontBoxState.WeightSelect -> {
            val family = fontFamilies.find { it.id == state.familyId }
            family?.familyName?.uppercase() ?: "SELECT WEIGHT"
        }
        else -> "FONT"
    }

    val isExpanded = state != FontBoxState.Collapsed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 20.dp
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .animateContentSize()
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (state) {
                            FontBoxState.Collapsed -> onStateChange(FontBoxState.Expanded)
                            FontBoxState.Expanded -> onStateChange(FontBoxState.Collapsed)
                            is FontBoxState.WeightSelect -> onStateChange(FontBoxState.Expanded)
                            is FontBoxState.Preview -> {
                                val family = fontFamilies.find { it.id == state.familyId }
                                if (family != null && family.weights.isNotEmpty()) {
                                    onStateChange(FontBoxState.WeightSelect(state.familyId))
                                } else {
                                    onStateChange(FontBoxState.Expanded)
                                }
                            }
                        }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            // Content Box (ALWAYS present)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (state) {
                    FontBoxState.Collapsed -> {
                        FontCollapsedList(
                            activeId = activeFontId,
                            onSelect = { familyId ->
                                val family = fontFamilies.find { it.id == familyId }
                                if (family != null) {
                                    if (family.weights.isEmpty()) {
                                        viewModel.previewFont(familyId)
                                    } else {
                                        viewModel.previewFont(familyId, family.weights.first().id)
                                    }
                                }
                                onStateChange(FontBoxState.Expanded)
                            }
                        )
                    }
                    FontBoxState.Expanded -> {
                        FontExpandedGrid(
                            activeId = activeFontId,
                            onReview = { familyId ->
                                val family = fontFamilies.find { it.id == familyId }
                                if (family != null) {
                                    if (family.weights.isEmpty()) {
                                        viewModel.previewFont(familyId)
                                        onStateChange(FontBoxState.Preview(familyId, null))
                                    } else {
                                        onStateChange(FontBoxState.WeightSelect(familyId))
                                    }
                                }
                            }
                        )
                    }
                    is FontBoxState.WeightSelect -> {
                        val family = fontFamilies.find { it.id == state.familyId }
                        if (family != null) {
                            FontWeightList(
                                family = family,
                                activeId = activeFontId,
                                onSelect = { weightId ->
                                    viewModel.previewFont(family.id, weightId)
                                    onStateChange(FontBoxState.Preview(family.id, weightId))
                                }
                            )
                        }
                    }
                    is FontBoxState.Preview -> {
                        val family = fontFamilies.find { it.id == state.familyId }
                        val weight = family?.weights?.find { it.id == state.weightId }
                        if (family != null) {
                            FontPreviewArea(
                                family = family,
                                weight = weight,
                                isActive = activeFontId == previewFontId,
                                onApply = {
                                    viewModel.applyFont()
                                    onStateChange(FontBoxState.Expanded)
                                },
                                onRevert = {
                                    viewModel.revertFont()
                                    onStateChange(FontBoxState.Expanded)
                                },
                                onRevertToDefault = {
                                    viewModel.revertFontToDefault()
                                    onStateChange(FontBoxState.Expanded)
                                },
                            showWarning = { msg, icon -> viewModel.showWarning(msg, icon) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FontCollapsedList(activeId: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fontFamilies.forEach { family ->
            val isActive = activeId.startsWith(family.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(family.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(if (isActive) 1.5.dp else 0.dp, if (isActive) Color(0xFF4FC3F7) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = family.initial,
                        color = Color.White,
                        fontFamily = family.fontFamily,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = family.familyName,
                    color = Color.White,
                    fontFamily = AppFonts.Lifesavers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isActive) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FontExpandedGrid(activeId: String, onReview: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(fontFamilies) { family ->
            val isActive = activeId.startsWith(family.id)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(if (isActive) 2.dp else 0.dp, if (isActive) Color(0xFF4FC3F7) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = family.initial,
                        color = Color.White,
                        fontFamily = family.fontFamily,
                        fontSize = 22.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = family.familyName,
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onReview(family.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("REVIEW", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun FontWeightList(
    family: FontFamilyOption,
    activeId: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        family.weights.forEach { weight ->
            val fullId = "${family.id}_${weight.id}"
            val isActive = activeId == fullId
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, if (isActive) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(25.dp))
                    .clickable { onSelect(weight.id) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = family.initial,
                    color = Color.White,
                    fontFamily = family.fontFamily,
                    fontWeight = weight.fontWeight,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = weight.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isActive) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FontPreviewArea(
    family: FontFamilyOption,
    weight: FontWeightOption?,
    isActive: Boolean,
    onApply: () -> Unit,
    onRevert: () -> Unit,
    onRevertToDefault: () -> Unit,
    showWarning: (String, Boolean) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "See that's what I was talking about, I love this font!",
                    color = AppColors.textPrimary,
                    fontFamily = family.fontFamily,
                    fontWeight = weight?.fontWeight ?: FontWeight.Normal,
                    fontSize = 16.sp
                )
                Text(
                    text = "Hello Baroness ✨",
                    color = AppColors.textPrimary,
                    fontFamily = family.fontFamily,
                    fontWeight = weight?.fontWeight ?: FontWeight.Normal,
                    fontSize = 22.sp
                )
                Text(
                    text = "1234567890",
                    color = AppColors.textPrimary,
                    fontFamily = family.fontFamily,
                    fontWeight = weight?.fontWeight ?: FontWeight.Normal,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // REVERT Button
            Button(
                onClick = {
                    if (isActive) {
                        onRevertToDefault()
                        showWarning("Reverted to default font", false)
                    } else {
                        showWarning("Nothing to revert", true)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFFF453A) else Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White.copy(alpha = if (isActive) 1f else 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REVERT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            // APPLY Button
            Button(
                onClick = {
                    if (isActive) {
                        showWarning("This font is already in use", true)
                    } else {
                        onApply()
                        showWarning("Font applied successfully", false)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color.White.copy(alpha = 0.05f) else Color(0xFFC6ABFF).copy(alpha = 0.8f),
                    contentColor = if (isActive) Color.White.copy(alpha = 0.2f) else AppColors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPLY", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
