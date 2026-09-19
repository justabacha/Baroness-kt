package com.baroness.app.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun DrawerSoundHaptics(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    viewModel: SettingsViewModel,
    hazeState: HazeState
) {
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val voiceProvider by viewModel.voiceProvider.collectAsState()
    val voiceId by viewModel.voiceId.collectAsState()
    val voiceSpeed by viewModel.voiceSpeed.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val directorNote by viewModel.directorNote.collectAsState()

    GlassCategoryBox(
        title = "AVIS - SOUND & HAPTICS",
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
            // Master Voice Toggle and Preview Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    VoiceSettingToggle(
                        label = "Voice Announcements",
                        checked = voiceEnabled,
                        onCheckedChange = { viewModel.setVoiceEnabled(it) }
                    )
                }
                
                if (voiceEnabled) {
                    IconButton(
                        onClick = { viewModel.previewVoice() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview Voice")
                    }
                }
            }

            if (voiceEnabled) {
                Divider(color = Color.White.copy(alpha = 0.1f))

                // Provider Selection
                Column {
                    Text(
                        text = "Voice Provider",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceProviderOption(
                            label = "Deepgram",
                            selected = voiceProvider == "deepgram",
                            onClick = { viewModel.setVoiceProvider("deepgram") },
                            modifier = Modifier.weight(1f)
                        )
                        VoiceProviderOption(
                            label = "Murf AI",
                            selected = voiceProvider == "murf",
                            onClick = { viewModel.setVoiceProvider("murf") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Voice Selection Row
                val availableVoices = remember(voiceProvider) {
                    when (voiceProvider) {
                        "deepgram" -> listOf(
                            "aura-asteria-en" to "Asteria (F)",
                            "aura-luna-en" to "Luna (F)",
                            "aura-stella-en" to "Stella (F)",
                            "aura-athena-en" to "Athena (F)",
                            "aura-hera-en" to "Hera (F)",
                            "aura-orion-en" to "Orion (M)",
                            "aura-arcas-en" to "Arcas (M)",
                            "aura-perseus-en" to "Perseus (M)",
                            "aura-angus-en" to "Angus (M)",
                            "aura-orpheus-en" to "Orpheus (M)",
                            "aura-helios-en" to "Helios (M)",
                            "aura-zeus-en" to "Zeus (M)"
                        )
                        "murf" -> listOf("en-US-marcus" to "Marcus (M)")
                        "edge" -> listOf("en-US-jenny" to "Jenny (F)")
                        else -> emptyList()
                    }
                }

                if (availableVoices.size > 1) {
                    Column {
                        Text(
                            text = "Select Voice",
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
                            availableVoices.forEach { (id, label) ->
                                VoiceProviderOption(
                                    label = label,
                                    selected = voiceId == id,
                                    onClick = { viewModel.setVoiceId(id) }
                                )
                            }
                        }
                    }
                }

                // Speed Slider
                VoiceSlider(
                    label = "Speed",
                    value = voiceSpeed,
                    range = 0.5f..2.0f,
                    onValueChange = { viewModel.setVoiceSpeed(it) }
                )

                // Pitch Slider
                VoiceSlider(
                    label = "Pitch",
                    value = voicePitch,
                    range = 0.5f..1.5f,
                    onValueChange = { viewModel.setVoicePitch(it) }
                )
            }
        }
    }
}

@Composable
fun VoiceSettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Black.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun VoiceProviderOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = Color.White
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.5f else 0.1f)),
        contentPadding = PaddingValues(8.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
fun VoiceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(text = "%.1fx".format(value), color = Color.White, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}
