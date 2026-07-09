package com.baroness.app.api

import android.util.Log
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.utils.formatLongToIso
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

private const val TAG = "WishlistApi"

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

    // ─── CREATE ───
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
                createdAt = formatLongToIso(createdAt)
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
                    set("updated_at", formatLongToIso(updatedAt))
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

    suspend fun upsertReaction(reaction: ReactionDto): Boolean {
        return try {
            Log.d(TAG, "Upserting reaction: wish=${reaction.wishId}")

            supabase.postgrest["wishlist_reactions"]
                .upsert(reaction) {
                    onConflict = "wish_id,persona_id"
                }

            Log.d(TAG, "Reaction upsert success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reaction upsert failed: ${e.message}", e)
            false
        }
    }

    suspend fun upsertRating(rating: RatingDto): Boolean {
        return try {
            Log.d(TAG, "Upserting rating: wish=${rating.wishId}")

            supabase.postgrest["wishlist_ratings"]
                .upsert(rating) {
                    onConflict = "wish_id,persona_id"
                }

            Log.d(TAG, "Rating upsert success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Rating upsert failed: ${e.message}", e)
            false
        }
    }

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

    suspend fun fetchAllReactions(): List<ReactionDto> {
        return try {
            Log.d(TAG, "Fetching all reactions")

            val result = supabase.postgrest["wishlist_reactions"]
                .select()
                .decodeList<ReactionDto>()

            Log.d(TAG, "Fetch success: ${result.size} reactions")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Fetch reactions failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchAllRatings(): List<RatingDto> {
        return try {
            Log.d(TAG, "Fetching all ratings")

            val result = supabase.postgrest["wishlist_ratings"]
                .select()
                .decodeList<RatingDto>()

            Log.d(TAG, "Fetch success: ${result.size} ratings")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Fetch ratings failed: ${e.message}", e)
            emptyList()
        }
    }
}