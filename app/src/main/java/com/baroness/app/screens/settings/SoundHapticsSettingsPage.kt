package com.baroness.app.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baroness.app.clock.ClockSoundPlayer
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.voice.VoiceRegistry
import com.baroness.app.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundHapticsSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current

    // Voice State
    val voiceEnabled by settingsViewModel.voiceEnabled.collectAsStateWithLifecycle()
    val voiceId by settingsViewModel.voiceId.collectAsStateWithLifecycle()
    val voiceSpeed by settingsViewModel.voiceSpeed.collectAsStateWithLifecycle()
    val voicePitch by settingsViewModel.voicePitch.collectAsStateWithLifecycle()
    val usePersonaVoices by settingsViewModel.usePersonaVoices.collectAsStateWithLifecycle()
    val personaName by settingsViewModel.personaName.collectAsStateWithLifecycle()
    val voiceState by settingsViewModel.voiceState.collectAsStateWithLifecycle()

    // Clock State
    val clockVoiceAnnounce by settingsViewModel.clockVoiceAnnounce.collectAsStateWithLifecycle()
    val alarmSound by settingsViewModel.alarmSoundOption.collectAsStateWithLifecycle()
    val timerChime by settingsViewModel.timerChimeOption.collectAsStateWithLifecycle()
    val vibrationPattern by settingsViewModel.alarmVibrationPattern.collectAsStateWithLifecycle()

    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    DisposableEffect(Unit) {
        onDispose {
            ClockSoundPlayer.stopSound()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SOUND & HAPTICS",
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
            // Voice & TTS Card
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
                            text = "AVIS", //Abacha Voice Interface System (AVIS)
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )

                        if (voiceEnabled) {
                            IconButton(
                                onClick = {
                                    if (voiceState == VoiceState.IDLE) settingsViewModel.previewVoice()
                                    else settingsViewModel.stopVoice()
                                },
                                enabled = voiceState != VoiceState.LOADING,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                when (voiceState) {
                                    VoiceState.LOADING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    VoiceState.PLAYING -> {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop Voice Preview",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    VoiceState.IDLE -> {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Preview Voice",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Master Voice Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Announcements",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Enable spoken announcements across the application",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = { settingsViewModel.setVoiceEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    if (voiceEnabled) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        // Smart Persona Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Optimize for $personaName",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically select optimal voice signature for $personaName",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = usePersonaVoices,
                                onCheckedChange = { settingsViewModel.setUsePersonaVoices(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }

                        if (!usePersonaVoices) {
                            val selectedVoice = remember(voiceId) { VoiceRegistry.resolve(voiceId) }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "SELECT VOICE",
                                    color = Color.White,
                                    fontFamily = AppFonts.PlayfairDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VoiceRegistry.voices.forEach { voice ->
                                        val isSelected = voice.id == voiceId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { settingsViewModel.setVoiceId(voice.id) },
                                            label = {
                                                Text(
                                                    text = voice.name,
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
                                                selected = isSelected,
                                                borderColor = Color.White.copy(alpha = 0.2f),
                                                selectedBorderColor = accentColor
                                            )
                                        )
                                    }
                                }
                            }

                            val isMurf = selectedVoice.provider == "murf"

                            // Speed Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Speed",
                                        color = if (isMurf) Color.White else Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "%.1fx".format(voiceSpeed),
                                        color = if (isMurf) accentColor else Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = voiceSpeed,
                                    onValueChange = { settingsViewModel.setVoiceSpeed(it) },
                                    valueRange = 0.5f..2.0f,
                                    enabled = isMurf,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                        disabledThumbColor = Color.Gray,
                                        disabledActiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            // Pitch Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Pitch",
                                        color = if (isMurf) Color.White else Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "%.1fx".format(voicePitch),
                                        color = if (isMurf) accentColor else Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = voicePitch,
                                    onValueChange = { settingsViewModel.setVoicePitch(it) },
                                    valueRange = 0.5f..1.5f,
                                    enabled = isMurf,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                        disabledThumbColor = Color.Gray,
                                        disabledActiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            if (!isMurf) {
                                Text(
                                    text = "Speed and pitch adjustments are available for Murf voices.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Voice settings are automatically optimized for $personaName.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Clock & Alarm Sounds Card
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
                        text = "CLOCK & REMINDER SOUNDS",
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )

                    // Spoken Clock Readout Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Friday Voice Readout",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Friday speaks reminders out loud after the chime",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = clockVoiceAnnounce,
                            onCheckedChange = { settingsViewModel.setClockVoiceAnnounce(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Alarm Sound Option
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ALARM SOUND",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "default_alarm" to "System Default",
                                "gentle_chime" to "Gentle Chime",
                                "digital_beep" to "Digital Beep"
                            ).forEach { (optionId, label) ->
                                val isSelected = alarmSound == optionId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        settingsViewModel.setAlarmSoundOption(optionId)
                                        ClockSoundPlayer.playSound(context, optionId, isAlarm = true)
                                    },
                                    label = { Text(text = label, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = accentColor
                                    )
                                )
                            }
                        }
                    }

                    // Timer Chime Option
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "TIMER CHIME",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "chime_chime" to "Classic Chime",
                                "soft_bell" to "Soft Bell",
                                "marimba" to "Marimba"
                            ).forEach { (optionId, label) ->
                                val isSelected = timerChime == optionId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        settingsViewModel.setTimerChimeOption(optionId)
                                        ClockSoundPlayer.playSound(context, optionId, isAlarm = false)
                                    },
                                    label = { Text(text = label, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Vibration & Haptics Card
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
                        text = "VIBRATION & HAPTICS",
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ALARM & TIMER VIBRATION",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "wave" to "Wave",
                                "pulse" to "Pulse",
                                "heartbeat" to "Heartbeat"
                            ).forEach { (patternId, label) ->
                                val isSelected = vibrationPattern == patternId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        settingsViewModel.setAlarmVibrationPattern(patternId)
                                        ClockSoundPlayer.playVibration(context, patternId)
                                    },
                                    label = { Text(text = label, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
