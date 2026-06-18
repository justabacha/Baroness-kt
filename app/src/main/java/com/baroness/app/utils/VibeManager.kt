package com.baroness.app.utils

import android.content.Context
import android.location.Location
import com.baroness.app.data.SignatureLoops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.Serializable

object VibeManager {

    private val client = OkHttpClient()
    private const val WEATHER_API = "https://api.open-meteo.com/v1/forecast"
    private const val GEO_API = "https://api.bigdatacloud.net/data/reverse-geocode-client"

    private val greetingMap = mapOf(
        "morning" to mapOf(
            "Phesty" to listOf("hope your morning's starting easy.", "fresh start, fresh energy today.", "let's make today count, yeah?"),
            "Baroness" to listOf("hope the morning's treating you gently.", "new day, same glow.", "take it slow, you've got time.")
        ),
        "afternoon" to mapOf(
            "Phesty" to listOf("midday check, still in control?", "hope the day's moving your way.", "don't lose that momentum now."),
            "Baroness" to listOf("hope the day's been kind so far.", "still shining through the afternoon.", "just a little more to go.")
        ),
        "evening" to mapOf(
            "Phesty" to listOf("blud time to get night started.", "you made it through, take it in.", "slow it down, you've done enough."),
            "Baroness" to listOf("the evening's calm, just like you.", "time to relax, you've earned it.", "let the day fade easy.")
        )
    )

    fun getDynamicGreeting(persona: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            else -> "evening"
        }
        val options = greetingMap[timeOfDay]?.get(persona) ?: greetingMap[timeOfDay]?.get("Phesty") ?: listOf("Have a great day!")
        return options.random()
    }

    fun getFormattedDate(): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.US)
        return formatter.format(Date())
    }

    fun getCurrentTimeString(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        return formatter.format(Date())
    }

    fun getVibeOfTheDay(): com.baroness.app.models.VibeQuote {
        val launchDate = Calendar.getInstance().apply {
            set(2026, 3, 11) // April 11, 2026
        }.timeInMillis
        val today = System.currentTimeMillis()
        val daysSinceLaunch = ((today - launchDate) / (1000 * 60 * 60 * 24)).toInt()

        val shuffled = SignatureLoops.quotes.toMutableList()
        var seed = 2026
        for (i in shuffled.indices.reversed()) {
            seed = (seed * 9301 + 49297) % 233280
            val j = (seed / 233280.0 * (i + 1)).toInt()
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }
        val index = daysSinceLaunch % shuffled.size
        return shuffled[index]
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherData {
        return withContext(Dispatchers.IO) {
            try {
                // Reverse geocoding
                val geoUrl = "$GEO_API?latitude=$lat&longitude=$lon&localityLanguage=en"
                val geoRequest = Request.Builder().url(geoUrl).build()
                val geoResponse = client.newCall(geoRequest).execute()
                val geoJson = JSONObject(geoResponse.body?.string() ?: "{}")
                val city = geoJson.optString("city", "").takeIf { it.isNotEmpty() }
                    ?: geoJson.optString("locality", "Eldoret")

                // Weather with current endpoint (includes humidity)
                val weatherUrl = "$WEATHER_API?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m"
                val weatherRequest = Request.Builder().url(weatherUrl).build()
                val weatherResponse = client.newCall(weatherRequest).execute()
                val weatherJson = JSONObject(weatherResponse.body?.string() ?: "{}")
                val current = weatherJson.optJSONObject("current")
                if (current == null) throw Exception("No current weather data")

                val temp = current.optDouble("temperature_2m").toInt()
                val humidity = current.optInt("relative_humidity_2m", 0)

                val suggestion = when {
                    temp <= 18 -> "$city is cold, stay warm! ☕"
                    temp < 26 -> "$city is chill, enjoy the vibe. 🍃"
                    else -> "$city is heating up! Keep icy. 🧊"
                }
                WeatherData(temp, humidity, suggestion, city)
            } catch (e: Exception) {
                e.printStackTrace()
              WeatherData(22, 65, "Nairobi is chill, enjoy the vibe. 🍃", "Nairobi")
            }
        }
    }

    @Serializable
    data class WeatherData(
        val temp: Int,
        val humidity: Int,
        val suggestion: String,
        val city: String
    )
}