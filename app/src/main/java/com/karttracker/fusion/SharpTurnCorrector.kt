package com.karttracker.fusion

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class SharpTurnCorrector {
    private val TAG = "SharpTurnCorrector"
    
    private var lastGyroZ = 0.0
    private var lastTimestamp = 0.0
    private var accumulatedAngle = 0.0
    private var accumulatedLateralOffset = 0.0
    
    private val lateralVelocityHistory = mutableListOf<Double>()
    private val maxHistorySize = 10
    
    companion object {
        private const val SHARP_TURN_ACCEL_THRESHOLD = 3.5
        private const val SHARP_TURN_GYRO_THRESHOLD = 1.5
        private const val MIN_TURN_DURATION = 0.3
        private const val LATERAL_ACCEL_SENSITIVITY = 0.15
        private const val VELOCITY_DECAY = 0.85
        private const val MAX_LATERAL_OFFSET = 5.0
    }
    
    private var turnStartTime = 0.0
    private var isInSharpTurn = false
    private var totalTimeInTurn = 0.0
    
    data class TurnState(
        val isSharpTurn: Boolean,
        val lateralOffset: Double,
        val turnAngle: Double,
        val lateralAcceleration: Double,
        val confidence: Double
    )
    
    fun reset() {
        lastGyroZ = 0.0
        lastTimestamp = 0.0
        accumulatedAngle = 0.0
        accumulatedLateralOffset = 0.0
        lateralVelocityHistory.clear()
        isInSharpTurn = false
        turnStartTime = 0.0
        totalTimeInTurn = 0.0
    }
    
    fun update(
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        quaternion: FloatArray,
        speed: Float,
        timestamp: Double
    ): TurnState {
        if (lastTimestamp <= 0) {
            lastTimestamp = timestamp
            return TurnState(false, 0.0, 0.0, 0.0, 0.0)
        }
        
        val dt = (timestamp - lastTimestamp).coerceIn(0.001, 0.1)
        lastTimestamp = timestamp
        
        val lateralAccel = calculateLateralAcceleration(accelX, accelY, accelZ, quaternion)
        val turnRate = abs(gyroZ.toDouble())
        
        val sharpTurnDetected = detectSharpTurn(lateralAccel, turnRate, speed, timestamp)
        
        if (sharpTurnDetected) {
            if (!isInSharpTurn) {
                isInSharpTurn = true
                turnStartTime = timestamp
                accumulatedAngle = 0.0
                accumulatedLateralOffset = 0.0
            }
            
            updateTurnDynamics(gyroZ, lateralAccel, speed, dt, timestamp)
            totalTimeInTurn += dt
        } else {
            if (isInSharpTurn && totalTimeInTurn >= MIN_TURN_DURATION) {
                decayLateralOffset(dt)
            } else {
                isInSharpTurn = false
                totalTimeInTurn = 0.0
                decayLateralOffset(dt)
            }
        }
        
        lastGyroZ = gyroZ.toDouble()
        
        val confidence = calculateConfidence(lateralAccel, turnRate, speed)
        
        return TurnState(
            isSharpTurn = isInSharpTurn && totalTimeInTurn >= MIN_TURN_DURATION,
            lateralOffset = accumulatedLateralOffset.coerceIn(-MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET),
            turnAngle = accumulatedAngle,
            lateralAcceleration = lateralAccel,
            confidence = confidence
        )
    }
    
    private fun calculateLateralAcceleration(
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        quaternion: FloatArray
    ): Double {
        val q0 = quaternion[0].toDouble()
        val q1 = quaternion[1].toDouble()
        val q2 = quaternion[2].toDouble()
        val q3 = quaternion[3].toDouble()
        
        val ax = accelX.toDouble()
        val ay = accelY.toDouble()
        val az = accelZ.toDouble()
        
        val q0q0 = q0 * q0
        val q0q1 = q0 * q1
        val q0q2 = q0 * q2
        val q0q3 = q0 * q3
        val q1q1 = q1 * q1
        val q1q2 = q1 * q2
        val q1q3 = q1 * q3
        val q2q2 = q2 * q2
        val q2q3 = q2 * q3
        val q3q3 = q3 * q3
        
        val rotationMatrix = Array(3) { FloatArray(3) }
        rotationMatrix[0][0] = (1 - 2 * (q2q2 + q3q3)).toFloat()
        rotationMatrix[0][1] = (2 * (q1q2 - q0q3)).toFloat()
        rotationMatrix[0][2] = (2 * (q1q3 + q0q2)).toFloat()
        rotationMatrix[1][0] = (2 * (q1q2 + q0q3)).toFloat()
        rotationMatrix[1][1] = (1 - 2 * (q1q1 + q3q3)).toFloat()
        rotationMatrix[1][2] = (2 * (q2q3 - q0q1)).toFloat()
        rotationMatrix[2][0] = (2 * (q1q3 - q0q2)).toFloat()
        rotationMatrix[2][1] = (2 * (q2q3 + q0q1)).toFloat()
        rotationMatrix[2][2] = (1 - 2 * (q1q1 + q2q2)).toFloat()
        
        val worldAccelY = rotationMatrix[1][0] * ax + rotationMatrix[1][1] * ay + rotationMatrix[1][2] * az
        
        return worldAccelY
    }
    
    private fun detectSharpTurn(
        lateralAccel: Double,
        turnRate: Double,
        speed: Float,
        timestamp: Double
    ): Boolean {
        if (speed < 2.0) {
            return false
        }
        
        val accelCondition = abs(lateralAccel) > SHARP_TURN_ACCEL_THRESHOLD
        val gyroCondition = turnRate > SHARP_TURN_GYRO_THRESHOLD
        
        val combinedScore = (abs(lateralAccel) / SHARP_TURN_ACCEL_THRESHOLD + 
                           turnRate / SHARP_TURN_GYRO_THRESHOLD) / 2.0
        
        lateralVelocityHistory.add(combinedScore)
        if (lateralVelocityHistory.size > maxHistorySize) {
            lateralVelocityHistory.removeAt(0)
        }
        
        val avgScore = lateralVelocityHistory.average()
        
        return avgScore > 1.2 || (accelCondition && gyroCondition)
    }
    
    private fun updateTurnDynamics(
        gyroZ: Float,
        lateralAccel: Double,
        speed: Float,
        dt: Double,
        timestamp: Double
    ) {
        val angleIncrement = gyroZ.toDouble() * dt
        accumulatedAngle += angleIncrement
        
        val direction = if (lateralAccel >= 0) 1.0 else -1.0
        val normalizedAccel = abs(lateralAccel) / 9.81
        
        val speedFactor = (speed / 20.0).coerceIn(0.5, 2.0)
        val turnFactor = abs(gyroZ.toDouble()) / 3.0
        
        val instantOffset = direction * normalizedAccel * LATERAL_ACCEL_SENSITIVITY * speedFactor * dt
        
        accumulatedLateralOffset += instantOffset
        
        if (abs(accumulatedLateralOffset) > MAX_LATERAL_OFFSET) {
            accumulatedLateralOffset = MAX_LATERAL_OFFSET * direction
        }
    }
    
    private fun decayLateralOffset(dt: Double) {
        accumulatedLateralOffset *= VELOCITY_DECAY
        if (abs(accumulatedLateralOffset) < 0.01) {
            accumulatedLateralOffset = 0.0
        }
    }
    
    private fun calculateConfidence(
        lateralAccel: Double,
        turnRate: Double,
        speed: Float
    ): Double {
        if (speed < 2.0) {
            return 0.0
        }
        
        val accelConfidence = (abs(lateralAccel) / 9.81).coerceIn(0.0, 1.0)
        val gyroConfidence = (abs(turnRate) / 5.0).coerceIn(0.0, 1.0)
        val speedConfidence = (speed / 30.0).coerceIn(0.0, 1.0)
        
        return (accelConfidence * 0.4 + gyroConfidence * 0.4 + speedConfidence * 0.2)
    }
    
    fun applyCorrection(
        baseLat: Double,
        baseLon: Double,
        bearing: Float,
        offset: Double
    ): Pair<Double, Double> {
        if (abs(offset) < 0.01) {
            return Pair(baseLat, baseLon)
        }
        
        val bearingRad = Math.toRadians(bearing.toDouble())
        val lateralBearing = bearingRad + Math.PI / 2
        
        val offsetMeters = offset
        
        val latOffset = offsetMeters * kotlin.math.cos(lateralBearing) / 111320.0
        val lonOffset = offsetMeters * kotlin.math.sin(lateralBearing) / (111320.0 * kotlin.math.cos(Math.toRadians(baseLat)))
        
        return Pair(baseLat + latOffset, baseLon + lonOffset)
    }
}
