package com.baroness.app.screens.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.baroness.app.BuildConfig
import com.baroness.app.R
import com.baroness.app.phestydrop.data.PhestyDropInfo
import com.baroness.app.phestydrop.manager.PhestyDropManager
import com.baroness.app.phestydrop.ui.PhestyDropSheet
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.ui.theme.LocalBaronessTheme
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.formatRelativeTimestamp
import com.baroness.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val TAG = "AboutBaronessPage"
private const val LAST_CHECKED_KEY = "phestydrop_last_checked"

private enum class CheckButtonState {
    Idle,
    Checking,
    UpToDate,
    Error
}

private const val ABOUT_STORY =
    "No noise. No algorithms. No endless feeds. Just a clean, beautiful room where conversations, wishes, and memories live without distraction.\n\n" +
        "Two personas. One connection. Phesty and Baroness — each with their own voice, their own presence. This isn't a social network. It's a private dialogue, shared emotions, and a space to dream together.\n\n" +
        "And then there's Friday — a companion with her own personality, memory, and presence. Built to feel like a friend who actually remembers.\n\n" +
        "The interface isn't just functional — it's designed to feel calm, warm, and intentional. Every blur, every colour, every font choice is meant to create a vibe, not just a UI.\n\n" +
        "Baroness is a title, not a person. Strength, elegance, quiet power. The name was chosen because it reflects the energy of the person it was built for — someone who doesn't need to shout to be heard.\n\n" +
        "Naah, am just kidding. Baroness is her actual name lol :)"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AboutBaronessPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageManager = remember { StorageManager(context) }
    val typography = rememberChatTypography(settingsViewModel)
    val accentColor = MaterialTheme.colorScheme.primary

    var autoUpdateEnabled by remember { mutableStateOf(true) }
    var checkState by remember { mutableStateOf(CheckButtonState.Idle) }
    var activeDropInfo by remember { mutableStateOf<PhestyDropInfo?>(null) }
    var lastCheckedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        autoUpdateEnabled = storageManager.getBoolean("phestydrop_auto_update") ?: true
        lastCheckedAt = storageManager.getLong(LAST_CHECKED_KEY)
    }

    activeDropInfo?.let { drop ->
        PhestyDropSheet(
            dropInfo = drop,
            onDismiss = { activeDropInfo = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ABOUT BARONESS",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = "Baroness app icon",
                modifier = Modifier.size(80.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
            }

            AboutCard(accentColor) {
                Text(
                    text = "PHESTYDROP ENGINE",
                    fontFamily = AppFonts.Gamaamli,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}",
                    style = typography.meta,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = lastCheckedAt?.let { "Last checked: ${formatRelativeTimestamp(it)}" }
                        ?: "Never checked",
                    style = typography.meta,
                    color = Color.White.copy(alpha = 0.6f)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Download Drops",
                            style = typography.body,
                            color = Color.White
                        )
                        Text(
                            text = "Auto-fetch low & mandatory drops on start",
                            style = typography.meta,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { checked ->
                            autoUpdateEnabled = checked
                            scope.launch {
                                try {
                                    storageManager.saveBoolean("phestydrop_auto_update", checked)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to save auto-download preference", e)
                                    Toast.makeText(context, "Unable to save preference", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            checkState = CheckButtonState.Checking
                            try {
                                val drop = try {
                                    withContext(Dispatchers.IO) {
                                        PhestyDropManager.checkForDrop()
                                    }
                                } finally {
                                    lastCheckedAt = storageManager.getLong(LAST_CHECKED_KEY)
                                }
                                if (drop != null) {
                                    activeDropInfo = drop
                                    checkState = CheckButtonState.Idle
                                } else {
                                    checkState = CheckButtonState.UpToDate
                                    delay(1_000)
                                    checkState = CheckButtonState.Idle
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to check for PhestyDrop", e)
                                checkState = CheckButtonState.Error
                                delay(1_500)
                                checkState = CheckButtonState.Idle
                            }
                        }
                    },
                    enabled = checkState == CheckButtonState.Idle,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (checkState == CheckButtonState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                    } else {
                        val icon = when (checkState) {
                            CheckButtonState.Idle -> Icons.Default.Refresh
                            CheckButtonState.UpToDate -> Icons.Default.Check
                            CheckButtonState.Error -> Icons.Default.ErrorOutline
                            CheckButtonState.Checking -> Icons.Default.Refresh
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = when (checkState) {
                            CheckButtonState.Idle -> "Check for PhestyDrop"
                            CheckButtonState.Checking -> "Checking…"
                            CheckButtonState.UpToDate -> "Up to date"
                            CheckButtonState.Error -> "Check failed"
                        },
                        style = typography.meta,
                        color = LocalContentColor.current,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AboutCard(accentColor) {
                Text(
                    text = ABOUT_STORY,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )
            }

            AboutCard(accentColor) {
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

            val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
            Text(
                text = "Baroness ©$currentYear · Powered by PhestyDrop",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.35f),
                fontFamily = AppFonts.PlayfairDisplay,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AboutCard(
    accentColor: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
