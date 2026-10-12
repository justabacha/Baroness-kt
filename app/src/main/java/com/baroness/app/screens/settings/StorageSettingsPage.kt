package com.baroness.app.screens.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import coil.annotation.ExperimentalCoilApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.imageLoader
import com.baroness.app.api.ChatApi
import com.baroness.app.repository.BackupManager
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.ui.theme.LocalBaronessTheme
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.data.AvatarRepository
import com.baroness.app.utils.UserSessionManager
import com.baroness.app.utils.formatBytes
import com.baroness.app.utils.totalSizeBytes
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.voice.VoiceCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "StorageSettingsPage"

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
fun StorageSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typography = rememberChatTypography(settingsViewModel)
    val theme = LocalBaronessTheme.current
    val personaId = remember { UserSessionManager(context).currentUserId }
    val avatarRepository = remember { AvatarRepository(context) }
    val backupManager = remember { BackupManager(context) }

    var roomDbSize by remember { mutableStateOf<Long?>(null) }
    var avatarCacheSize by remember { mutableStateOf<Long?>(null) }
    var imageCacheSize by remember { mutableStateOf<Long?>(null) }
    var voiceCacheSize by remember { mutableStateOf<Long?>(null) }
    var lastBackupAt by remember { mutableStateOf<String?>(null) }
    var backupLogLoaded by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }

    fun refreshSizes() {
        scope.launch {
            launch {
                roomDbSize = withContext(Dispatchers.IO) {
                    context.getDatabasePath("baroness_wishlist.db").length()
                }
            }
            launch {
                avatarCacheSize = withContext(Dispatchers.IO) {
                    File(context.filesDir, "user_avatars").totalSizeBytes()
                }
            }
            launch {
                imageCacheSize = withContext(Dispatchers.IO) {
                    File(context.filesDir, "image_cache").totalSizeBytes()
                }
            }
            launch {
                voiceCacheSize = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "voice_cache").totalSizeBytes()
                }
            }
        }
    }

    suspend fun refreshBackupLog() {
        try {
            val log = withContext(Dispatchers.IO) {
                ChatApi.fetchBackupLog(personaId)
            }
            lastBackupAt = log?.lastBackupAt
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh backup log", e)
            lastBackupAt = null
        } finally {
            backupLogLoaded = true
        }
    }

    fun clearImageAndAvatarCache() {
        if (isClearing) return
        isClearing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.imageLoader.diskCache?.clear()
                    context.imageLoader.memoryCache?.clear()
                    avatarRepository.clearCache()
                }
                refreshSizes()
                Toast.makeText(context, "Image and avatar cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear image and avatar cache", e)
                Toast.makeText(context, "Unable to clear image and avatar cache", Toast.LENGTH_SHORT).show()
            } finally {
                isClearing = false
            }
        }
    }

    fun clearVoiceTempCache() {
        if (isClearing) return
        isClearing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VoiceCacheManager(context).clearTempCache()
                }
                refreshSizes()
                Toast.makeText(context, "Voice temp cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear voice temp cache", e)
                Toast.makeText(context, "Unable to clear voice temp cache", Toast.LENGTH_SHORT).show()
            } finally {
                isClearing = false
            }
        }
    }

    fun clearAllCaches() {
        if (isClearing) return
        isClearing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.imageLoader.diskCache?.clear()
                    context.imageLoader.memoryCache?.clear()
                    avatarRepository.clearCache()
                    VoiceCacheManager(context).clearTempCache()
                }
                refreshSizes()
                Toast.makeText(context, "All temporary caches cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all caches", e)
                Toast.makeText(context, "Unable to clear all caches", Toast.LENGTH_SHORT).show()
            } finally {
                isClearing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
        refreshBackupLog()
    }

    val totalSize = if (
        roomDbSize != null &&
        avatarCacheSize != null &&
        imageCacheSize != null &&
        voiceCacheSize != null
    ) {
        roomDbSize!! + avatarCacheSize!! + imageCacheSize!! + voiceCacheSize!!
    } else {
        null
    }
    val operationInProgress = isBackingUp || isRestoring

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "STORAGE & DATA",
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
            StorageCard(title = "Storage Overview", accent = theme.glowColor, typography = typography) {
                StorageSizeRow("Database", roomDbSize, typography)
                StorageSizeRow("Avatar cache", avatarCacheSize, typography)
                StorageSizeRow("Image cache", imageCacheSize, typography)
                StorageSizeRow("Voice cache", voiceCacheSize, typography)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", style = typography.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = totalSize?.let(::formatBytes) ?: "Calculating…",
                        style = typography.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            StorageCard(title = "Clear Caches", accent = theme.glowColor, typography = typography) {
                CacheActionRow(
                    label = "Clear image & avatar cache",
                    size = combinedCacheSize(avatarCacheSize, imageCacheSize),
                    enabled = !isClearing,
                    typography = typography,
                    onClick = ::clearImageAndAvatarCache
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                CacheActionRow(
                    label = "Clear voice temp cache",
                    size = voiceCacheSize?.let(::formatBytes),
                    enabled = !isClearing,
                    typography = typography,
                    onClick = ::clearVoiceTempCache
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                CacheActionRow(
                    label = "Clear all caches",
                    size = totalCacheSize(avatarCacheSize, imageCacheSize, voiceCacheSize),
                    enabled = !isClearing,
                    typography = typography,
                    onClick = { showClearAllDialog = true }
                )
            }

            StorageCard(title = "Backup & Restore", accent = theme.glowColor, typography = typography) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chat Backup", style = typography.title, color = Color.White)
                        Text(
                            text = when {
                                isBackingUp -> "Backing up…"
                                !backupLogLoaded -> "Checking…"
                                lastBackupAt == null -> "No backup yet"
                                else -> "Last backed up: ${formatBackupTimestamp(lastBackupAt!!)}"
                            },
                            style = typography.subtitle,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                    ActionButton(
                        text = "Back up now",
                        enabled = !operationInProgress,
                        accent = theme.glowColor,
                        typography = typography
                    ) {
                        if (!operationInProgress) {
                            isBackingUp = true
                            scope.launch {
                                try {
                                    val succeeded = withContext(Dispatchers.IO) {
                                        backupManager.createBackup(personaId)
                                    }
                                    if (succeeded) {
                                        refreshBackupLog()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Backup failed — check your connection.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Backup operation failed", e)
                                    Toast.makeText(
                                        context,
                                        "Backup failed — check your connection.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isBackingUp = false
                                }
                            }
                        }
                    }
                }
                if (isBackingUp) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .height(4.dp),
                        color = theme.glowColor,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restore Chat History", style = typography.title, color = Color.White)
                        Text(
                            text = "Merges cloud backup with your local messages.",
                            style = typography.subtitle,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        if (isRestoring) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .height(4.dp),
                                color = theme.glowColor,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                    }
                    ActionButton(
                        text = if (isRestoring) "Restoring…" else "Restore",
                        enabled = !operationInProgress,
                        accent = theme.glowColor,
                        typography = typography
                    ) {
                        if (!operationInProgress) showRestoreDialog = true
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(
                    text = "Clear All Caches?",
                    color = Color.White,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will clear temporary image, avatar, and voice caches. Your account, messages, and settings will remain intact.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        clearAllCaches()
                    }
                ) {
                    Text("CLEAR", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Text(
                    text = "Restore Chat History?",
                    color = Color.White,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will merge your cloud backup into your local chats. Messages you deleted after your last backup will reappear. Your current chats will not be removed.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        isRestoring = true
                        scope.launch {
                            try {
                                val succeeded = withContext(Dispatchers.IO) {
                                    backupManager.restoreBackup(personaId)
                                }
                                Toast.makeText(
                                    context,
                                    if (succeeded) "Restore complete." else "Restore failed — check your connection.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Restore operation failed", e)
                                Toast.makeText(
                                    context,
                                    "Restore failed — check your connection.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isRestoring = false
                            }
                        }
                    }
                ) {
                    Text("Restore", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun StorageCard(
    title: String,
    accent: Color,
    typography: com.baroness.app.ui.theme.ChatTypography,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Text(
                    text = title,
                    style = typography.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                content()
            }
        )
    }
}

@Composable
private fun StorageSizeRow(
    label: String,
    bytes: Long?,
    typography: com.baroness.app.ui.theme.ChatTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = typography.body, color = Color.White.copy(alpha = 0.8f))
        Text(
            text = bytes?.let(::formatBytes) ?: "Calculating…",
            style = typography.body,
            color = Color.White
        )
    }
}

@Composable
private fun CacheActionRow(
    label: String,
    size: String?,
    enabled: Boolean,
    typography: com.baroness.app.ui.theme.ChatTypography,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.body, color = Color.White)
            Text(
                text = size ?: "Calculating…",
                style = typography.meta,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Text("Clear", color = LocalBaronessTheme.current.glowColor)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    accent: Color,
    typography: com.baroness.app.ui.theme.ChatTypography,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent)
    ) {
        Text(text, style = typography.meta, color = Color.White)
    }
}

private fun totalCacheSize(
    avatarCacheSize: Long?,
    imageCacheSize: Long?,
    voiceCacheSize: Long?
): String? {
    if (avatarCacheSize == null || imageCacheSize == null || voiceCacheSize == null) return null
    return formatBytes(avatarCacheSize + imageCacheSize + voiceCacheSize)
}

private fun combinedCacheSize(first: Long?, second: Long?): String? {
    if (first == null || second == null) return null
    return formatBytes(first + second)
}

private fun formatBackupTimestamp(value: String): String {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )
    val parsed = patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull()
    } ?: return value

    val now = Calendar.getInstance()
    val backup = Calendar.getInstance().apply { time = parsed }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return when {
        now.isSameDay(backup) -> "Today ${timeFormat.format(parsed)}"
        now.isYesterday(backup) -> "Yesterday ${timeFormat.format(parsed)}"
        else -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(parsed)
    }
}

private fun Calendar.isSameDay(other: Calendar): Boolean =
    get(Calendar.ERA) == other.get(Calendar.ERA) &&
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

private fun Calendar.isYesterday(other: Calendar): Boolean {
    val yesterday = (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return yesterday.isSameDay(other)
}
