package com.baroness.app.voice

enum class AudioFormat {
    MP3, WAV
}

data class VoiceConfig(
    val voiceId: String,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: String = "en-US",
    val provider: String = "deepgram",
    val directorNote: String? = ""
)

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

data class VoiceSettings(
    val enabled: Boolean = true,
    val provider: String = "deepgram",
    val globalVoiceId: String = "aura-asteria-en",
    val globalSpeed: Float = 1.0f,
    val globalPitch: Float = 1.0f,
    val directorNote: String = ""
)

data class VoiceContext(
    val config: VoiceConfig
)
