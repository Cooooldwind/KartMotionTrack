package com.karttracker.model

data class TrackPoint(
    val timestamp: Double,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Float,
    val accuracy: Float,
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)
