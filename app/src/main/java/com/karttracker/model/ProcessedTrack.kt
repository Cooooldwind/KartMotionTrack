package com.karttracker.model

data class ProcessedTrack(
    val fileName: String,
    val filePath: String,
    val rawFilePath: String,
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val pointCount: Int,
    val samplingRate: Int = 100,
    val maxSpeed: Float,
    val totalDistance: Double
)
