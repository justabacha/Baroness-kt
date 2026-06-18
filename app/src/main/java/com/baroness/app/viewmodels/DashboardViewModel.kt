package com.baroness.app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.utils.LocationHelper
import com.baroness.app.models.UserProfile
import com.baroness.app.models.VibeQuote
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.VibeManager
import com.baroness.app.data.QuoteRepository
import com.baroness.app.data.AvatarRepository
import com.baroness.app.utils.VoiceManager
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
    private val json = Json { ignoreUnknownKeys = true }
    private val voiceManager = VoiceManager(getApplication())
    private val locationHelper = LocationHelper(getApplication())
    private val quoteRepository = QuoteRepository(getApplication())
    private val avatarRepository = AvatarRepository(getApplication())

    companion object {
        private var hasSpokenInSession = false
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

            val todaysVibe = quoteRepository.getTodayQuote(forceRefresh = false)
            _vibe.value = todaysVibe

            _todayDate.value = VibeManager.getFormattedDate()
            _greeting.value = VibeManager.getDynamicGreeting(profile?.persona ?: "Phesty")

            val cachedWeather = loadCachedWeather()
            if (cachedWeather != null) {
                _weather.value = cachedWeather.data
            }

            _isInitialLoading.value = false

            triggerAnnouncement(profile, _weather.value?.suggestion, isManual = false)

            if (cachedWeather == null || System.currentTimeMillis() - cachedWeather.timestamp >= 10 * 60 * 1000) {
                refreshWeatherInBackground()
            }
        }
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
        if (isManual || !hasSpokenInSession) {
            val message = voiceManager.buildAnnouncementMessage(
                userProfile = profile,
                greeting = _greeting.value,
                weatherSuggestion = weatherSuggestion ?: "stay in your zone"
            )
            voiceManager.speak(message)
            hasSpokenInSession = true
        }
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
        voiceManager.shutdown()
        super.onCleared()
    }

    @Serializable
    data class CachedWeather(
        val data: VibeManager.WeatherData,
        val timestamp: Long
    )
}