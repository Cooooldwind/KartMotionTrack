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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KartTracker(private val context: Context) {
    private val TAG = "KartTracker"
    
    private val imuSampler = IMUSampler(context)
    private val gpsCollector = GPSCollector(context)
    private val rawDataWriter = RawDataWriter(context)
    
    private var isWaitingForGPS = false
    private var hasGPSLock = false
    private var isRecording = false
    private var gpsPointCount = 0
    private var imuPointCount = 0
    
    var onStatusUpdate: ((String) -> Unit)? = null
    var onGPSLockObtained: (() -> Unit)? = null
    
    data class RecordStats(
        val gpsPoints: Int,
        val imuPoints: Int,
        val duration: Long
    )
    
    private var startTimestamp: String = ""
    private var startTimeMillis: Long = 0
    
    fun start() {
        if (isRecording || isWaitingForGPS) return
        
        Log.d(TAG, "等待GPS锁定...")
        
        gpsPointCount = 0
        imuPointCount = 0
        hasGPSLock = false
        isWaitingForGPS = true
        
        gpsCollector.addListener { onGPSForLock(it) }
        gpsCollector.start()
        
        updateStatus("等待GPS锁定...")
    }
    
    fun stop(): RecordStats {
        if (!isRecording && !isWaitingForGPS) {
            return RecordStats(0, 0, 0)
        }
        
        if (isWaitingForGPS) {
            Log.d(TAG, "取消等待GPS锁定")
            isWaitingForGPS = false
            gpsCollector.removeListener { onGPSForLock(it) }
            gpsCollector.stop()
            updateStatus("准备就绪")
            return RecordStats(0, 0, 0)
        }
        
        if (gpsPointCount == 0) {
            Log.w(TAG, "没有GPS数据，无法停止")
            updateStatus("没有GPS数据，无法停止")
            return RecordStats(0, 0, 0)
        }
        
        Log.d(TAG, "停止追踪")
        
        isRecording = false
        
        imuSampler.stopSampling()
        gpsCollector.stop()
        
        val duration = System.currentTimeMillis() - startTimeMillis
        
        val stats = RecordStats(gpsPointCount, imuPointCount, duration)
        updateStatus("记录完成: GPS ${gpsPointCount}点, IMU ${imuPointCount}点")
        
        return stats
    }
    
    fun isWaiting(): Boolean = isWaitingForGPS
    
    fun isRecording(): Boolean = isRecording
    
    private fun onGPSForLock(data: GPSData) {
        if (!isWaitingForGPS) return
        
        if (data.accuracy > 100f) {
            updateStatus("GPS精度不足，等待中...")
            return
        }
        
        Log.d(TAG, "GPS锁定成功，开始记录")
        isWaitingForGPS = false
        hasGPSLock = true
        
        startTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        startTimeMillis = System.currentTimeMillis()
        
        rawDataWriter.start(startTimestamp)
        
        isRecording = true
        
        gpsCollector.removeListener { onGPSForLock(it) }
        gpsCollector.addListener { onGPSData(it) }
        
        imuSampler.addListener { onIMUData(it) }
        imuSampler.startSampling()
        
        onGPSLockObtained?.invoke()
        updateStatus("正在记录: GPS 0点, IMU 0点")
    }
    
    private fun onIMUData(data: IMUData) {
        if (!isRecording) return
        
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
        
        updateStatus("正在记录: GPS ${gpsPointCount}点, IMU ${imuPointCount}点")
    }
    
    private fun onGPSData(data: GPSData) {
        if (!isRecording) return
        
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
        if (isRecording) {
            stop()
        }
        if (isWaitingForGPS) {
            isWaitingForGPS = false
            gpsCollector.stop()
        }
    }
}
