package com.karttracker.storage

import android.content.Context
import com.google.gson.Gson
import com.karttracker.model.TrackData
import com.karttracker.model.TrackPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TrackFileManager(private val context: Context) {
    private val gson = Gson()
    private val filesDir = context.getExternalFilesDir(null)
    
    fun getAllTrackFiles(): List<TrackData> {
        val trackFiles = mutableListOf<TrackData>()
        
        filesDir?.listFiles { file ->
            file.name.startsWith("raw_track_") || file.name.startsWith("processed_") && file.name.endsWith(".json")
        }?.forEach { file ->
            try {
                if (file.name.startsWith("raw_track_")) {
                    val trackData = parseRawTrackFile(file)
                    trackFiles.add(trackData)
                } else if (file.name.startsWith("processed_")) {
                    val processedFile = findRawFileForProcessed(file)
                    if (processedFile != null) {
                        trackFiles.add(createTrackDataFromProcessed(file, processedFile))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return trackFiles.sortedByDescending { it.startTime }
    }
    
    private fun parseRawTrackFile(file: File): TrackData {
        val reader = RawDataReader()
        val stats = reader.getStatistics(file.absolutePath)
        
        val startTime = file.name.removePrefix("raw_track_").removeSuffix(".json")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val startDate = dateFormat.parse(startTime)
        
        val duration = if (stats.startTime != null && stats.endTime != null) {
            ((stats.endTime - stats.startTime) * 1000).toLong()
        } else {
            0L
        }
        
        val endDate = startDate?.let { Date(it.time + duration) }
        val endTimeStr = endDate?.let { dateFormat.format(it) } ?: startTime
        
        val processedFile = findProcessedFileForRaw(file)
        
        return TrackData(
            fileName = file.name,
            filePath = file.absolutePath,
            startTime = startTime,
            endTime = endTimeStr,
            duration = duration,
            gpsPointCount = stats.gpsPointCount,
            imuPointCount = stats.imuPointCount,
            isProcessed = processedFile != null,
            processedFilePath = processedFile?.absolutePath,
            maxSpeed = 0f
        )
    }
    
    private fun createTrackDataFromProcessed(processedFile: File, rawFile: File): TrackData {
        val processor = TrackProcessor(context)
        val info = processor.getProcessedTrackInfo(processedFile.absolutePath)
        
        val startTime = rawFile.name.removePrefix("raw_track_").removeSuffix(".json")
        
        return TrackData(
            fileName = rawFile.name,
            filePath = rawFile.absolutePath,
            startTime = startTime,
            endTime = info?.endTime ?: startTime,
            duration = info?.duration ?: 0L,
            gpsPointCount = 0,
            imuPointCount = 0,
            isProcessed = true,
            processedFilePath = processedFile.absolutePath,
            maxSpeed = info?.maxSpeed ?: 0f
        )
    }
    
    private fun findRawFileForProcessed(processedFile: File): File? {
        val processedName = processedFile.nameWithoutExtension
            .replace("processed_", "")
            .substringBefore("_")
        val rawFileName = "raw_$processedName.json"
        
        return filesDir?.listFiles()?.find { it.name == rawFileName }
    }
    
    private fun findProcessedFileForRaw(rawFile: File): File? {
        val rawName = rawFile.nameWithoutExtension.removePrefix("raw_")
        return filesDir?.listFiles()?.find { 
            it.name.startsWith("processed_") && 
            it.nameWithoutExtension.contains(rawName)
        }
    }
    
    fun loadTrackPoints(filePath: String): List<TrackPoint> {
        val processor = TrackProcessor(context)
        return processor.loadProcessedTrack(filePath)
    }
    
    fun exportToGPX(filePath: String, onProgress: ((Int) -> Unit)? = null): String {
        val points = loadTrackPoints(filePath)
        if (points.isEmpty()) return ""
        
        val outputFile = File(filesDir, "export_${File(filePath).nameWithoutExtension}.gpx")
        val writer = outputFile.bufferedWriter()
        val totalPoints = points.size
        val gpxDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        writer.use {
            it.write("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="KartMotionTrack"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>${File(filePath).name}</name>
    <time>${gpxDateFormat.format(Date())}</time>
  </metadata>
  <trk>
    <name>Track</name>
    <trkseg>
""")
            
            points.forEachIndexed { index, point ->
                it.write("      <trkpt lat=\"${point.lat}\" lon=\"${point.lon}\">\n")
                it.write("        <ele>${point.alt}</ele>\n")
                val timestamp = Date((point.timestamp * 1000).toLong())
                it.write("        <time>${gpxDateFormat.format(timestamp)}</time>\n")
                it.write("      </trkpt>\n")
                
                if (index % 100 == 0 || index == totalPoints - 1) {
                    onProgress?.invoke(((index + 1).toFloat() / totalPoints * 100).toInt())
                }
            }
            
            it.write("""    </trkseg>
  </trk>
</gpx>""")
        }
        
        onProgress?.invoke(100)
        return outputFile.absolutePath
    }
    
    fun exportToCSV(filePath: String, onProgress: ((Int) -> Unit)? = null): String {
        val points = loadTrackPoints(filePath)
        if (points.isEmpty()) return ""
        
        val outputFile = File(filesDir, "export_${File(filePath).nameWithoutExtension}.csv")
        val writer = outputFile.bufferedWriter()
        val totalPoints = points.size
        
        writer.use {
            it.write("timestamp,lat,lon,alt,speed,accuracy,roll,pitch,yaw\n")
            
            points.forEachIndexed { index, point ->
                it.write("${point.timestamp},${point.lat},${point.lon},${point.alt},${point.speed},${point.accuracy},${point.roll},${point.pitch},${point.yaw}\n")
                
                if (index % 100 == 0 || index == totalPoints - 1) {
                    onProgress?.invoke(((index + 1).toFloat() / totalPoints * 100).toInt())
                }
            }
        }
        
        onProgress?.invoke(100)
        return outputFile.absolutePath
    }
    
    fun deleteTrackFile(filePath: String): Boolean {
        val file = File(filePath)
        val deleted = file.delete()
        
        val processedFile = findProcessedFileForRaw(file)
        processedFile?.delete()
        
        return deleted
    }
    
    fun getTrackStatus(filePath: String): TrackData? {
        return getAllTrackFiles().find { it.filePath == filePath }
    }
}
