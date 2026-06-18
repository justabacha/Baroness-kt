package com.baroness.app.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val displayName: String,
    val avatar: String?,
    val persona: String,
    val id: String
)