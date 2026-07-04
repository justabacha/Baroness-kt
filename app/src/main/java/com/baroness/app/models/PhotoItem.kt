package com.baroness.app.models

import kotlinx.serialization.Serializable

@Serializable
data class PhotoItem(
    val id: String,
    val url: String,
    val title: String,
    val dateString: String, // format "yyyy-MM-dd"
    val location: String? = null
)
