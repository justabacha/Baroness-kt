package com.baroness.app.viewmodels

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.utils.LocationHelper
import com.baroness.app.models.UserProfile
import com.baroness.app.models.VibeQuote
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.VibeManager
import com.baroness.app.data.QuoteRepository
import com.baroness.app.data.AvatarRepository
import com.baroness.app.voice.VoiceCenter
import com.baroness.app.voice.VoiceConfig
import com.baroness.app.voice.VoiceContext
import com.baroness.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = StorageManager(getApplication())
    private val settingsRepository = SettingsRepository(getApplication())
    private val json = Json { ignoreUnknownKeys = true }
    private val voiceCenter = VoiceCenter(getApplication())
    private val locationHelper = LocationHelper(getApplication())
    private val quoteRepository = QuoteRepository(getApplication())
    private val avatarRepository = AvatarRepository(getApplication())

    companion object {
        private const val ANNOUNCEMENT_COOLDOWN_MS = 40 * 60 * 1000L // 40 minutes
        private const val KEY_LAST_ANNOUNCEMENT_TIME = "last_announcement_time"
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _todayDate = MutableStateFlow("")
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    private val _vibe = MutableStateFlow<VibeQuote?>(null)
    val vibe: StateFlow<VibeQuote?> = _vibe.asStateFlow()

    private val _weather = MutableStateFlow<VibeManager.WeatherData?>(null)
    val weather: StateFlow<VibeManager.WeatherData?> = _weather.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    init {
        loadInitialData()
        startTimeUpdate()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val profileJson = storage.getString("userProfile")
            val profile = profileJson?.let { json.decodeFromString<UserProfile>(it) }
            val updatedProfile = if (profile != null && profile.avatar != null) {
                val localAvatarPath = avatarRepository.getCachedAvatar(profile.avatar)
                profile.copy(avatar = localAvatarPath ?: profile.avatar)
            } else {
                profile
            }
            _userProfile.value = updatedProfile

            val cachedWeather = loadCachedWeather()
            if (cachedWeather != null) {
                _weather.value = cachedWeather.data
            }

            val cachedVibeJson = storage.getString("quote_data")
            val cachedVibe = cachedVibeJson?.let {
                try { json.decodeFromString<VibeQuote>(it) } catch (_: Exception) { null }
            }

            if (cachedVibe != null) {
                _vibe.value = cachedVibe
                _isInitialLoading.value = false
                _todayDate.value = VibeManager.getFormattedDate()
                _greeting.value = VibeManager.getDynamicGreeting(profile?.persona ?: "Phesty")
                triggerAnnouncement(profile, _weather.value?.suggestion, isManual = false)
            }

            launch {
                val todaysVibe = quoteRepository.getTodayQuote(forceRefresh = false)
                _vibe.value = todaysVibe

                _todayDate.value = VibeManager.getFormattedDate()
                _greeting.value = VibeManager.getDynamicGreeting(profile?.persona ?: "Phesty")

                if (_isInitialLoading.value) {
                    _isInitialLoading.value = false
                    triggerAnnouncement(profile, _weather.value?.suggestion, isManual = false)
                }

                if (cachedWeather == null || System.currentTimeMillis() - cachedWeather.timestamp >= 10 * 60 * 1000) {
                    if (isOnline()) {
                        refreshWeatherInBackground()
                    }
                }
            }
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun reloadProfile() {
        viewModelScope.launch {
            val profileJson = storage.getString("userProfile")
            val profile = profileJson?.let { json.decodeFromString<UserProfile>(it) }
            val updatedProfile = if (profile != null && profile.avatar != null) {
                val localPath = avatarRepository.getCachedAvatar(profile.avatar)
                profile.copy(avatar = localPath ?: profile.avatar)
            } else {
                profile
            }
            _userProfile.value = updatedProfile
        }
    }

    private suspend fun loadCachedWeather(): CachedWeather? {
        return withContext(Dispatchers.IO) {
            val jsonStr = storage.getString("dashboard_weather_cache")
            jsonStr?.let { json.decodeFromString<CachedWeather>(it) }
        }
    }

    private suspend fun saveCachedWeather(weather: VibeManager.WeatherData) {
        withContext(Dispatchers.IO) {
            val cached = CachedWeather(weather, System.currentTimeMillis())
            storage.saveString("dashboard_weather_cache", json.encodeToString(cached))
        }
    }

    private fun refreshWeatherInBackground() {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation()
            val (lat, lon) = if (location != null) {
                location.latitude to location.longitude
            } else {
                -1.2864 to 36.8172
            }
            val freshWeather = VibeManager.fetchWeather(lat, lon)
            _weather.value = freshWeather
            saveCachedWeather(freshWeather)

            triggerAnnouncement(_userProfile.value, freshWeather.suggestion, isManual = false)
        }
    }

    @Suppress("unused")
    suspend fun fetchWeather() {
        val location = locationHelper.getCurrentLocation()
        val (lat, lon) = if (location != null) {
            location.latitude to location.longitude
        } else {
            -1.2864 to 36.8172
        }
        val weatherData = VibeManager.fetchWeather(lat, lon)
        _weather.value = weatherData
        saveCachedWeather(weatherData)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            val profileJson = storage.getString("userProfile")
            val profile = profileJson?.let { json.decodeFromString<UserProfile>(it) }
            val updatedProfile = if (profile != null && profile.avatar != null) {
                val localAvatarPath = avatarRepository.getCachedAvatar(profile.avatar)
                profile.copy(avatar = localAvatarPath ?: profile.avatar)
            } else {
                profile
            }
            _userProfile.value = updatedProfile

            val freshVibe = quoteRepository.getTodayQuote(forceRefresh = true)
            _vibe.value = freshVibe

            _greeting.value = VibeManager.getDynamicGreeting(profile?.persona ?: "Phesty")
            _todayDate.value = VibeManager.getFormattedDate()

            val location = locationHelper.getCurrentLocation()
            val (lat, lon) = if (location != null) {
                location.latitude to location.longitude
            } else {
                -1.2864 to 36.8172
            }
            val weatherData = VibeManager.fetchWeather(lat, lon)
            _weather.value = weatherData
            saveCachedWeather(weatherData)

            _isRefreshing.value = false

            triggerAnnouncement(profile, weatherData.suggestion, isManual = true)
        }
    }

    private fun triggerAnnouncement(profile: UserProfile?, weatherSuggestion: String?, isManual: Boolean) {
        viewModelScope.launch {
            val lastAnnounce = storage.getLong(KEY_LAST_ANNOUNCEMENT_TIME) ?: 0L
            val currentTimeMillis = System.currentTimeMillis()
            val timePassed = currentTimeMillis - lastAnnounce

            if (isManual || timePassed >= ANNOUNCEMENT_COOLDOWN_MS) {
                val isVoiceEnabled = settingsRepository.getInitialVoiceEnabled()
                if (!isVoiceEnabled) return@launch

                val message = buildAnnouncementMessage(
                    userProfile = profile,
                    greeting = _greeting.value,
                    weatherSuggestion = weatherSuggestion ?: "stay in your zone"
                )

                val voiceConfig = VoiceConfig(
                    voiceId = settingsRepository.getInitialVoiceId(),
                    speed = settingsRepository.getInitialVoiceSpeed(),
                    pitch = settingsRepository.getInitialVoicePitch(),
                    provider = settingsRepository.getInitialVoiceProvider(),
                    directorNote = settingsRepository.getInitialDirectorNote()
                )

                voiceCenter.speak(message, VoiceContext(voiceConfig))
                storage.saveLong(KEY_LAST_ANNOUNCEMENT_TIME, currentTimeMillis)
            }
        }
    }

    private fun buildAnnouncementMessage(
        userProfile: UserProfile?,
        greeting: String,
        weatherSuggestion: String
    ): String {
        val now = java.util.Calendar.getInstance()
        val dayName = now.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.LONG, java.util.Locale.UK) ?: "Monday"
        val month = now.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.UK) ?: "January"
        val dateStr = "${now.get(java.util.Calendar.DAY_OF_MONTH)} $month"
        var hours = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minutes = now.get(java.util.Calendar.MINUTE)
        val amPmStr = if (hours >= 12) "PM" else "AM"
        hours = if (hours % 12 == 0) 12 else hours % 12
        val minutesStr = when (minutes) {
            0 -> "o'clock"
            in 1..9 -> "oh $minutes"
            else -> minutes.toString()
        }
        val period = if (amPmStr == "AM") "morning" else "evening"
        val timeForVoice = "$hours $minutesStr in the $period"

        val welcome = userProfile?.displayName?.let { "Hi $it" } ?: "Hi there"
        val introVariants = listOf("Quick update,", "Here's where we are,", "Right now,")
        val intro = introVariants.random()

        val cleanStatus = weatherSuggestion.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
        return "$welcome. $greeting... $intro it's $dayName, $dateStr. The time is $timeForVoice. Just so you know, $cleanStatus."
    }

    private fun startTimeUpdate() {
        viewModelScope.launch {
            while (true) {
                _currentTime.value = VibeManager.getCurrentTimeString()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    override fun onCleared() {
        voiceCenter.shutdown()
        super.onCleared()
    }

    @Serializable
    data class CachedWeather(
        val data: VibeManager.WeatherData,
        val timestamp: Long
    )
}