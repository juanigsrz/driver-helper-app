package com.juanigsrz.driverhelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

object LocationProvider {

    @Volatile private var cached: Location? = null
    private var client: FusedLocationProviderClient? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { cached = it }
        }
    }

    private fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (client != null) return
        if (!hasPermission(ctx)) return
        val c = LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
        client = c
        try {
            c.lastLocation.addOnSuccessListener { loc -> loc?.let { cached = it } }
            val req = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                10_000L,
            ).setMinUpdateIntervalMillis(5_000L).build()
            c.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            client = null
        }
    }

    fun stop() {
        client?.removeLocationUpdates(callback)
        client = null
    }

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        cached?.let { return it }
        val c = client
            ?: LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
                .also { client = it }
        return try {
            val fix = c.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token,
            ).await() ?: c.lastLocation.await()
            fix?.also { cached = it }
        } catch (_: SecurityException) {
            null
        }
    }
}
