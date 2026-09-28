package com.baroness.app.voice

import kotlin.math.roundToInt

enum class AudioFormat {
    MP3, WAV
}

data class VoiceConfig(
    val voiceId: String,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: String = "en-US",
    val directorNote: String? = ""
) {
    val voice: VoiceOption
        get() = VoiceRegistry.resolve(voiceId)
    val provider: String
        get() = voice.provider
    val murfRate: Int
        get() = toMurfOffset(speed, min = 0.5f, max = 2.0f)
    val murfPitch: Int
        get() = toMurfOffset(pitch, min = 0.5f, max = 1.5f)
}

private fun toMurfOffset(value: Float, min: Float, max: Float): Int {
    val clamped = value.coerceIn(min, max)
    // Map the existing controls around neutral 1.0 to Murf's signed API offsets.
    val offset = if (clamped < 1.0f) {
        (clamped - 1.0f) / (1.0f - min) * 50.0f
    } else {
        (clamped - 1.0f) / (max - 1.0f) * 50.0f
    }
    return offset.roundToInt()
}

data class AudioResult(
    val audioData: ByteArray,
    val format: AudioFormat,
    val duration: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioResult
        if (!audioData.contentEquals(other.audioData)) return false
        if (format != other.format) return false
        if (duration != other.duration) return false
        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + duration.hashCode()
        return result
    }
}

data class VoiceOption(
    val id: String,
    val name: String,
    val gender: String,
    val provider: String
)

object VoiceRegistry {
    val voices = listOf(
        VoiceOption("aura-asteria-en", "Asteria (F)", "Female", "deepgram"),
        VoiceOption("aura-luna-en", "Luna (F)", "Female", "deepgram"),
        VoiceOption("aura-stella-en", "Stella (F)", "Female", "deepgram"),
        VoiceOption("aura-athena-en", "Athena (F)", "Female", "deepgram"),
        VoiceOption("aura-hera-en", "Hera (F)", "Female", "deepgram"),
        VoiceOption("aura-orion-en", "Orion (M)", "Male", "deepgram"),
        VoiceOption("aura-arcas-en", "Arcas (M)", "Male", "deepgram"),
        VoiceOption("aura-perseus-en", "Perseus (M)", "Male", "deepgram"),
        VoiceOption("aura-angus-en", "Angus (M)", "Male", "deepgram"),
        VoiceOption("aura-orpheus-en", "Orpheus (M)", "Male", "deepgram"),
        VoiceOption("aura-helios-en", "Helios (M)", "Male", "deepgram"),
        VoiceOption("aura-zeus-en", "Zeus (M)", "Male", "deepgram"),
        VoiceOption("en-US-marcus", "Marcus (M)", "Male", "murf")
    )

    val defaultVoice: VoiceOption = voices.first()

    fun find(id: String?): VoiceOption? = voices.firstOrNull { it.id == id }

    fun resolve(id: String?): VoiceOption = find(id) ?: defaultVoice

    fun sanitize(id: String?): String = resolve(id).id
}

data class VoiceSettings(
    val enabled: Boolean = true,
    val globalVoiceId: String = "aura-asteria-en",
    val globalSpeed: Float = 1.0f,
    val globalPitch: Float = 1.0f,
    val directorNote: String = ""
)

data class VoiceContext(
    val config: VoiceConfig
)
