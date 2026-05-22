package com.karttracker.storage

import android.content.Context
import com.google.gson.Gson
import com.karttracker.model.TrackData
import com.karttracker.model.TrackPoint
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class TrackFileManager(private val context: Context) {
    private val gson = Gson()
    private val filesDir = context.getExternalFilesDir(null)
    
    fun getAllTrackFiles(): List<TrackData> {
        val trackFiles = mutableListOf<TrackData>()
        
        filesDir?.listFiles { file ->
            file.name.startsWith("kart_track_") && file.name.endsWith(".json")
        }?.forEach { file ->
            try {
                val trackData = parseTrackFile(file)
                trackFiles.add(trackData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return trackFiles.sortedByDescending { it.startTime }
    }
    
    fun parseTrackFile(file: File): TrackData {
        val points = mutableListOf<TrackPoint>()
        file.bufferedReader().forEachLine { line ->
            if (line.isNotBlank()) {
                try {
                    val point = gson.fromJson(line, TrackPoint::class.java)
                    points.add(point)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val startTime = file.name.removePrefix("kart_track_").removeSuffix(".json")
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val startDate = dateFormat.parse(startTime)
        
        val duration = if (points.size >= 2) {
            ((points.last().timestamp - points.first().timestamp) * 1000).toLong()
        } else {
            0L
        }
        
        val endDate = startDate?.let { Date(it.time + duration) }
        val endTime = endDate?.let { dateFormat.format(it) } ?: startTime
        
        val sortedPoints = points.sortedBy { it.timestamp }
        
        return TrackData(
            fileName = file.name,
            filePath = file.absolutePath,
            startTime = startTime,
            endTime = endTime,
            pointCount = sortedPoints.size.toLong(),
            duration = if (sortedPoints.size >= 2) {
                ((sortedPoints.last().timestamp - sortedPoints.first().timestamp) * 1000).toLong()
            } else {
                0L
            },
            maxSpeed = sortedPoints.maxOfOrNull { it.speed } ?: 0f
        )
    }
    
    fun loadTrackPoints(filePath: String): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val file = File(filePath)
        
        if (file.exists()) {
            file.bufferedReader().forEachLine { line ->
                if (line.isNotBlank()) {
                    try {
                        val point = gson.fromJson(line, TrackPoint::class.java)
                        points.add(point)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        
        return points.sortedBy { it.timestamp }
    }
    
    fun exportToGPX(filePath: String): String {
        val points = loadTrackPoints(filePath)
        if (points.isEmpty()) return ""
        
        val outputFile = File(filesDir, "export_${File(filePath).nameWithoutExtension}.gpx")
        val writer = FileWriter(outputFile)
        
        writer.write("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="KartMotionTrack">
  <metadata>
    <name>${File(filePath).name}</name>
    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}</time>
  </metadata>
  <trk>
    <name>Track</name>
    <trkseg>
""")
        
        points.forEach { point ->
            writer.write("      <trkpt lat=\"${point.lat}\" lon=\"${point.lon}\">\n")
            writer.write("        <ele>${point.alt}</ele>\n")
            writer.write("        <speed>${point.speed}</speed>\n")
            writer.write("      </trkpt>\n")
        }
        
        writer.write("""    </trkseg>
  </trk>
</gpx>""")
        
        writer.close()
        return outputFile.absolutePath
    }
    
    fun exportToCSV(filePath: String): String {
        val points = loadTrackPoints(filePath)
        if (points.isEmpty()) return ""
        
        val outputFile = File(filesDir, "export_${File(filePath).nameWithoutExtension}.csv")
        val writer = FileWriter(outputFile)
        
        writer.write("timestamp,lat,lon,alt,speed,accuracy,roll,pitch,yaw\n")
        
        points.forEach { point ->
            writer.write("${point.timestamp},${point.lat},${point.lon},${point.alt},${point.speed},${point.accuracy},${point.roll},${point.pitch},${point.yaw}\n")
        }
        
        writer.close()
        return outputFile.absolutePath
    }
    
    fun deleteTrackFile(filePath: String): Boolean {
        return File(filePath).delete()
    }
}
