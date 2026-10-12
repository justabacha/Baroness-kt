package com.baroness.app.phestydrop.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhestyDropInfo(
    @SerialName("id") val id: String? = null,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("changelog") val changelog: String? = null,
    @SerialName("is_mandatory") val isMandatory: Boolean = false,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
)
