package com.baroness.app.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.BuildConfig
import com.baroness.app.phestydrop.data.PhestyDropInfo
import com.baroness.app.phestydrop.manager.PhestyDropManager
import com.baroness.app.phestydrop.ui.PhestyDropSheet
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.utils.StorageManager
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageManager = remember { StorageManager(context) }

    var isCheckingDrop by remember { mutableStateOf(false) }
    var activeDropInfo by remember { mutableStateOf<PhestyDropInfo?>(null) }
    var autoUpdateEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        autoUpdateEnabled = storageManager.getBoolean("phestydrop_auto_update") ?: true
    }

    // PhestyDrop Modal Sheet when an update is found
    activeDropInfo?.let { drop ->
        PhestyDropSheet(
            dropInfo = drop,
            onDismiss = { activeDropInfo = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "About Baroness",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )

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
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // PhestyDrop System Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PHESTYDROP ENGINE",
                            fontFamily = AppFonts.Gamaamli,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Auto-Update Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Download Drops",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Auto-fetch low & mandatory drops on start",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { checked ->
                            autoUpdateEnabled = checked
                            scope.launch {
                                storageManager.saveBoolean("phestydrop_auto_update", checked)
                            }
                        }
                    )
                }

                // Manual Check Action Button
                Button(
                    onClick = {
                        scope.launch {
                            isCheckingDrop = true
                            val drop = PhestyDropManager.checkForDrop()
                            isCheckingDrop = false
                            if (drop != null) {
                                activeDropInfo = drop
                            } else {
                                Toast.makeText(context, "Baroness is fully up to date!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isCheckingDrop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isCheckingDrop) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking for Drops...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for PhestyDrop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 4.dp)
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

        Text(
            text = "Built by Phestone with love. For just Baroness and Phesty.",
            fontFamily = AppFonts.PlayfairDisplay,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp
        )
    }
}
