package com.baroness.app.components

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun DrawerClock(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    viewModel: SettingsViewModel,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val voiceAnnounce by viewModel.clockVoiceAnnounce.collectAsState()
    val alarmSound by viewModel.alarmSoundOption.collectAsState()
    val timerChime by viewModel.timerChimeOption.collectAsState()
    val vibrationPattern by viewModel.alarmVibrationPattern.collectAsState()

    GlassCategoryBox(
        title = "CLOCK & REMINDERS",
        isExpanded = isExpanded,
        onExpand = onToggle,
        hazeState = hazeState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Voice Announcement Toggle + Test Button
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Friday Voice Readout",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Friday speaks reminders out loud after the chime",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = voiceAnnounce,
                        onCheckedChange = { viewModel.setClockVoiceAnnounce(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.White.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Black.copy(alpha = 0.2f)
                        )
                    )
                }

                // Test Voice Button
                if (voiceAnnounce) {
                    OutlinedButton(
                        onClick = {
                            viewModel.previewVoice()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(text = "🔊 Test Voice Announcement", fontSize = 11.sp)
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Alarm Ringtone Selection
            Column {
                Text(
                    text = "Alarm Sound",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockOptionChip(
                        label = "System Default",
                        selected = alarmSound == "default_alarm",
                        onClick = {
                            viewModel.setAlarmSoundOption("default_alarm")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "default_alarm", isAlarm = true)
                        }
                    )
                    ClockOptionChip(
                        label = "Gentle Chime",
                        selected = alarmSound == "gentle_chime",
                        onClick = {
                            viewModel.setAlarmSoundOption("gentle_chime")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "gentle_chime", isAlarm = true)
                        }
                    )
                    ClockOptionChip(
                        label = "Digital Beep",
                        selected = alarmSound == "digital_beep",
                        onClick = {
                            viewModel.setAlarmSoundOption("digital_beep")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "digital_beep", isAlarm = true)
                        }
                    )
                }
            }

            // Timer Chime Selection
            Column {
                Text(
                    text = "Timer Chime",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockOptionChip(
                        label = "Classic Chime",
                        selected = timerChime == "chime_chime",
                        onClick = {
                            viewModel.setTimerChimeOption("chime_chime")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "chime_chime", isAlarm = false)
                        }
                    )
                    ClockOptionChip(
                        label = "Soft Bell",
                        selected = timerChime == "soft_bell",
                        onClick = {
                            viewModel.setTimerChimeOption("soft_bell")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "soft_bell", isAlarm = false)
                        }
                    )
                    ClockOptionChip(
                        label = "Marimba",
                        selected = timerChime == "marimba",
                        onClick = {
                            viewModel.setTimerChimeOption("marimba")
                            com.baroness.app.clock.ClockSoundPlayer.playSound(context, "marimba", isAlarm = false)
                        }
                    )
                }
            }

            // Vibration Pattern Selection
            Column {
                Text(
                    text = "Vibration Pattern",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockOptionChip(
                        label = "Wave",
                        selected = vibrationPattern == "wave",
                        onClick = {
                            viewModel.setAlarmVibrationPattern("wave")
                            com.baroness.app.clock.ClockSoundPlayer.playVibration(context, "wave")
                        }
                    )
                    ClockOptionChip(
                        label = "Pulse",
                        selected = vibrationPattern == "pulse",
                        onClick = {
                            viewModel.setAlarmVibrationPattern("pulse")
                            com.baroness.app.clock.ClockSoundPlayer.playVibration(context, "pulse")
                        }
                    )
                    ClockOptionChip(
                        label = "Heartbeat",
                        selected = vibrationPattern == "heartbeat",
                        onClick = {
                            viewModel.setAlarmVibrationPattern("heartbeat")
                            com.baroness.app.clock.ClockSoundPlayer.playVibration(context, "heartbeat")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClockOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = Color.White
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (selected) 0.5f else 0.1f)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}
