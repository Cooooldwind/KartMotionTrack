package com.karttracker.fusion

import com.karttracker.model.TrackState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EKF {
    private var x = FloatArray(11)
    private var P = FloatArray(11) { 0.1f }
    private var initialized = false
    
    // 约束参数
    private val MAX_SPEED = 50f  // 最大速度 50 m/s（约180 km/h）
    private val MAX_ACCEL = 10f   // 最大加速度 10 m/s²（约1G）
    private val MAX_POS_CHANGE = 1f  // 单次预测最大位置变化（米）
    
    fun initialize(lat: Double, lon: Double, alt: Double) {
        x[0] = lat.toFloat()
        x[1] = lon.toFloat()
        x[2] = alt.toFloat()
        x[3] = 0f
        x[4] = 0f
        x[5] = 0f
        x[6] = 0f
        x[7] = 0f
        x[8] = 0f
        x[9] = 0f
        x[10] = 0f
        initialized = true
    }
    
    fun isInitialized(): Boolean = initialized
    
    fun predict(linearAccelWorld: FloatArray, angularVelWorld: FloatArray, dt: Float) {
        if (!initialized) return
        
        // 获取当前速度
        val currentSpeed = sqrt(x[3] * x[3] + x[4] * x[4] + x[5] * x[5])
        
        // 只有当速度超过阈值时才进行积分（避免静止时漂移）
        if (currentSpeed < 0.5f && sqrt(linearAccelWorld[0] * linearAccelWorld[0] + 
                                         linearAccelWorld[1] * linearAccelWorld[1] + 
                                         linearAccelWorld[2] * linearAccelWorld[2]) < 1f) {
            // 静止状态，不积分
            return
        }
        
        var ax = linearAccelWorld[0]
        var ay = linearAccelWorld[1]
        var az = linearAccelWorld[2]
        
        // 限制加速度
        val accelMag = sqrt(ax * ax + ay * ay + az * az)
        if (accelMag > MAX_ACCEL) {
            val scale = MAX_ACCEL / accelMag
            ax *= scale
            ay *= scale
            az *= scale
        }
        
        // 预测速度
        var vxNew = x[3] + ax * dt
        var vyNew = x[4] + ay * dt
        var vzNew = x[5] + az * dt
        
        // 限制速度
        val newSpeed = sqrt(vxNew * vxNew + vyNew * vyNew + vzNew * vzNew)
        if (newSpeed > MAX_SPEED) {
            val scale = MAX_SPEED / newSpeed
            vxNew *= scale
            vyNew *= scale
            vzNew *= scale
        }
        
        // 预测位置变化（转换为经纬度变化）
        // 近似：1度纬度 ≈ 111111米，1度经度 ≈ 111111*cos(lat)米
        var latChange = vxNew * dt / 111111f
        var lonChange = vyNew * dt / (111111f * cos(Math.toRadians(x[0].toDouble())).toFloat())
        
        // 限制位置变化（单次预测不超过1米）
        val posChange = sqrt(latChange * latChange + lonChange * lonChange) * 111111f
        if (posChange > MAX_POS_CHANGE) {
            val scale = MAX_POS_CHANGE / posChange
            latChange *= scale
            lonChange *= scale
            vxNew *= scale
            vyNew *= scale
        }
        
        // 更新状态
        x[0] += latChange
        x[1] += lonChange
        x[2] += vzNew * dt
        
        x[3] = vxNew
        x[4] = vyNew
        x[5] = vzNew
        
        x[6] += angularVelWorld[0] * dt
        x[7] += angularVelWorld[1] * dt
        x[8] += angularVelWorld[2] * dt
        
        // 缓慢增加协方差
        for (i in P.indices) {
            P[i] = (P[i] + 0.0001f).coerceAtMost(1.0f)
        }
    }
    
    fun updateGPS(lat: Double, lon: Double, alt: Double, 
                  speed: Float, bearing: Float, accuracy: Float) {
        if (!initialized) return
        
        // 计算 GPS 位置与当前位置的距离
        val distanceMeters = calculateDistance(x[0].toDouble(), x[1].toDouble(), lat, lon)
        
        // 大幅提高 GPS 权重
        val gpsWeight = when {
            accuracy < 10f -> 0.95f
            accuracy < 20f -> 0.90f
            else -> 0.80f
        }
        
        // 如果距离超过 50 米，完全重置为 GPS 位置
        if (distanceMeters > 50) {
            x[0] = lat.toFloat()
            x[1] = lon.toFloat()
            x[2] = alt.toFloat()
            x[3] = 0f
            x[4] = 0f
            x[5] = 0f
        } else {
            // 否则大幅修正位置
            x[0] += (lat.toFloat() - x[0]) * gpsWeight
            x[1] += (lon.toFloat() - x[1]) * gpsWeight
            x[2] += (alt.toFloat() - x[2]) * gpsWeight
        }
        
        // 更新速度
        val vEast = speed * cos(Math.toRadians(bearing.toDouble())).toFloat()
        val vNorth = speed * sin(Math.toRadians(bearing.toDouble())).toFloat()
        
        val speedWeight = 0.8f
        x[3] += (vEast - x[3]) * speedWeight
        x[4] += (vNorth - x[4]) * speedWeight
        
        // 降低协方差
        for (i in 0..2) {
            P[i] *= (1 - gpsWeight)
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0  // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    
    fun getCurrentState(): TrackState {
        return TrackState(
            lat = x[0].toDouble(),
            lon = x[1].toDouble(),
            alt = x[2].toDouble(),
            vEast = x[3],
            vNorth = x[4],
            vUp = x[5],
            roll = x[6],
            pitch = x[7],
            yaw = x[8]
        )
    }
    
    fun getSpeed(): Float {
        return sqrt(x[3] * x[3] + x[4] * x[4] + x[5] * x[5])
    }
    
    fun reset() {
        x.fill(0f)
        P.fill(0.1f)
        initialized = false
    }
}
