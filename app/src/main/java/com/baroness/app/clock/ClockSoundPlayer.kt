package com.baroness.app.clock

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*

object ClockSoundPlayer {

    private var currentRingtone: Ringtone? = null
    private var alarmLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun playSound(context: Context, soundOption: String, isAlarm: Boolean, loopDurationMs: Long = 1500L) {
        stopSound()

        if (isAlarm && loopDurationMs > 5000L) {
            startAlarmLoop(context, soundOption, loopDurationMs)
            return
        }

        playSingleSound(context, soundOption, isAlarm, loopDurationMs)
    }

    private fun startAlarmLoop(context: Context, soundOption: String, durationMs: Long) {
        alarmLoopJob?.cancel()
        alarmLoopJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                playSingleSound(context, soundOption, isAlarm = true, durationMs = 2000L)
                delay(2200L)
            }
        }
    }

    private fun playSingleSound(context: Context, soundOption: String, isAlarm: Boolean, durationMs: Long) {
        try {
            when (soundOption) {
                "gentle_chime" -> playGentleChime()
                "digital_beep" -> playDigitalBeep()
                "soft_bell" -> playSoftBell()
                "marimba" -> playMarimba()
                "chime_chime" -> playClassicChime()
                else -> playSystemDefaultRingtone(context, isAlarm, durationMs)
            }
        } catch (e: Exception) {
            Log.e("ClockSoundPlayer", "Error playing sound option '$soundOption': ${e.message}")
            playSystemDefaultRingtone(context, isAlarm, durationMs)
        }
    }

    private fun playSystemDefaultRingtone(context: Context, isAlarm: Boolean, durationMs: Long) {
        try {
            val soundType = if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val uri = RingtoneManager.getDefaultUri(soundType)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            currentRingtone = ringtone
            ringtone?.play()
            Handler(Looper.getMainLooper()).postDelayed({
                if (ringtone != null && ringtone.isPlaying) {
                    ringtone.stop()
                }
            }, durationMs)
        } catch (e: Exception) {
            Log.e("ClockSoundPlayer", "Failed to play default ringtone: ${e.message}")
        }
    }

    private fun playGentleChime() {
        scope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                delay(300)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                delay(500)
                toneGen.release()
            } catch (e: Exception) {
                Log.e("ClockSoundPlayer", "Gentle chime error: ${e.message}")
            }
        }
    }

    private fun playDigitalBeep() {
        scope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                repeat(4) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 120)
                    delay(180)
                }
                toneGen.release()
            } catch (e: Exception) {
                Log.e("ClockSoundPlayer", "Digital beep error: ${e.message}")
            }
        }
    }

    private fun playClassicChime() {
        scope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 350)
                delay(400)
                toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 500)
                delay(550)
                toneGen.release()
            } catch (e: Exception) {
                Log.e("ClockSoundPlayer", "Classic chime error: ${e.message}")
            }
        }
    }

    private fun playSoftBell() {
        scope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 75)
                toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
                delay(350)
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                delay(450)
                toneGen.release()
            } catch (e: Exception) {
                Log.e("ClockSoundPlayer", "Soft bell error: ${e.message}")
            }
        }
    }

    private fun playMarimba() {
        scope.launch(Dispatchers.IO) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                val tones = listOf(
                    ToneGenerator.TONE_DTMF_1,
                    ToneGenerator.TONE_DTMF_3,
                    ToneGenerator.TONE_DTMF_5,
                    ToneGenerator.TONE_DTMF_8
                )
                for (t in tones) {
                    toneGen.startTone(t, 100)
                    delay(130)
                }
                toneGen.release()
            } catch (e: Exception) {
                Log.e("ClockSoundPlayer", "Marimba error: ${e.message}")
            }
        }
    }

    fun playVibration(context: Context, patternName: String) {
        val pattern = when (patternName) {
            "pulse" -> longArrayOf(0, 100, 100, 100, 100, 100, 100, 500)
            "heartbeat" -> longArrayOf(0, 120, 150, 280, 600, 120, 150, 280)
            else -> longArrayOf(0, 600, 300, 600, 300, 1000) // "wave"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("ClockSoundPlayer", "Error playing vibration: ${e.message}")
        }
    }

    fun stopSound() {
        alarmLoopJob?.cancel()
        try {
            currentRingtone?.stop()
            currentRingtone = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
