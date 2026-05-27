package com.karttracker.storage

import android.content.Context
import com.google.gson.Gson
import com.karttracker.model.ProcessedTrack
import com.karttracker.model.TrackPoint
import com.karttracker.processing.AdaptiveInterpolation
import com.karttracker.processing.IMUFusionProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class TrackProcessor(private val context: Context) {
    private val gson = Gson()
    private val rawDataReader = RawDataReader()
    private val interpolation = AdaptiveInterpolation(100)
    private val imuFusion = IMUFusionProcessor(100)
    
    data class ProcessResult(
        val success: Boolean,
        val processedFile: File?,
        val pointCount: Int,
        val errorMessage: String?
    )
    
    data class ProcessProgress(
        val phase: String,
        val progress: Int,
        val currentPoint: Int,
        val totalPoints: Int
    )
    
    suspend fun processTrack(
        rawFilePath: String,
        onProgress: ((ProcessProgress) -> Unit)? = null
    ): ProcessResult = withContext(Dispatchers.Default) {
        try {
            onProgress?.invoke(ProcessProgress("读取GPS数据", 0, 0, 0))
            
            val allData = rawDataReader.readAll(rawFilePath)
            val gpsPoints = allData.filter { 
                it.type == com.karttracker.model.DataType.GPS && 
                it.gpsLat != null && it.gpsLon != null 
            }.sortedBy { it.timestamp }
            val imuPoints = allData.filter { 
                it.type == com.karttracker.model.DataType.IMU 
            }.sortedBy { it.timestamp }
            
            if (gpsPoints.size < 2) {
                return@withContext ProcessResult(
                    success = false,
                    processedFile = null,
                    pointCount = 0,
                    errorMessage = "GPS数据点不足"
                )
            }
            
            onProgress?.invoke(ProcessProgress("处理GPS Kalman滤波", 20, 0, gpsPoints.size))
            
            val firstTime = gpsPoints.firstOrNull()?.timestamp ?: 0.0
            val lastTime = gpsPoints.lastOrNull()?.timestamp ?: 0.0
            val totalDuration = lastTime - firstTime
            val trackPointsToGenerate = (totalDuration * 100).toInt()
            
            onProgress?.invoke(ProcessProgress("生成100Hz轨迹点", 40, 0, trackPointsToGenerate))
            
            val trackPoints = interpolation.interpolate(gpsPoints, imuPoints)
            
            if (trackPoints.isEmpty()) {
                return@withContext ProcessResult(
                    success = false,
                    processedFile = null,
                    pointCount = 0,
                    errorMessage = "插值失败"
                )
            }
            
            onProgress?.invoke(ProcessProgress("保存轨迹文件", 90, 0, trackPoints.size))
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val rawFileName = File(rawFilePath).nameWithoutExtension
            val processedFileName = "processed_${rawFileName}_$timestamp.json"
            val processedFile = File(context.getExternalFilesDir(null), processedFileName)
            
            processedFile.bufferedWriter().use { writer ->
                trackPoints.forEach { point ->
                    writer.write(gson.toJson(point) + "\n")
                }
            }
            
            onProgress?.invoke(ProcessProgress("完成", 100, trackPoints.size, trackPoints.size))
            
            ProcessResult(
                success = true,
                processedFile = processedFile,
                pointCount = trackPoints.size,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            ProcessResult(
                success = false,
                processedFile = null,
                pointCount = 0,
                errorMessage = e.message
            )
        }
    }
    
    fun loadProcessedTrack(filePath: String): List<TrackPoint> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        
        return file.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        gson.fromJson(line, TrackPoint::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                .toList()
        }
    }
    
    fun getProcessedTrackInfo(filePath: String): ProcessedTrack? {
        val points = loadProcessedTrack(filePath)
        if (points.isEmpty()) return null
        
        val file = File(filePath)
        val rawFilePath = file.nameWithoutExtension
            .replace("processed_", "")
            .substringBefore("_")
            .let { "raw_$it.json" }
        
        val startPoint = points.first()
        val endPoint = points.last()
        val duration = ((endPoint.timestamp - startPoint.timestamp) * 1000).toLong()
        val maxSpeed = points.maxOfOrNull { it.speed } ?: 0f
        val totalDistance = calculateTotalDistance(points)
        
        return ProcessedTrack(
            fileName = file.name,
            filePath = filePath,
            rawFilePath = File(context.getExternalFilesDir(null), rawFilePath).absolutePath,
            startTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(startPoint.timestamp.toLong() * 1000)),
            endTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(endPoint.timestamp.toLong() * 1000)),
            duration = duration,
            pointCount = points.size,
            samplingRate = 100,
            maxSpeed = maxSpeed,
            totalDistance = totalDistance
        )
    }
    
    private fun calculateTotalDistance(points: List<TrackPoint>): Double {
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += haversine(
                points[i-1].lat, points[i-1].lon,
                points[i].lat, points[i].lon
            )
        }
        return distance
    }
    
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }
}
