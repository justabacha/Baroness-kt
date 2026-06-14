package com.baroness.app.viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.modules.AuthManager
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException

class GateViewModel(private val context: Context) : ViewModel() {
    private val authManager = AuthManager(context)

    val password = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    val isPasswordValid: Boolean
        get() = password.value.length >= 6

    fun updatePassword(newPass: String) {
        password.value = newPass
        if (errorMessage.value != null) errorMessage.value = null
    }

    fun onGateSelected(persona: String, onSuccess: (String?, String) -> Unit) {
        if (!isPasswordValid) {
            errorMessage.value = "Secret key must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            val result = authManager.checkGate(persona, password.value)
            isLoading.value = false
            when (result) {
                is AuthManager.GateResult.Success -> {
                    val storage = StorageManager(context)
                    storage.saveString("vibe_persona", persona)
                    storage.saveString("currentPersonaId", result.currentPersonaId)
                    result.userProfile?.let { profile ->
                        try {
                            val jsonString = Json.encodeToString(profile)
                            storage.saveString("userProfile", jsonString)
                        } catch (e: SerializationException) {
                            e.printStackTrace()
                        }
                    }
                    onSuccess(result.userProfile?.id, result.currentPersonaId)
                }
                is AuthManager.GateResult.Error -> {
                    errorMessage.value = result.message
                }
            }
        }
    }
}