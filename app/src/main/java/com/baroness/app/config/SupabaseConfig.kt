package com.baroness.app.config

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit   // <-- ADD THIS

object SupabaseConfig {
    const val SUPABASE_URL = "https://wckluymkbqxdmipzaiff.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indja2x1eW1rYnF4ZG1pcHphaWZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4MjM3NjAsImV4cCI6MjA5MzM5OTc2MH0.y3murBfcZtgluuPd_uFBut4Ky3Wl8WAHVCp-kA1u9sU"

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            install(Postgrest)
            install(Storage)
        }
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}