package com.baroness.app.api

import com.baroness.app.config.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object WishlistApi {
    @Suppress("unused")
    private val supabase = SupabaseConfig.supabase
    private val client = SupabaseConfig.client
    private val supabaseUrl = SupabaseConfig.SUPABASE_URL
    private val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY

    suspend fun createWish(payload: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_items")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .header("Prefer", "return=representation")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) null else response.body?.string()
            } catch (_: Exception) { null }
        }
    }

    suspend fun updateWish(wishId: String, payload: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_items?id=eq.$wishId")
                    .patch(payload.toRequestBody("application/json".toMediaType()))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (_: Exception) { false }
        }
    }

    suspend fun deleteWish(wishId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_items?id=eq.$wishId")
                    .delete()
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (_: Exception) { false }
        }
    }

    suspend fun upsertReaction(wishId: String, personaId: String, emoji: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("wish_id", wishId)
                    put("persona_id", personaId)
                    put("emoji", emoji)
                }.toString()
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_reactions")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .header("Prefer", "resolution=merge-duplicates")
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (_: Exception) { false }
        }
    }

    suspend fun upsertRating(wishId: String, personaId: String, rating: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("wish_id", wishId)
                    put("persona_id", personaId)
                    put("rating", rating)
                }.toString()
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_ratings")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .header("Prefer", "resolution=merge-duplicates")
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (_: Exception) { false }
        }
    }

    suspend fun fetchAllWishes(): List<String>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/wishlist_items?select=*")
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer $supabaseKey")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val jsonArray = JSONArray(body)
                    val result = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        result.add(jsonArray.getJSONObject(i).toString())
                    }
                    result
                } else null
            } catch (_: Exception) { null }
        }
    }
}