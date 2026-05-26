package com.juanigsrz.driverhelper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

object LocationProvider {

    private fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        return try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token,
            ).await()
        } catch (_: SecurityException) {
            null
        }
    }
}
