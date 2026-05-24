package com.karttracker.storage

import android.content.Context
import com.google.gson.GsonBuilder
import com.karttracker.model.RawDataPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RawDataWriter(private val context: Context) {
    private val gson = GsonBuilder().create()
    private var file: File? = null
    
    fun start(timestamp: String): File {
        val fileName = "raw_track_$timestamp.json"
        file = File(context.getExternalFilesDir(null), fileName)
        file?.createNewFile()
        return file!!
    }
    
    fun writePoint(point: RawDataPoint) {
        file?.appendText(gson.toJson(point) + "\n")
    }
    
    fun writeGPS(
        timestamp: Double,
        lat: Double, lon: Double, alt: Double,
        speed: Float, bearing: Float, accuracy: Float
    ) {
        val point = RawDataPoint(
            timestamp = timestamp,
            type = com.karttracker.model.DataType.GPS,
            gpsLat = lat, gpsLon = lon, gpsAlt = alt,
            gpsSpeed = speed, gpsBearing = bearing, gpsAccuracy = accuracy
        )
        writePoint(point)
    }
    
    fun writeIMU(
        timestamp: Double,
        accelX: Float, accelY: Float, accelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        magX: Float, magY: Float, magZ: Float
    ) {
        val point = RawDataPoint(
            timestamp = timestamp,
            type = com.karttracker.model.DataType.IMU,
            accelX = accelX, accelY = accelY, accelZ = accelZ,
            gyroX = gyroX, gyroY = gyroY, gyroZ = gyroZ,
            magX = magX, magY = magY, magZ = magZ
        )
        writePoint(point)
    }
    
    fun close(): File? {
        val result = file
        file = null
        return result
    }
    
    fun getFile(): File? = file
}
