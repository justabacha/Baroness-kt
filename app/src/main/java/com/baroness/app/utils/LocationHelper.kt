package com.baroness.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class LocationHelper(private val context: Context) {

    suspend fun getCurrentLocation(): Location? {
        // Check permissions
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (_: Exception) {
            null
        }
    }
}