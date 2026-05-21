package com.karttracker

import android.content.Context
import android.util.Log
import com.karttracker.model.GPSData
import com.karttracker.model.IMUData
import com.karttracker.model.TrackPoint
import com.karttracker.sensors.GPSCollector
import com.karttracker.sensors.IMUSampler
import com.karttracker.storage.BatchJsonWriter
import com.karttracker.fusion.SharpTurnCorrector
import com.karttracker.fusion.MadgwickFilter
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPS点之间的传感器辅助插值方案 - 批量版本（带详细进度）
 *
 * 原理：
 * 1. 收集GPS点（约1Hz）
 * 2. 收集IMU数据（100Hz）
 * 3. 在两个GPS点之间，批量生成插值点
 * 4. 实时显示插值进度和性能指标
 */
class KartTracker(private val context: Context) {
    private val TAG = "KartTracker"
    
    private val imuSampler = IMUSampler(context)
    private val gpsCollector = GPSCollector(context)
    
    private var jsonWriter: BatchJsonWriter? = null
    private var isRunning = false
    private var dataPointCount = 0L
    
    // 回调
    var onStatusUpdate: ((String) -> Unit)? = null
    var onTrackPointUpdate: ((TrackPoint) -> Unit)? = null
    var onProgressUpdate: ((ProgressInfo) -> Unit)? = null
    
    // GPS点队列
    private val gpsPoints = mutableListOf<GPSPoint>()
    
    // IMU数据缓存
    private val imuBuffer = mutableListOf<IMUData>()
    private val maxImuBufferSize = 200
    
    // 当前插值状态
    private var lastGPSPoint: GPSPoint? = null
    private var currentGPSPoint: GPSPoint? = null
    private var isInterpolating = false
    
    // 姿态跟踪
    private val madgwickFilter = MadgwickFilter()
    
    // 急转弯修正器
    private val sharpTurnCorrector = SharpTurnCorrector()
    
    // 当前急转弯状态
    private var currentTurnState: SharpTurnCorrector.TurnState? = null
    
    // 时间戳跟踪
    private var lastIMUTimestamp = 0.0
    
    // 性能统计
    private var interpolationStartTime = 0L
    private var totalInterpolationTime = 0.0
    private var totalDataTime = 0.0  // 总共插值的数据时间（秒）
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 进度信息
     */
    data class ProgressInfo(
        val startGPS: GPSPoint?,      // 起始GPS点
        val endGPS: GPSPoint?,        // 结束GPS点
        val currentProgress: Float,   // 当前进度 0.0-1.0
        val pointsGenerated: Int,     // 已生成点数
        val totalPoints: Int,         // 总点数
        val speed: Double,            // 插值速度（秒数据/秒实际时间）
        val estimatedTimeLeft: Double // 预计剩余时间（秒）
    )
    
    fun start() {
        if (isRunning) return
        
        Log.d(TAG, "开始追踪（批量插值，100Hz）")
        
        madgwickFilter.reset()
        sharpTurnCorrector.reset()
        gpsPoints.clear()
        imuBuffer.clear()
        lastGPSPoint = null
        currentGPSPoint = null
        isInterpolating = false
        totalInterpolationTime = 0.0
        totalDataTime = 0.0
        currentTurnState = null
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "kart_track_$timestamp.json"
        val file = File(context.getExternalFilesDir(null), fileName)
        jsonWriter = BatchJsonWriter(file.absolutePath)
        
        dataPointCount = 0L
        isRunning = true
        
        imuSampler.addListener { onIMUData(it) }
        gpsCollector.addListener { onGPSData(it) }
        
        imuSampler.startSampling()
        gpsCollector.start()
        
        updateStatus("正在等待GPS定位...")
    }
    
    fun stop() {
        if (!isRunning) return
        
        Log.d(TAG, "停止追踪")
        
        isRunning = false
        
        imuSampler.stopSampling()
        gpsCollector.stop()
        
        jsonWriter?.close()
        jsonWriter = null
        
        updateStatus("追踪已停止，共记录 $dataPointCount 个数据点")
    }
    
    fun isRunning(): Boolean = isRunning
    
    private fun onIMUData(data: IMUData) {
        if (!isRunning) return
        
        val dt = if (lastIMUTimestamp > 0) {
            (data.timestamp - lastIMUTimestamp).toFloat().coerceIn(0.001f, 0.1f)
        } else {
            0.01f
        }
        lastIMUTimestamp = data.timestamp
        
        madgwickFilter.update(
            floatArrayOf(data.accelX, data.accelY, data.accelZ),
            floatArrayOf(data.gyroX, data.gyroY, data.gyroZ),
            floatArrayOf(data.magX, data.magY, data.magZ),
            dt
        )
        
        val quaternion = madgwickFilter.getQuaternion()
        val speed = gpsCollector.getLastSpeed()
        
        currentTurnState = sharpTurnCorrector.update(
            data.accelX,
            data.accelY,
            data.accelZ,
            data.gyroX,
            data.gyroY,
            data.gyroZ,
            quaternion,
            speed,
            data.timestamp
        )
        
        imuBuffer.add(data)
        if (imuBuffer.size > maxImuBufferSize) {
            imuBuffer.removeFirst()
        }
        
        if (currentGPSPoint != null && lastGPSPoint != null && !isInterpolating) {
            startInterpolation()
        }
    }
    
    private fun onGPSData(data: GPSData) {
        if (!isRunning) return
        
        Log.d(TAG, "收到GPS数据: (${data.lat}, ${data.lon}), 精度: ${data.accuracy}m")
        
        if (data.accuracy > 100f) {
            Log.w(TAG, "GPS精度太差，忽略")
            return
        }
        
        val gpsPoint = GPSPoint(
            timestamp = data.timestamp,
            lat = data.lat,
            lon = data.lon,
            alt = data.alt,
            speed = data.speed,
            bearing = data.bearing,
            accuracy = data.accuracy
        )
        
        lastGPSPoint = currentGPSPoint
        currentGPSPoint = gpsPoint
        
        gpsPoints.add(gpsPoint)
        if (gpsPoints.size > 10) {
            gpsPoints.removeFirst()
        }
        
        if (lastGPSPoint == null) {
            updateStatus("已收到GPS点，等待下一个点...")
            
            val trackPoint = TrackPoint(
                timestamp = gpsPoint.timestamp,
                lat = gpsPoint.lat,
                lon = gpsPoint.lon,
                alt = gpsPoint.alt,
                speed = gpsPoint.speed,
                accuracy = gpsPoint.accuracy,
                roll = 0f,
                pitch = 0f,
                yaw = 0f
            )
            
            jsonWriter?.writePoint(trackPoint)
            dataPointCount++
            scope.launch {
                onTrackPointUpdate?.invoke(trackPoint)
            }
        } else if (!isInterpolating) {
            updateStatus("收到2个GPS点，开始插值...")
            startInterpolation()
        }
    }
    
    private fun startInterpolation() {
        val start = lastGPSPoint ?: return
        val end = currentGPSPoint ?: return
        
        isInterpolating = true
        interpolationStartTime = System.currentTimeMillis()
        
        Log.d(TAG, "开始插值: (${start.lat}, ${start.lon}) → (${end.lat}, ${end.lon})")
        
        scope.launch(Dispatchers.Default) {
            interpolateBetweenPoints(start, end)
        }
    }
    
    private suspend fun interpolateBetweenPoints(start: GPSPoint, end: GPSPoint) {
        try {
            val timeDiff = end.timestamp - start.timestamp
            if (timeDiff <= 0) {
                Log.w(TAG, "时间差无效: $timeDiff")
                isInterpolating = false
                return
            }
            
            val targetPointCount = (timeDiff * 100).toInt().coerceAtLeast(2)
            Log.d(TAG, "在 $timeDiff 秒内生成 $targetPointCount 个点")
            
            totalDataTime += timeDiff
            
            val imuInWindow = imuBuffer.filter {
                it.timestamp >= start.timestamp && it.timestamp <= end.timestamp
            }
            
            val startTime = System.currentTimeMillis()
            
            for (i in 0 until targetPointCount) {
                if (!isRunning) break
                
                val progress = i.toFloat() / (targetPointCount - 1)
                
                var finalLat = lerp(start.lat, end.lat, progress)
                var finalLon = lerp(start.lon, end.lon, progress)
                val finalAlt = lerp(start.alt, end.alt, progress)
                val finalSpeed = lerp(start.speed, end.speed, progress)
                
                if (turnState != null && turnState.confidence > 0.3) {
                    val bearing = (start.bearing + (end.bearing - start.bearing) * progress)
                    val (correctedLat, correctedLon) = sharpTurnCorrector.applyCorrection(
                        lat, lon, bearing, turnState.lateralOffset
                    )
                    
                    finalLat = correctedLat
                    finalLon = correctedLon
                }
                
                val (roll, pitch, yaw) = if (i < imuInWindow.size) {
                    val euler = madgwickFilter.getEulerAngles()
                    Triple(euler[0], euler[1], euler[2])
                } else {
                    Triple(0f, 0f, 0f)
                }
                
                val timestamp = start.timestamp + progress * timeDiff
                
                val trackPoint = TrackPoint(
                    timestamp = timestamp,
                    lat = finalLat,
                    lon = finalLon,
                    alt = finalAlt,
                    speed = finalSpeed,
                    accuracy = start.accuracy * (1 - progress) + end.accuracy * progress,
                    roll = roll,
                    pitch = pitch,
                    yaw = yaw
                )
                
                jsonWriter?.writePoint(trackPoint)
                dataPointCount++
                
                withContext(Dispatchers.Main) {
                    onTrackPointUpdate?.invoke(trackPoint)
                }
                
                // 每5个点更新一次进度
                if (i % 5 == 0 || i == targetPointCount - 1) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsedSeconds > 0) timeDiff / elapsedSeconds else 0.0
                    val remainingDataTime = timeDiff * (1 - progress)
                    val estimatedTimeLeft = if (speed > 0) remainingDataTime / speed else 0.0
                    
                    val progressInfo = ProgressInfo(
                        startGPS = start,
                        endGPS = end,
                        currentProgress = progress,
                        pointsGenerated = i + 1,
                        totalPoints = targetPointCount,
                        speed = speed,
                        estimatedTimeLeft = estimatedTimeLeft
                    )
                    
                    withContext(Dispatchers.Main) {
                        onProgressUpdate?.invoke(progressInfo)
                    }
                }
                
                if (i % 10 == 0) {
                    delay(1)
                }
            }
            
            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
            totalInterpolationTime += totalTime
            Log.d(TAG, "插值完成: ${timeDiff}秒数据，耗时${totalTime}秒")
            
        } catch (e: Exception) {
            Log.e(TAG, "插值错误", e)
        } finally {
            isInterpolating = false
            
            val avgSpeed = if (totalInterpolationTime > 0) totalDataTime / totalInterpolationTime else 0.0
            
            scope.launch {
                updateStatus("插值完成，平均速度: ${String.format("%.2f", avgSpeed)}秒数据/秒")
            }
        }
    }
    
    private fun lerp(a: Double, b: Double, t: Float): Double {
        return a + (b - a) * t
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
    
    private fun updateStatus(message: String) {
        scope.launch {
            onStatusUpdate?.invoke(message)
        }
    }
    
    fun destroy() {
        if (isRunning) {
            stop()
        }
        scope.cancel()
    }
    
    data class GPSPoint(
        val timestamp: Double,
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val speed: Float,
        val bearing: Float,
        val accuracy: Float
    )
}
