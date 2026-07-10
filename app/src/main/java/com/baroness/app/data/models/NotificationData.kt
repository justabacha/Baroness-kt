package com.baroness.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationData(
    val title: String,
    val body: String,
    val avatarUrl: String? = null,
    val featureType: String? = null, // e.g., "wishlist", "messages"
    val route: String? = null,      // Deep link route
    val timestamp: Long = System.currentTimeMillis()
)
