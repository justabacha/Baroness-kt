package com.baroness.app.utils

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "baroness_prefs"
private const val KEY_USER_ID = "current_user_id"
private const val KEY_USER_KEY = "current_user_key"

class UserSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Default to Phesty, but each phone should set this once
    var currentUserId: String
        get() = prefs.getString(KEY_USER_ID, "phesty_official") ?: "phesty_official"
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var currentUserKey: String
        get() = prefs.getString(KEY_USER_KEY, "P") ?: "P"
        set(value) = prefs.edit().putString(KEY_USER_KEY, value).apply()

    val isPhesty: Boolean get() = currentUserId == "phesty_official"
    val isBaroness: Boolean get() = currentUserId == "baroness_official"

    fun setUser(userId: String, userKey: String) {
        currentUserId = userId
        currentUserKey = userKey
    }
}