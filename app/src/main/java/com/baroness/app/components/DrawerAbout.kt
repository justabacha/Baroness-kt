package com.baroness.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.ui.theme.AppFonts
import dev.chrisbanes.haze.HazeState

@Composable
fun DrawerAbout(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    hazeState: HazeState
) {
    GlassCategoryBox(
        title = "ABOUT",
        isExpanded = isExpanded,
        onExpand = onToggle,
        hazeState = hazeState
    ) {
        AboutContent()
    }
}

@Composable
fun AboutContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App name / logo area
        Text(
            text = "About Baroness",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        // Tagline
        Text(
            text = "A private universe for two.",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
        
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // Main text
        Text(
            text = "No noise. No algorithms. No endless feeds. Just a clean, beautiful room where conversations, wishes, and memories live without distraction.\n\n" +
                   "Two personas. One connection. Phesty and Baroness — each with their own voice, their own presence. This isn't a social network. It's a private dialogue, shared emotions, and a space to dream together.\n\n" +
                   "And then there's Friday — a companion with her own personality, memory, and presence. Built to feel like a friend who actually remembers.\n\n" +
                   "The interface isn't just functional — it's designed to feel calm, warm, and intentional. Every blur, every colour, every font choice is meant to create a vibe, not just a UI.\n\n" +
                   "Baroness is a title, not a person. Strength, elegance, quiet power. The name was chosen because it reflects the energy of the person it was built for — someone who doesn't need to shout to be heard.\n\n" +
                   "Naah, am just kidding. Baroness is her actual name lol :)",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        // Closing
        Text(
            text = "Built by Phestone with love. For just Phesty and Baroness.",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp
        )
    }
}
