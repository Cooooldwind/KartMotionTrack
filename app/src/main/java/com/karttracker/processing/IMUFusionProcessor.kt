package com.karttracker.processing

import com.karttracker.model.RawDataPoint
import kotlin.math.*

class IMUFusionProcessor(
    private val targetRateHz: Int = 100
) {
    private val METERS_PER_DEGREE_LAT = 111000.0
    
    companion object {
        const val DEFAULT_IMU_GAIN = 0.3f
        const val DEFAULT_COMPLEMENTARY_FILTER_ALPHA = 0.3f
        const val DEFAULT_GAUSSIAN_SMOOTH_SIGMA = 50f
    }
    
    data class IMUParams(
        val imuGain: Float = DEFAULT_IMU_GAIN,
        val complementaryFilterAlpha: Float = DEFAULT_COMPLEMENTARY_FILTER_ALPHA,
        val gaussianSmoothSigma: Float = DEFAULT_GAUSSIAN_SMOOTH_SIGMA
    )
    
    data class GPSPoint(
        val timestamp: Double,
        val lat: Double,
        val lon: Double,
        val speed: Float,
        val bearing: Float
    )
    
    data class IMUPoint(
        val timestamp: Double,
        val accelX: Float,
        val accelY: Float,
        val gyroZ: Float
    )
    
    fun interpolateWithIMU(
        gpsPoints: List<GPSPoint>,
        imuPoints: List<IMUPoint>,
        params: IMUParams = IMUParams()
    ): List<Pair<Double, Double>> {
        if (gpsPoints.size < 2) {
            return gpsPoints.map { Pair(it.lat, it.lon) }
        }
        
        val gpsTimestamps = gpsPoints.map { it.timestamp }.toDoubleArray()
        val gpsLats = gpsPoints.map { it.lat }.toDoubleArray()
        val gpsLons = gpsPoints.map { it.lon }.toDoubleArray()
        
        val startTime = gpsTimestamps[0]
        val endTime = gpsTimestamps.last()
        
        val totalPoints = ((endTime - startTime) * targetRateHz).toInt()
        if (totalPoints < 2) {
            return gpsPoints.map { Pair(it.lat, it.lon) }
        }
        
        val interval = (endTime - startTime) / totalPoints
        val newTimestamps = DoubleArray(totalPoints + 1) { startTime + it * interval }
        
        val baseLats = DoubleArray(totalPoints + 1)
        val baseLons = DoubleArray(totalPoints + 1)
        
        for (i in newTimestamps.indices) {
            val t = newTimestamps[i]
            val gpsIdx = binarySearchGPS(t, gpsTimestamps)
            val before = gpsPoints[gpsIdx]
            val after = if (gpsIdx + 1 < gpsPoints.size) gpsPoints[gpsIdx + 1] else before
            
            val progress = if (after.timestamp != before.timestamp) {
                ((t - before.timestamp) / (after.timestamp - before.timestamp)).toDouble()
            } else 0.0
            
            baseLats[i] = lerp(before.lat, after.lat, progress)
            baseLons[i] = lerp(before.lon, after.lon, progress)
        }
        
        if (imuPoints.size > 10) {
            return applyIMUFusion(baseLats, baseLons, newTimestamps, gpsPoints, imuPoints, params)
        }
        
        return baseLats.mapIndexed { i, lat -> Pair(lat, baseLons[i]) }
    }
    
    private fun applyIMUFusion(
        baseLats: DoubleArray,
        baseLons: DoubleArray,
        timestamps: DoubleArray,
        gpsPoints: List<GPSPoint>,
        imuPoints: List<IMUPoint>,
        params: IMUParams
    ): List<Pair<Double, Double>> {
        val imuTimestamps = imuPoints.map { it.timestamp }.toDoubleArray()
        val gyroZ = imuPoints.map { it.gyroZ.toDouble() }.toDoubleArray()
        val accelX = imuPoints.map { it.accelX.toDouble() }.toDoubleArray()
        val accelY = imuPoints.map { it.accelY.toDouble() }.toDoubleArray()
        
        val finalLats = DoubleArray(timestamps.size) { 0.0 }
        val finalLons = DoubleArray(timestamps.size) { 0.0 }
        
        val metersPerDegreeLon = METERS_PER_DEGREE_LAT * cos(Math.toRadians(gpsPoints[0].lat))
        
        val gpsIndices = IntArray(gpsPoints.size)
        for (i in gpsPoints.indices) {
            gpsIndices[i] = binarySearchTimestamp(timestamps, gpsPoints[i].timestamp)
        }
        
        for (segmentIdx in 0 until gpsPoints.size - 1) {
            val startIdx = gpsIndices[segmentIdx]
            val endIdx = gpsIndices[segmentIdx + 1]
            
            val startLat = gpsPoints[segmentIdx].lat
            val startLon = gpsPoints[segmentIdx].lon
            val endLat = gpsPoints[segmentIdx + 1].lat
            val endLon = gpsPoints[segmentIdx + 1].lon
            
            finalLats[startIdx] = startLat
            finalLons[startIdx] = startLon
            
            val deltaLat = endLat - startLat
            val deltaLon = endLon - startLon
            val segmentYaw = atan2(deltaLon * metersPerDegreeLon, deltaLat * METERS_PER_DEGREE_LAT)
            
            val segmentDistance = sqrt((deltaLat * METERS_PER_DEGREE_LAT).pow(2) + (deltaLon * metersPerDegreeLon).pow(2))
            val segmentTime = timestamps[endIdx] - timestamps[startIdx]
            val avgSpeed = if (segmentTime > 0) segmentDistance / segmentTime else 0.0
            
            var currentYaw = segmentYaw
            var vNorth = avgSpeed * cos(segmentYaw)
            var vEast = avgSpeed * sin(segmentYaw)
            
            for (i in startIdx until endIdx) {
                val dt = timestamps[i + 1] - timestamps[i]
                
                val gz = interpolateIMUValue(timestamps[i], imuTimestamps, gyroZ)
                val ax = interpolateIMUValue(timestamps[i], imuTimestamps, accelX)
                val ay = interpolateIMUValue(timestamps[i], imuTimestamps, accelY)
                
                currentYaw += gz * dt
                
                val accelN = ay * cos(currentYaw) - ax * sin(currentYaw)
                val accelE = ay * sin(currentYaw) + ax * cos(currentYaw)
                
                vNorth += accelN * dt * params.imuGain
                vEast += accelE * dt * params.imuGain
                
                val targetVN = avgSpeed * cos(segmentYaw)
                val targetVE = avgSpeed * sin(segmentYaw)
                
                vNorth = vNorth * (1 - params.complementaryFilterAlpha) + targetVN * params.complementaryFilterAlpha
                vEast = vEast * (1 - params.complementaryFilterAlpha) + targetVE * params.complementaryFilterAlpha
                
                val deltaN = vNorth * dt
                val deltaE = vEast * dt
                
                finalLats[i + 1] = finalLats[i] + deltaN / METERS_PER_DEGREE_LAT
                finalLons[i + 1] = finalLons[i] + deltaE / metersPerDegreeLon
            }
            
            finalLats[endIdx] = endLat
            finalLons[endIdx] = endLon
        }
        
        if (params.gaussianSmoothSigma > 1) {
            applyGaussianSmooth(finalLats, params.gaussianSmoothSigma.toInt())
            applyGaussianSmooth(finalLons, params.gaussianSmoothSigma.toInt())
        }
        
        return finalLats.mapIndexed { i, lat -> Pair(lat, finalLons[i]) }
    }
    
    private fun binarySearchGPS(t: Double, timestamps: DoubleArray): Int {
        var low = 0
        var high = timestamps.size - 1
        
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (timestamps[mid] <= t) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return low
    }
    
    private fun binarySearchTimestamp(timestamps: DoubleArray, t: Double): Int {
        var low = 0
        var high = timestamps.size - 1
        
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (timestamps[mid] <= t) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return low
    }
    
    private fun interpolateIMUValue(t: Double, timestamps: DoubleArray, values: DoubleArray): Double {
        val idx = binarySearchGPS(t, timestamps)
        if (idx >= timestamps.size - 1) return values.last()
        if (idx < 0) return values.first()
        
        val t0 = timestamps[idx]
        val t1 = timestamps[idx + 1]
        if (t1 == t0) return values[idx]
        
        val progress = (t - t0) / (t1 - t0)
        return lerp(values[idx], values[idx + 1], progress)
    }
    
    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }
    
    private fun applyGaussianSmooth(data: DoubleArray, sigma: Int) {
        val size = 6 * sigma + 1
        val kernel = DoubleArray(size)
        var sum = 0.0
        
        for (i in kernel.indices) {
            val x = i - 3 * sigma
            kernel[i] = exp(-x * x.toDouble() / (2 * sigma * sigma))
            sum += kernel[i]
        }
        
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        
        val halfSize = size / 2
        val result = DoubleArray(data.size)
        
        for (i in data.indices) {
            var value = 0.0
            var weightSum = 0.0
            
            for (j in kernel.indices) {
                val idx = i + j - halfSize
                if (idx in data.indices) {
                    value += data[idx] * kernel[j]
                    weightSum += kernel[j]
                }
            }
            
            result[i] = if (weightSum > 0) value / weightSum else data[i]
        }
        
        for (i in data.indices) {
            data[i] = result[i]
        }
    }
}
