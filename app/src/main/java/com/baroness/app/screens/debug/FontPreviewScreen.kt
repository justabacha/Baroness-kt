// TEMPORARY SCREEN — safe to delete after quote authoring is complete.
// Removal checklist:
//   1. Delete this file.
//   2. Remove the `composable("debug/fonts")` block in MainActivity.kt.
//   3. Remove the temporary TextButton in AboutBaronessPage.kt.
//   The QuoteMarkup.allFonts() accessor may stay or be deleted — it has no side effects.

package com.baroness.app.screens.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.baroness.app.data.QuoteMarkup
import com.baroness.app.ui.theme.AppFonts

private val DISPLAY_NAMES = mapOf(
    "yuyu" to "Yuyu",
    "lifesavers" to "Lifesavers",
    "robotomono" to "Roboto Mono",
    "gamaamli" to "Gamaamli",
    "matemasie" to "Matemasie",
    "dancing" to "Dancing Script",
    "marck" to "Marck Script",
    "playfair" to "Playfair Display",
    "kaushan" to "Kaushan Script",
    "marker" to "Permanent Marker",
    "shadows" to "Shadows Into Light",
    "xmas_bundle" to "Christmas Bundle",
    "xmas_dariona" to "Christmas Dariona",
    "cookie" to "Cookie Monster",
    "crayon" to "Crayon Hand",
    "evil" to "Evil Scratch",
    "filx" to "Filx Girl",
    "god" to "God of War",
    "squids" to "Game of Squids",
    "graveyard" to "Graveyard Crimes",
    "birthday" to "Happy Birthday",
    "harukaze" to "Harukaze",
    "hello_xmas" to "Hello Christmas",
    "hoshiko" to "Hoshiko Satsuki",
    "jealousy" to "Jealousy",
    "klomang" to "Klomang Brush",
    "skate" to "Power Skate",
    "rain" to "Racing in the Rain",
    "sakelar" to "Sakelar",
    "shareit" to "Shareit",
    "asia" to "Spell of Asia",
    "spookery" to "Spookery",
    "sprucken" to "Sprucken",
    "rules" to "The Rules of the Game",
    "trash" to "Trash Secret"
)

private data class FontPreviewItem(
    val tag: String,
    val displayName: String,
    val fontFamily: FontFamily,
    val sizeScale: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPreviewScreen(
    navController: NavController
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    val fontItems = remember {
        val registry = QuoteMarkup.allFonts()
        DISPLAY_NAMES.mapNotNull { (tag, name) ->
            registry[tag]?.let { font ->
                FontPreviewItem(
                    tag = tag,
                    displayName = name,
                    fontFamily = font.family,
                    sizeScale = font.sizeScale
                )
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FONT PREVIEW",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(fontItems, key = { it.tag }) { item ->
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.displayName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "<${item.tag}>",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            if (item.sizeScale != 1.0f) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, accentColor)
                                ) {
                                    Text(
                                        text = "scale: ${item.sizeScale}x",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Text(
                            text = item.displayName,
                            fontFamily = item.fontFamily,
                            fontSize = (20 * item.sizeScale).sp,
                            color = Color.White
                        )

                        Text(
                            text = "I still wish for you at 11:11",
                            fontFamily = item.fontFamily,
                            fontSize = (16 * item.sizeScale).sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
