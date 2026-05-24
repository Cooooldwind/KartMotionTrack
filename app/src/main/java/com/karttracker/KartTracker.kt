package com.karttracker

import android.content.Context
import android.util.Log
import com.karttracker.model.GPSData
import com.karttracker.model.IMUData
import com.karttracker.storage.RawDataWriter
import com.karttracker.sensors.GPSCollector
import com.karttracker.sensors.IMUSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KartTracker(private val context: Context) {
    private val TAG = "KartTracker"
    
    private val imuSampler = IMUSampler(context)
    private val gpsCollector = GPSCollector(context)
    private val rawDataWriter = RawDataWriter(context)
    
    private var isRunning = false
    private var gpsPointCount = 0
    private var imuPointCount = 0
    
    var onStatusUpdate: ((String) -> Unit)? = null
    
    data class RecordStats(
        val gpsPoints: Int,
        val imuPoints: Int,
        val duration: Long
    )
    
    private var startTimestamp: String = ""
    private var startTimeMillis: Long = 0
    
    fun start() {
        if (isRunning) return
        
        Log.d(TAG, "开始追踪（记录原始数据）")
        
        gpsPointCount = 0
        imuPointCount = 0
        startTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        startTimeMillis = System.currentTimeMillis()
        
        rawDataWriter.start(startTimestamp)
        
        isRunning = true
        
        imuSampler.addListener { onIMUData(it) }
        gpsCollector.addListener { onGPSData(it) }
        
        imuSampler.startSampling()
        gpsCollector.start()
        
        updateStatus("正在记录数据...")
    }
    
    fun stop(): RecordStats {
        if (!isRunning) return RecordStats(0, 0, 0)
        
        Log.d(TAG, "停止追踪")
        
        isRunning = false
        
        imuSampler.stopSampling()
        gpsCollector.stop()
        
        val file = rawDataWriter.close()
        val duration = System.currentTimeMillis() - startTimeMillis
        
        val stats = RecordStats(gpsPointCount, imuPointCount, duration)
        updateStatus("记录完成: GPS ${gpsPointCount}点, IMU ${imuPointCount}点")
        
        return stats
    }
    
    fun isRunning(): Boolean = isRunning
    
    private fun onIMUData(data: IMUData) {
        if (!isRunning) return
        
        rawDataWriter.writeIMU(
            timestamp = data.timestamp,
            accelX = data.accelX,
            accelY = data.accelY,
            accelZ = data.accelZ,
            gyroX = data.gyroX,
            gyroY = data.gyroY,
            gyroZ = data.gyroZ,
            magX = data.magX,
            magY = data.magY,
            magZ = data.magZ
        )
        imuPointCount++
    }
    
    private fun onGPSData(data: GPSData) {
        if (!isRunning) return
        
        if (data.accuracy > 100f) {
            Log.w(TAG, "GPS精度太差，忽略")
            return
        }
        
        rawDataWriter.writeGPS(
            timestamp = data.timestamp,
            lat = data.lat,
            lon = data.lon,
            alt = data.alt,
            speed = data.speed,
            bearing = data.bearing,
            accuracy = data.accuracy
        )
        gpsPointCount++
        
        updateStatus("正在记录: GPS ${gpsPointCount}点, IMU ${imuPointCount}点")
    }
    
    private fun updateStatus(message: String) {
        GlobalScope.launch(Dispatchers.Main) {
            onStatusUpdate?.invoke(message)
        }
    }
    
    fun destroy() {
        if (isRunning) {
            stop()
        }
    }
}
