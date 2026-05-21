package com.karttracker.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karttracker.model.GPSData

class GPSCollector(private val context: Context) {
    private val TAG = "KartTracker"
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var lastGPSData: GPSData? = null
    private val listeners = mutableListOf<(GPSData) -> Unit>()
    private var callbackCount = 0
    
    fun addListener(listener: (GPSData) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (GPSData) -> Unit) {
        listeners.remove(listener)
    }
    
    fun start() {
        // 检查权限
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 没有权限，不启动GPS
            return
        }
        
        try {
            val locationRequest = LocationRequest.create().apply {
                interval = 50
                fastestInterval = 20
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stop() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                callbackCount++
                Log.d(TAG, "GPS回调 #$callbackCount: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}")
                
                val data = GPSData(
                    timestamp = location.elapsedRealtimeNanos / 1_000_000_000.0,
                    lat = location.latitude,
                    lon = location.longitude,
                    alt = location.altitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing
                )
                lastGPSData = data
                listeners.forEach { it(data) }
            } ?: run {
                Log.d(TAG, "GPS回调 #${callbackCount}: location is null")
            }
        }
    }
    
    fun getLastGPSData(): GPSData? = lastGPSData
    
    fun getLastSpeed(): Float = lastGPSData?.speed ?: 0f
}
