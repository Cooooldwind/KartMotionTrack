package com.karttracker.storage

import com.google.gson.Gson
import com.karttracker.model.DataType
import com.karttracker.model.RawDataPoint
import java.io.File

class RawDataReader {
    private val gson = Gson()
    
    fun readAll(filePath: String): List<RawDataPoint> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        
        return file.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        gson.fromJson(line, RawDataPoint::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                .toList()
        }
    }
    
    fun getGPSPoints(filePath: String): List<RawDataPoint> {
        return readAll(filePath).filter { it.type == DataType.GPS }
    }
    
    fun getIMUPoints(filePath: String): List<RawDataPoint> {
        return readAll(filePath).filter { it.type == DataType.IMU }
    }
    
    fun getStatistics(filePath: String): RawStatistics {
        val all = readAll(filePath)
        val gpsPoints = all.filter { it.type == DataType.GPS }
        val imuPoints = all.filter { it.type == DataType.IMU }
        
        return RawStatistics(
            totalPoints = all.size,
            gpsPointCount = gpsPoints.size,
            imuPointCount = imuPoints.size,
            startTime = all.minOfOrNull { it.timestamp },
            endTime = all.maxOfOrNull { it.timestamp }
        )
    }
    
    data class RawStatistics(
        val totalPoints: Int,
        val gpsPointCount: Int,
        val imuPointCount: Int,
        val startTime: Double?,
        val endTime: Double?
    )
}
