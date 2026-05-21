package com.karttracker.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.karttracker.model.IMUData

class IMUSampler(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val listeners = mutableListOf<(IMUData) -> Unit>()
    
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private var lastAccel: FloatArray? = null
    private var lastGyro: FloatArray? = null
    private var lastMag: FloatArray? = null
    private var emitCount = 0
    private var lastEmitTimestamp = 0L
    private val targetIntervalNs = 10_000_000L  // 100Hz = 10ms间隔
    
    init {
        Log.d("KartTracker", "IMU可用性 - 加速度计: ${accelerometer != null}, 陀螺仪: ${gyroscope != null}, 磁力计: ${magnetometer != null}")
    }
    
    fun addListener(listener: (IMUData) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (IMUData) -> Unit) {
        listeners.remove(listener)
    }
    
    fun startSampling() {
        try {
            accelerometer?.let {
                sensorManager.registerListener(
                    accelListener, it,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
            
            gyroscope?.let {
                sensorManager.registerListener(
                    gyroListener, it,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
            
            magnetometer?.let {
                sensorManager.registerListener(
                    magListener, it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopSampling() {
        try {
            sensorManager.unregisterListener(accelListener)
            sensorManager.unregisterListener(gyroListener)
            sensorManager.unregisterListener(magListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastAccel = event.values.copyOf()
            tryMergeAndEmit(event.timestamp)
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
    }
    
    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastGyro = event.values.copyOf()
            tryMergeAndEmit(event.timestamp)
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
    }
    
    private val magListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastMag = event.values.copyOf()
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
    }
    
    private fun tryMergeAndEmit(timestamp: Long) {
        val accel = lastAccel ?: return
        val gyro = lastGyro ?: return
        
        // 使用时间戳控制采样频率（稳定的100Hz）
        val timeSinceLastEmit = timestamp - lastEmitTimestamp
        if (timeSinceLastEmit < targetIntervalNs) {
            return
        }
        lastEmitTimestamp = timestamp
        
        emitCount++
        if (emitCount % 100 == 0) {
            Log.d("KartTracker", "IMU 100Hz正常，次数: $emitCount, 加速度: ${accel[0]}, ${accel[1]}, ${accel[2]}")
        }
        
        val data = IMUData(
            timestamp = timestamp / 1_000_000_000.0,
            accelX = accel[0], accelY = accel[1], accelZ = accel[2],
            gyroX = gyro[0], gyroY = gyro[1], gyroZ = gyro[2],
            magX = lastMag?.get(0) ?: 0f,
            magY = lastMag?.get(1) ?: 0f,
            magZ = lastMag?.get(2) ?: 0f
        )
        listeners.forEach { it(data) }
    }
}
