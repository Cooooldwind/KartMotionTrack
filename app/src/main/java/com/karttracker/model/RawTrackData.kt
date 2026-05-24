package com.karttracker.model

data class RawTrackData(
    val fileName: String,
    val filePath: String,
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val gpsPointCount: Int,
    val imuPointCount: Int,
    val isProcessed: Boolean = false,
    val processedFilePath: String? = null
)
