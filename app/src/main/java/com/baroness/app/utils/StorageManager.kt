package com.baroness.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baroness_prefs")

class StorageManager(private val context: Context) {

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
