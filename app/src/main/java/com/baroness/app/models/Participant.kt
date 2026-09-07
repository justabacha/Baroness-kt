package com.baroness.app.models

data class Participant(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val isOnline: Boolean?
)
