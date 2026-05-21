package com.karttracker.model

data class TrackData(
    val fileName: String,
    val filePath: String,
    val startTime: String,
    val endTime: String,
    val pointCount: Long,
    val duration: Long,
    val maxSpeed: Float
)
