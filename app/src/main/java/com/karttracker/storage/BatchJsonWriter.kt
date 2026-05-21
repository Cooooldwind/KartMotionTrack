package com.karttracker.storage

import com.google.gson.Gson
import com.karttracker.model.TrackPoint
import java.io.File

class BatchJsonWriter(
    private val filePath: String,
    private val batchSize: Int = 1000
) {
    private val buffer = CircularBuffer<TrackPoint>(10000)
    private val gson = Gson()
    private val file = File(filePath)
    
    init {
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        if (!file.exists()) {
            file.createNewFile()
        }
    }
    
    fun writePoint(point: TrackPoint) {
        buffer.add(point)
        if (buffer.size() >= batchSize) {
            flush()
        }
    }
    
    fun flush() {
        val points = buffer.drainTo(batchSize)
        if (points.isEmpty()) return
        
        val jsonString = points.joinToString("\n") { gson.toJson(it) } + "\n"
        file.appendText(jsonString)
    }
    
    fun close() {
        flush()
    }
    
    fun getFilePath(): String = file.absolutePath
    
    fun getCurrentBufferSize(): Int = buffer.size()
}
