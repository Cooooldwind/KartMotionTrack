package com.karttracker.model

data class GPSData(
    val timestamp: Double,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float
)
