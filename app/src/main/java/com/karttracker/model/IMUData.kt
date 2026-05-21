package com.karttracker.model

data class IMUData(
    val timestamp: Double,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val magX: Float,
    val magY: Float,
    val magZ: Float
)
