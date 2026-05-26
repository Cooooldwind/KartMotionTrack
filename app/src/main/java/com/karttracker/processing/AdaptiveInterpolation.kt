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
        if (totalPoints < 2) return result
        
        val interval = duration / totalPoints
        
        val sortedIMU = imuPoints.filter { it.type == com.karttracker.model.DataType.IMU }
            .sortedBy { it.timestamp }
        
        val imuTimestamps = sortedIMU.map { it.timestamp }.toDoubleArray()
        
        for (i in 0 until totalPoints) {
            val t = startTime + i * interval
            
            val gpsIndex = findGPSIndex(t, validGPS)
            val before = validGPS[gpsIndex]
            val after = if (gpsIndex + 1 < validGPS.size) validGPS[gpsIndex + 1] else before
            
            val (lat, lon, alt, speed, bearing, accuracy) = if (before.timestamp == after.timestamp) {
                val (fLat, fLon) = kalmanFilter.process(before.lat, before.lon, before.accuracy)
                InterpolatedData(fLat, fLon, 0.0, before.speed, before.bearing, before.accuracy)
            } else {
                interpolateBetweenGPS(t, before, after, sortedIMU, imuTimestamps)
            }
            
            val rollPitchYaw = calculateOrientationFast(t, sortedIMU, imuTimestamps)
            
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
    
    private fun findGPSIndex(t: Double, gpsPoints: List<GPSPoint>): Int {
        var low = 0
        var high = gpsPoints.size - 1
        
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (gpsPoints[mid].timestamp <= t) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return low
    }
    
    private fun findIMURange(startTime: Double, endTime: Double, timestamps: DoubleArray): Pair<Int, Int> {
        var startIdx = 0
        var endIdx = timestamps.size
        
        var low = 0
        var high = timestamps.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            if (timestamps[mid] >= startTime) {
                startIdx = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        
        low = startIdx
        high = timestamps.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            if (timestamps[mid] <= endTime) {
                endIdx = mid + 1
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        
        return Pair(startIdx, endIdx)
    }
    
    private fun interpolateBetweenGPS(
        t: Double,
        before: GPSPoint,
        after: GPSPoint,
        imuPoints: List<RawDataPoint>,
        imuTimestamps: DoubleArray
    ): InterpolatedData {
        val progress = ((t - before.timestamp) / (after.timestamp - before.timestamp)).toFloat()
        
        var lat = lerp(before.lat, after.lat, progress)
        var lon = lerp(before.lon, after.lon, progress)
        val alt = lerp(before.accuracy.toDouble(), after.accuracy.toDouble(), progress).toFloat()
        val speed = lerp(before.speed, after.speed, progress)
        val bearing = lerpAngle(before.bearing, after.bearing, progress)
        
        val lateralOffset = calculateLateralOffsetFast(before.timestamp, after.timestamp, imuPoints, imuTimestamps)
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
    
    private fun calculateLateralOffsetFast(
        startTime: Double,
        endTime: Double,
        imuPoints: List<RawDataPoint>,
        imuTimestamps: DoubleArray
    ): Double {
        val range = findIMURange(startTime, endTime, imuTimestamps)
        if (range.first >= range.second) return 0.0
        
        var totalOffset = 0.0
        var prevTime = startTime
        
        for (i in range.first until range.second) {
            val imu = imuPoints[i]
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
    
    private fun calculateOrientationFast(
        t: Double,
        imuPoints: List<RawDataPoint>,
        imuTimestamps: DoubleArray
    ): FloatArray {
        val idx = imuTimestamps.binarySearch(t)
        val searchIdx = if (idx < 0) -idx - 1 else idx
        
        val startIdx = max(0, searchIdx - 5)
        val endIdx = min(imuPoints.size, searchIdx + 5)
        
        if (startIdx >= endIdx) return floatArrayOf(0f, 0f, 0f)
        
        var sumAccelX = 0.0
        var sumAccelY = 0.0
        var sumAccelZ = 0.0
        var count = 0
        
        for (i in startIdx until endIdx) {
            val imu = imuPoints[i]
            imu.accelX?.let { sumAccelX += it; count++ }
            imu.accelY?.let { sumAccelY += it }
            imu.accelZ?.let { sumAccelZ += it }
        }
        
        if (count == 0) return floatArrayOf(0f, 0f, 0f)
        
        val avgAccelX = (sumAccelX / count).toFloat()
        val avgAccelY = (sumAccelY / count).toFloat()
        val avgAccelZ = (sumAccelZ / count).toFloat()
        
        val pitch = Math.toDegrees(atan2(-avgAccelX.toDouble(), sqrt(avgAccelY * avgAccelY + avgAccelZ * avgAccelZ).toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(avgAccelY.toDouble(), avgAccelZ.toDouble())).toFloat()
        
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
