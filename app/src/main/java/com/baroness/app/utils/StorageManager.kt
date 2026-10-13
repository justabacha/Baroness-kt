package com.baroness.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baroness_prefs")

class StorageManager(private val context: Context) {

    companion object {
        @Volatile
        private var cachedUserProfile: String? = null
        private val cachedStrings = ConcurrentHashMap<String, String>()
        @Volatile
        private var isMirrorInitialized = false
        private val companionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun initSyncMirror(context: Context) {
            if (isMirrorInitialized) return
            synchronized(this) {
                if (isMirrorInitialized) return
                isMirrorInitialized = true
            }
            val appContext = context.applicationContext
            companionScope.launch {
                appContext.dataStore.data.collect { prefs ->
                    val map = mutableMapOf<String, String>()
                    prefs.asMap().forEach { (key, value) ->
                        if (value is String) {
                            map[key.name] = value
                        }
                    }
                    cachedStrings.clear()
                    cachedStrings.putAll(map)
                    cachedUserProfile = map["userProfile"]
                }
            }
        }

        fun getUserProfileSync(): String? = cachedUserProfile ?: cachedStrings["userProfile"]
        fun getStringSync(key: String): String? = cachedStrings[key]
    }

    suspend fun saveAuthSession(accessToken: String, refreshToken: String?, expiresAt: Long?) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("auth_token")] = accessToken
            if (refreshToken.isNullOrBlank()) {
                preferences.remove(stringPreferencesKey("refresh_token"))
            } else {
                preferences[stringPreferencesKey("refresh_token")] = refreshToken
            }
            if (expiresAt == null) {
                preferences.remove(longPreferencesKey("token_expires_at"))
            } else {
                preferences[longPreferencesKey("token_expires_at")] = expiresAt
            }
        }
    }

    suspend fun clearSessionData() {
        val keys = setOf(
            "auth_token",
            "refresh_token",
            "token_expires_at",
            "currentPersonaId",
            "userProfile",
            "vibe_persona",
            "fcm_token"
        )
        context.dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name in keys }
                .forEach { key -> preferences.remove(key) }
        }
    }

    // String
    suspend fun saveString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
    suspend fun getString(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }
    fun getStringFlow(key: String): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }
    }

    // Boolean
    suspend fun saveBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
    suspend fun getBoolean(key: String): Boolean? {
        val prefKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }
    fun getBooleanFlow(key: String): Flow<Boolean?> {
        val prefKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }
    }

    // Float
    suspend fun saveFloat(key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
    suspend fun getFloat(key: String): Float? {
        val prefKey = floatPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }
    fun getFloatFlow(key: String): Flow<Float?> {
        val prefKey = floatPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }
    }

    // Long
    suspend fun saveLong(key: String, value: Long) {
        val prefKey = longPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
    suspend fun getLong(key: String): Long? {
        val prefKey = longPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }
    fun getLongFlow(key: String): Flow<Long?> {
        val prefKey = longPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }
    }

    suspend fun saveObject(key: String, value: Any) {
        saveString(key, value.toString())
    }

    suspend fun remove(key: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it.remove(prefKey) }
    }
}
