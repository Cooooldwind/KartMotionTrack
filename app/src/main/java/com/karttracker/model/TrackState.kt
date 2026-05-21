package com.karttracker.model

data class TrackState(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val vEast: Float,
    val vNorth: Float,
    val vUp: Float,
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)
