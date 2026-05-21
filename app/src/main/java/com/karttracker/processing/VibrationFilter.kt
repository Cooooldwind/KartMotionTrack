package com.karttracker.processing

import com.karttracker.model.FilteredIMUData
import kotlin.math.sqrt

class VibrationFilter(
    private val sampleRate: Float = 200f
) {
    private val lowPass = LowPassFilter(cutoffFreq = 15f, sampleRate = sampleRate)
    private val medianFilter = MedianFilter(windowSize = 5)
    private val kalman = KalmanFilter1D(processNoise = 0.01f, measurementNoise = 0.1f)
    
    private var vibrationLevel = 0f
    private val accelHistory = mutableListOf<FloatArray>()
    private val historySize = 50
    
    fun filter(accel: FloatArray, gyro: FloatArray): FilteredIMUData {
        updateVibrationLevel(accel)
        
        val filterStrength = when {
            vibrationLevel > 0.5f -> 0.8f
            vibrationLevel > 0.2f -> 0.5f
            else -> 0.2f
        }
        
        val lowPassed = lowPass.apply(accel)
        val medianed = medianFilter.apply(lowPassed)
        val kalmanned = kalman.apply(medianed)
        
        return FilteredIMUData(
            accel = kalmanned,
            gyro = gyro,
            vibrationLevel = vibrationLevel,
            filterStrength = filterStrength
        )
    }
    
    private fun updateVibrationLevel(accel: FloatArray) {
        accelHistory.add(accel.copyOf())
        if (accelHistory.size > historySize) {
            accelHistory.removeAt(0)
        }
        
        if (accelHistory.size >= 10) {
            val magnitudes = accelHistory.map { 
                sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2])
            }
            val mean = magnitudes.average()
            val variance = magnitudes.map { (it - mean) * (it - mean) }.average()
            vibrationLevel = sqrt(variance).toFloat()
        }
    }
    
    fun reset() {
        lowPass.reset()
        medianFilter.reset()
        kalman.reset()
        vibrationLevel = 0f
        accelHistory.clear()
    }
}
