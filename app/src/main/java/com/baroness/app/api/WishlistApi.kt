package com.baroness.app.api

import android.util.Log
import com.baroness.app.config.SupabaseConfig
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "WishlistApi"

// API 24 compatible ISO timestamp formatter
private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun Long.toIsoString(): String {
    return isoFormat.format(Date(this))
}

// DTOs with @SerialName for JSON field mapping (Kotlin naming convention)
@Serializable
data class WishDto(
    val id: Long? = null,
    val text: String,
    @SerialName("wish_date") val wishDate: String,
    val status: String,
    @SerialName("creator_id") val creatorId: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ReactionDto(
    @SerialName("wish_id") val wishId: Long,
    @SerialName("persona_id") val personaId: String,
    val emoji: String
)

@Serializable
data class RatingDto(
    @SerialName("wish_id") val wishId: Long,
    @SerialName("persona_id") val personaId: String,
    val rating: Int
)

object WishlistApi {

    private val supabase = SupabaseConfig.supabase

    // ─── CREATE (returns created row with server-generated ID) ───
    suspend fun createWish(
        text: String,
        wishDate: String,
        status: String,
        creatorId: String,
        createdAt: Long
    ): WishDto? {
        return try {
            Log.d(TAG, "Creating wish: text=$text, creator=$creatorId")

            val wish = WishDto(
                text = text,
                wishDate = wishDate,
                status = status,
                creatorId = creatorId,
                createdAt = createdAt.toIsoString()
            )

            val result = supabase.postgrest["wishlist_items"]
                .insert(wish) {
                    select()
                }
                .decodeSingleOrNull<WishDto>()

            Log.d(TAG, "Create success: id=${result?.id}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Create failed: ${e.message}", e)
            null
        }
    }

    // ─── UPDATE ───
    suspend fun updateWish(wishId: Long, status: String, updatedAt: Long): Boolean {
        return try {
            Log.d(TAG, "Updating wish $wishId: status=$status")

            supabase.postgrest["wishlist_items"]
                .update({
                    set("status", status)
                    set("updated_at", updatedAt.toIsoString())
                }) {
                    filter { eq("id", wishId) }
                }

            Log.d(TAG, "Update success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Update failed: ${e.message}", e)
            false
        }
    }

    // ─── DELETE ───
    suspend fun deleteWish(wishId: Long): Boolean {
        return try {
            Log.d(TAG, "Deleting wish $wishId")

            supabase.postgrest["wishlist_items"]
                .delete {
                    filter { eq("id", wishId) }
                }

            Log.d(TAG, "Delete success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
            false
        }
    }

    // ─── UPSERT REACTION ───
    suspend fun upsertReaction(reaction: ReactionDto): Boolean {
        return try {
            Log.d(TAG, "Upserting reaction: wish=${reaction.wishId}")

            supabase.postgrest["wishlist_reactions"]
                .upsert(reaction)

            Log.d(TAG, "Reaction upsert success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reaction upsert failed: ${e.message}", e)
            false
        }
    }

    // ─── UPSERT RATING ───
    suspend fun upsertRating(rating: RatingDto): Boolean {
        return try {
            Log.d(TAG, "Upserting rating: wish=${rating.wishId}")

            supabase.postgrest["wishlist_ratings"]
                .upsert(rating)

            Log.d(TAG, "Rating upsert success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Rating upsert failed: ${e.message}", e)
            false
        }
    }

    // ─── FETCH ALL (for initial sync) ───
    suspend fun fetchAllWishes(): List<WishDto> {
        return try {
            Log.d(TAG, "Fetching all wishes")

            val result = supabase.postgrest["wishlist_items"]
                .select {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<WishDto>()

            Log.d(TAG, "Fetch success: ${result.size} wishes")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed: ${e.message}", e)
            emptyList()
        }
    }
}
