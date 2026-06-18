package com.baroness.app.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.models.UserProfile
import com.baroness.app.data.AvatarRepository
import com.baroness.app.modules.ProfileManager
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileSetupViewModel(
    context: Context,
    private val currentPersonaId: String
) : ViewModel() {

    private val appContext = context.applicationContext
    private val storageManager = StorageManager(appContext)

    private val avatarRepository = AvatarRepository(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    val name = mutableStateOf("")
    val avatarUri = mutableStateOf<Uri?>(null)
    val isLoading = mutableStateOf(false)
    val isLoadingProfile = mutableStateOf(true)
    val errorMessage = mutableStateOf<String?>(null)
    val existingAvatarUrl = mutableStateOf<String?>(null)
    val newImageUri = mutableStateOf<Uri?>(null)

    init {
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            isLoadingProfile.value = true

            val cachedProfileJson = storageManager.getString("userProfile")
            val cachedProfile = cachedProfileJson?.let {
                try { json.decodeFromString<UserProfile>(it) } catch (_: Exception) { null }
            }
            if (cachedProfile != null && cachedProfile.id == currentPersonaId) {
                name.value = cachedProfile.displayName
                // Use cached avatar
                val cachedAvatarPath = avatarRepository.getCachedAvatar(cachedProfile.avatar)
                avatarUri.value = cachedAvatarPath?.let { Uri.parse(it) } ?: cachedProfile.avatar?.toUri()
                existingAvatarUrl.value = cachedProfile.avatar
                isLoadingProfile.value = false
            }

            viewModelScope.launch {
                try {
                    val profile = withContext(Dispatchers.IO) {
                        ProfileManager.loadProfile(currentPersonaId)
                    }
                    profile?.let { (displayName, avatarUrl) ->
                        name.value = displayName ?: ""
                        val cachedPath = avatarRepository.getCachedAvatar(avatarUrl)
                        avatarUri.value = cachedPath?.let { Uri.parse(it) } ?: avatarUrl?.toUri()
                        existingAvatarUrl.value = avatarUrl
                        val updatedProfile = UserProfile(displayName ?: "", avatarUrl ?: "", "", currentPersonaId)
                        storageManager.saveString("userProfile", json.encodeToString(updatedProfile))
                    }
                } catch (_: Exception) {
               } finally {
                    if (isLoadingProfile.value) isLoadingProfile.value = false
                }
            }
        }
    }

    fun updateName(newName: String) {
        name.value = newName
        if (errorMessage.value != null) errorMessage.value = null
    }

    fun setSelectedImage(uri: Uri) {
        newImageUri.value = uri
        avatarUri.value = uri
    }

    fun onSave(callback: (success: Boolean, profile: ProfileManager.ProfileResult?) -> Unit) {
        val currentName = name.value.trim()
        if (currentName.isEmpty()) {
            errorMessage.value = "Please enter a nickname"
            callback(false, null)
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                if (newImageUri.value != null) {
                    val avatarRepo = AvatarRepository(appContext)
                    avatarRepo.clearCache()
                }

                val result = withContext(Dispatchers.IO) {
                    ProfileManager.saveSetup(
                        context = appContext,
                        currentPersonaId = currentPersonaId,
                        displayName = currentName,
                        newImageUri = newImageUri.value,
                        existingAvatarUrl = existingAvatarUrl.value
                    )
                }
                val userProfile = UserProfile(
                    displayName = result.displayName,
                    avatar = result.avatar,
                    persona = result.persona,
                    id = currentPersonaId
                )
                val jsonString = json.encodeToString(userProfile)
                storageManager.saveString("userProfile", jsonString)
                callback(true, result)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to save profile"
                callback(false, null)
            } finally {
                isLoading.value = false
            }
        }
    }


    fun onLogout(callback: () -> Unit) {
        viewModelScope.launch {
            storageManager.remove("vibe_persona")
            storageManager.remove("userProfile")
            storageManager.remove("currentPersonaId")
            callback()
        }
    }
}