package com.karttracker.processing

class KalmanFilter1D(
    private val processNoise: Float = 0.01f,
    private val measurementNoise: Float = 0.1f
) {
    private var estimate = FloatArray(3)
    private var errorCovariance = FloatArray(3) { 1f }
    
    fun apply(input: FloatArray): FloatArray {
        val output = FloatArray(3)
        for (i in 0..2) {
            output[i] = applySingle(input[i], i)
        }
        return output
    }
    
    private fun applySingle(measurement: Float, index: Int): Float {
        val kalmanGain = (errorCovariance[index] + processNoise) /
                (errorCovariance[index] + processNoise + measurementNoise)
        
        estimate[index] += kalmanGain * (measurement - estimate[index])
        errorCovariance[index] = (1 - kalmanGain) * (errorCovariance[index] + processNoise)
        
        return estimate[index]
    }
    
    fun reset() {
        estimate.fill(0f)
        errorCovariance.fill(1f)
    }
}
