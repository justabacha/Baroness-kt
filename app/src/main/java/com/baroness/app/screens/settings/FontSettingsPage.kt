package com.baroness.app.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baroness.app.components.fontFamilies
import com.baroness.app.models.AppColors
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FontSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val activeFontId by settingsViewModel.activeFont.collectAsStateWithLifecycle()
    val previewFontId by settingsViewModel.previewFont.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.revertFont()
        }
    }

    // Determine current selected family and weight
    val selectedFamily = remember(previewFontId) {
        val familyId = if (previewFontId.contains("_")) previewFontId.substringBefore("_") else previewFontId
        fontFamilies.find { it.id == familyId } ?: fontFamilies.find { it.id == "playfairdisplay" } ?: fontFamilies.first()
    }

    val selectedWeight = remember(previewFontId, selectedFamily) {
        if (previewFontId.contains("_")) {
            val weightId = previewFontId.substringAfter("_")
            selectedFamily.weights.find { it.id == weightId }
        } else {
            selectedFamily.weights.firstOrNull()
        }
    }

    val (resolvedFontFamily, resolvedFontWeight) = remember(previewFontId) {
        AppFonts.resolve(previewFontId) ?: Pair(selectedFamily.fontFamily, selectedWeight?.fontWeight ?: FontWeight.Normal)
    }

    val isCurrentlyActive = activeFontId == previewFontId
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TYPOGRAPHY & FONT",
                        color = Color.White,
                        fontFamily = AppFonts.Gamaamli,
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Font Family Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FONT FAMILIES",
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )

                    val fontListScrollState = rememberScrollState()
                    val density = LocalDensity.current

                    // Scroll to active font when page opens
                    LaunchedEffect(activeFontId) {
                        val activeIndex = fontFamilies.indexOfFirst { activeFontId.startsWith(it.id) }
                        if (activeIndex > 0) {
                            val itemHeightPx = with(density) { 66.dp.toPx() } // row height (56dp) + spacing (10dp)
                            fontListScrollState.scrollTo((activeIndex * itemHeightPx).toInt())
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(fontListScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        fontFamilies.forEach { family ->
                            val isFamilySelected = selectedFamily.id == family.id
                            val isFamilyActive = activeFontId.startsWith(family.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isFamilySelected) Color.White.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = if (isFamilySelected) 1.5.dp else 0.5.dp,
                                        color = if (isFamilySelected) accentColor else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (family.weights.isEmpty()) {
                                            settingsViewModel.previewFont(family.id)
                                        } else {
                                            settingsViewModel.previewFont(family.id, family.weights.first().id)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .border(
                                                1.dp,
                                                if (isFamilySelected) accentColor else Color.White.copy(alpha = 0.3f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = family.initial,
                                            color = Color.White,
                                            fontFamily = family.fontFamily,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = family.familyName,
                                        color = Color.White,
                                        fontFamily = family.fontFamily,
                                        fontSize = 16.sp
                                    )
                                }

                                if (isFamilyActive) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, accentColor)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Weight Selector Card (If selected family has weights)
            if (selectedFamily.weights.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    border = cardBorder
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "${selectedFamily.familyName.uppercase()} WEIGHT",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedFamily.weights.forEach { weight ->
                                val isWeightSelected = selectedWeight?.id == weight.id
                                FilterChip(
                                    selected = isWeightSelected,
                                    onClick = {
                                        settingsViewModel.previewFont(selectedFamily.id, weight.id)
                                    },
                                    label = {
                                        Text(
                                            text = weight.displayName,
                                            fontFamily = selectedFamily.fontFamily,
                                            fontWeight = weight.fontWeight,
                                            fontSize = 13.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isWeightSelected,
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Live Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE PREVIEW",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = selectedFamily.familyName,
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Hello Baroness ✨",
                            color = AppColors.textPrimary,
                            fontFamily = resolvedFontFamily,
                            fontWeight = resolvedFontWeight,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "See that's what I was talking about, I love this font!",
                            color = AppColors.textPrimary,
                            fontFamily = resolvedFontFamily,
                            fontWeight = resolvedFontWeight,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "1234567890 • abcdefghijklmnopqrstuvwxyz",
                            color = AppColors.textPrimary.copy(alpha = 0.8f),
                            fontFamily = resolvedFontFamily,
                            fontWeight = resolvedFontWeight,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Action Buttons Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // REVERT Button
                Button(
                    onClick = {
                        if (isCurrentlyActive) {
                            settingsViewModel.revertFontToDefault()
                            Toast.makeText(context, "Reverted to default font", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.revertFont()
                            Toast.makeText(context, "Reverted font preview", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) Color(0xFFFF453A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("REVERT", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }

                // APPLY Button
                Button(
                    onClick = {
                        if (isCurrentlyActive) {
                            Toast.makeText(context, "This font is already active", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.applyFont()
                            Toast.makeText(context, "Font applied successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) Color.White.copy(alpha = 0.15f) else accentColor,
                        contentColor = if (isCurrentlyActive) Color.White.copy(alpha = 0.4f) else Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("APPLY", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}
