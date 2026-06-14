package com.baroness.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baroness_prefs")

class StorageManager(private val context: Context) {

    suspend fun saveString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    suspend fun getString(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        val flow: Flow<Preferences> = context.dataStore.data
        // collect first value (blocking only for example, use coroutines in ViewModel)
        return runBlocking {
            flow.map { preferences ->
                preferences[prefKey]
            }.first()
        }
    }

    suspend fun saveObject(key: String, value: Any) {
        // Store as JSON string (simplified, use Gson/Moshi for real)
        saveString(key, value.toString())
    }

    suspend fun remove(key: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it.remove(prefKey) }
    }
}