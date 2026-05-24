package com.karttracker.processing

import com.karttracker.model.RawDataPoint
import com.karttracker.model.TrackPoint
import kotlin.math.*

class AdaptiveInterpolation(
    private val targetRateHz: Int = 100
) {
    private val kalmanFilter = KalmanGPSFilter()
    
    companion object {
        private const val MAX_LATERAL_OFFSET = 2.0
        private const val SHARP_TURN_ACCEL_THRESHOLD = 4.0
    }
    
    data class GPSPoint(
        val timestamp: Double,
        val lat: Double,
        val lon: Double,
        val speed: Float,
        val bearing: Float,
        val accuracy: Float
    )
    
    fun interpolate(
        gpsPoints: List<RawDataPoint>,
        imuPoints: List<RawDataPoint>
    ): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        
        if (gpsPoints.size < 2) return result
        
        kalmanFilter.reset()
        
        val validGPS = gpsPoints.mapNotNull { p ->
            if (p.type == com.karttracker.model.DataType.GPS && 
                p.gpsLat != null && p.gpsLon != null) {
                GPSPoint(
                    timestamp = p.timestamp,
                    lat = p.gpsLat,
                    lon = p.gpsLon,
                    speed = p.gpsSpeed ?: 0f,
                    bearing = p.gpsBearing ?: 0f,
                    accuracy = p.gpsAccuracy ?: 10f
                )
            } else null
        }.sortedBy { it.timestamp }
        
        if (validGPS.size < 2) return result
        
        val startTime = validGPS.first().timestamp
        val endTime = validGPS.last().timestamp
        val duration = endTime - startTime
        
        val totalPoints = (duration * targetRateHz).toInt()
        val interval = duration / totalPoints
        
        for (i in 0 until totalPoints) {
            val t = startTime + i * interval
            val (lat, lon, alt, speed, bearing, accuracy) = interpolateAtTime(
                t, validGPS, imuPoints
            )
            
            val rollPitchYaw = calculateOrientation(t, imuPoints)
            
            result.add(TrackPoint(
                timestamp = t,
                lat = lat,
                lon = lon,
                alt = alt,
                speed = speed,
                accuracy = accuracy,
                roll = rollPitchYaw[0],
                pitch = rollPitchYaw[1],
                yaw = rollPitchYaw[2]
            ))
        }
        
        return result
    }
    
    private fun interpolateAtTime(
        t: Double,
        gpsPoints: List<GPSPoint>,
        imuPoints: List<RawDataPoint>
    ): InterpolatedData {
        val before = gpsPoints.filter { it.timestamp <= t }.lastOrNull()
        val after = gpsPoints.filter { it.timestamp >= t }.firstOrNull()
        
        if (before == null || after == null || before.timestamp == after.timestamp) {
            if (before != null) {
                val (fLat, fLon) = kalmanFilter.process(before.lat, before.lon, before.accuracy)
                return InterpolatedData(fLat, fLon, 0.0, before.speed, before.bearing, before.accuracy)
            }
            return InterpolatedData(0.0, 0.0, 0.0, 0f, 0f, 100f)
        }
        
        val progress = ((t - before.timestamp) / (after.timestamp - before.timestamp)).toFloat()
        
        var lat = lerp(before.lat, after.lat, progress)
        var lon = lerp(before.lon, after.lon, progress)
        val alt = lerp(before.accuracy.toDouble(), after.accuracy.toDouble(), progress).toFloat()
        val speed = lerp(before.speed, after.speed, progress)
        val bearing = lerpAngle(before.bearing, after.bearing, progress)
        
        val lateralOffset = calculateLateralOffset(t, before.timestamp, after.timestamp, imuPoints)
        if (abs(lateralOffset) > 0.01) {
            val bearingRad = Math.toRadians(bearing.toDouble())
            val lateralBearing = bearingRad + Math.PI / 2
            val offsetMeters = lateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET)
            
            lat += offsetMeters * cos(lateralBearing) / 111320.0
            lon += offsetMeters * sin(lateralBearing) / (111320.0 * cos(Math.toRadians(lat)))
        }
        
        val (fLat, fLon) = kalmanFilter.process(lat, lon, before.accuracy)
        
        return InterpolatedData(fLat, fLon, alt.toDouble(), speed, bearing, before.accuracy)
    }
    
    private fun calculateLateralOffset(
        t: Double,
        startTime: Double,
        endTime: Double,
        imuPoints: List<RawDataPoint>
    ): Double {
        val relevantIMU = imuPoints.filter { 
            it.timestamp >= startTime && it.timestamp <= endTime 
        }
        
        if (relevantIMU.isEmpty()) return 0.0
        
        var totalOffset = 0.0
        var prevTime = startTime
        
        for (imu in relevantIMU) {
            val dt = imu.timestamp - prevTime
            if (dt > 0 && dt < 0.1) {
                val accelY = imu.accelY?.toDouble() ?: 0.0
                
                if (abs(accelY) > SHARP_TURN_ACCEL_THRESHOLD) {
                    totalOffset += accelY * dt * dt * 0.5
                }
            }
            prevTime = imu.timestamp
        }
        
        return totalOffset
    }
    
    private fun calculateOrientation(
        t: Double,
        imuPoints: List<RawDataPoint>
    ): FloatArray {
        val nearby = imuPoints.filter { 
            abs(it.timestamp - t) < 0.05 
        }
        
        if (nearby.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        
        val avgAccelX = nearby.mapNotNull { it.accelX }.average().toFloat()
        val avgAccelY = nearby.mapNotNull { it.accelY }.average().toFloat()
        val avgAccelZ = nearby.mapNotNull { it.accelZ }.average().toFloat()
        
        val pitch = Math.toDegrees(atan2(-avgAccelX, sqrt(avgAccelY * avgAccelY + avgAccelZ * avgAccelZ)).toDouble()).toFloat()
        val roll = Math.toDegrees(atan2(avgAccelY, avgAccelZ).toDouble()).toFloat()
        
        return floatArrayOf(roll, pitch, 0f)
    }
    
    private fun lerp(a: Double, b: Double, t: Float): Double {
        return a + (b - a) * t
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
    
    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return a + diff * t
    }
    
    private data class InterpolatedData(
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val speed: Float,
        val bearing: Float,
        val accuracy: Float
    )
}
