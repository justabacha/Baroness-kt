package com.baroness.app.phestydrop.manager

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.baroness.app.BuildConfig
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.phestydrop.data.PhestyDropInfo
import com.baroness.app.utils.StorageManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhestyDropManager {

    private const val TAG = "PhestyDropManager"
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Checks Supabase `phestydrop_releases` table for a newer build version code.
     * Returns `PhestyDropInfo` if remote versionCode > local BuildConfig.VERSION_CODE, otherwise null.
     */
    suspend fun checkForDrop(): PhestyDropInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = BuildConfig.VERSION_CODE
            Log.d(TAG, "Checking for drop. Local versionCode=$currentVersionCode")

            val releases = SupabaseConfig.supabase.from("phestydrop_releases")
                .select {
                    order("version_code", order = Order.DESCENDING)
                    limit(1)
                }
                .decodeList<PhestyDropInfo>()

            val latestDrop = releases.firstOrNull()
            appContext?.let { context ->
                StorageManager(context).saveLong(
                    "phestydrop_last_checked",
                    System.currentTimeMillis()
                )
            }
            if (latestDrop != null && latestDrop.versionCode > currentVersionCode) {
                Log.d(TAG, "New PhestyDrop detected! Remote versionCode=${latestDrop.versionCode}")
                return@withContext latestDrop
            } else {
                Log.d(TAG, "App is up to date.")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for PhestyDrop: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Launches the package installer for a DownloadManager-provided content URI.
     * Returns false when install permission is needed or no installer can handle the URI.
     */
    fun installDrop(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "Unknown package install permission missing. Launching Settings gate.")
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return false
            }
        }

        return try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No package installer is available", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}", e)
            false
        }
    }
}
