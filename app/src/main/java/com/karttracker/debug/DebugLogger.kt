package com.karttracker.debug

import android.util.Log

object DebugLogger {
    private const val TAG = "KartTracker"
    var imuCallbackCount = 0
    var trackPointCallbackCount = 0
    var gpsCallbackCount = 0
    
    fun logIMU() {
        imuCallbackCount++
        if (imuCallbackCount % 100 == 0) {
            Log.d(TAG, "IMU回调次数: $imuCallbackCount")
        }
    }
    
    fun logTrackPoint() {
        trackPointCallbackCount++
        if (trackPointCallbackCount % 100 == 0) {
            Log.d(TAG, "TrackPoint回调次数: $trackPointCallbackCount")
        }
    }
    
    fun logGPS() {
        gpsCallbackCount++
        Log.d(TAG, "GPS回调次数: $gpsCallbackCount")
    }
    
    fun reset() {
        imuCallbackCount = 0
        trackPointCallbackCount = 0
        gpsCallbackCount = 0
    }
    
    fun getStats(): String {
        return "IMU:$imuCallbackCount GPS:$gpsCallbackCount Track:$trackPointCallbackCount"
    }
}
