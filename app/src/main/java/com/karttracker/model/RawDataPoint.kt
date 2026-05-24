package com.karttracker.model

data class RawDataPoint(
    val timestamp: Double,
    val type: DataType,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsAlt: Double? = null,
    val gpsSpeed: Float? = null,
    val gpsBearing: Float? = null,
    val gpsAccuracy: Float? = null,
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val gyroX: Float? = null,
    val gyroY: Float? = null,
    val gyroZ: Float? = null,
    val magX: Float? = null,
    val magY: Float? = null,
    val magZ: Float? = null
)

enum class DataType {
    GPS, IMU
}
