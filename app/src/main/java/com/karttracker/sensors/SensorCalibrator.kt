package com.karttracker.sensors

class SensorCalibrator {
    private val accelBias = FloatArray(3)
    private val gyroBias = FloatArray(3)
    private var isCalibrated = false
    
    fun startCalibration() {
        accelBias.fill(0f)
        gyroBias.fill(0f)
        isCalibrated = false
    }
    
    fun addCalibrationSample(accel: FloatArray, gyro: FloatArray) {
        for (i in 0..2) {
            accelBias[i] += accel[i]
            gyroBias[i] += gyro[i]
        }
    }
    
    fun finishCalibration(sampleCount: Int) {
        if (sampleCount > 0) {
            for (i in 0..2) {
                accelBias[i] /= sampleCount.toFloat()
                gyroBias[i] /= sampleCount.toFloat()
            }
            isCalibrated = true
        }
    }
    
    fun applyCalibration(accel: FloatArray, gyro: FloatArray): Pair<FloatArray, FloatArray> {
        val calibratedAccel = FloatArray(3)
        val calibratedGyro = FloatArray(3)
        
        for (i in 0..2) {
            calibratedAccel[i] = accel[i] - accelBias[i]
            calibratedGyro[i] = gyro[i] - gyroBias[i]
        }
        
        return Pair(calibratedAccel, calibratedGyro)
    }
    
    fun isCalibrated(): Boolean = isCalibrated
}
