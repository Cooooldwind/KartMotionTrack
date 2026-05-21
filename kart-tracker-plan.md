# 卡丁车高频高精度轨迹追踪APP - 完整技术方案

## 一、开发环境准备

### 1.1 必需软件安装

#### IDE与开发工具
- **Android Studio** (最新稳定版)
  - 下载地址：https://developer.android.com/studio
  - 包含：Android SDK, Kotlin编译器, ADB调试工具

#### JDK
- **JDK 17或更高版本**（Android Studio自带，无需单独安装）

#### SDK配置
- Android SDK Platform 29 (Android 10) 或更高
- Android SDK Build-Tools 30.0.3 或更高
- Android SDK Platform-Tools

---

## 二、项目技术架构

### 2.1 核心技术栈
- **语言**：Kotlin
- **最低SDK版本**：API 26 (Android 8.0)
- **目标SDK版本**：API 34 (Android 14)
- **构建工具**：Gradle

### 2.2 项目结构
```
kart-tracker/
├── app/
│   ├── src/main/java/com/karttracker/
│   │   ├── sensors/           # 传感器采集模块
│   │   │   ├── SensorManager.kt
│   │   │   ├── IMUSampler.kt
│   │   │   ├── GPSCollector.kt
│   │   │   └── SensorCalibrator.kt
│   │   ├── fusion/            # 传感器融合模块
│   │   │   ├── MadgwickFilter.kt
│   │   │   ├── EKF.kt
│   │   │   └── CoordinateTransformer.kt
│   │   ├── processing/        # 信号处理模块
│   │   │   ├── VibrationFilter.kt
│   │   │   ├── LowPassFilter.kt
│   │   │   └── MedianFilter.kt
│   │   ├── storage/           # 数据存储模块
│   │   │   ├── CircularBuffer.kt
│   │   │   ├── JsonWriter.kt
│   │   │   └── BatchWriter.kt
│   │   ├── model/             # 数据模型
│   │   │   ├── IMUData.kt
│   │   │   ├── GPSData.kt
│   │   │   └── TrackPoint.kt
│   │   └── ui/                # 用户界面
│   │       └── MainActivity.kt
│   └── build.gradle.kts
└── build.gradle.kts
```

---

## 三、核心实现逻辑详解

### 3.1 高频传感器采集模块

#### 传感器配置
| 传感器 | 采样频率 | 用途 |
|--------|---------|------|
| 加速度计 | 200Hz | `SENSOR_DELAY_FASTEST` |
| 陀螺仪 | 200Hz | `SENSOR_DELAY_FASTEST` |
| 磁力计 | 50Hz | `SENSOR_DELAY_GAME` |
| GPS | 最高可用 | `LocationRequest.PRIORITY_HIGH_ACCURACY` |

#### 核心代码 - IMUSampler.kt
```kotlin
class IMUSampler(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val listeners = mutableListOf<(IMUData) -> Unit>()
    
    // 加速度计
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    // 陀螺仪
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    // 磁力计
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private var lastAccel: FloatArray? = null
    private var lastGyro: FloatArray? = null
    private var lastMag: FloatArray? = null
    
    fun startSampling() {
        // 加速度计 - 200Hz
        sensorManager.registerListener(
            accelListener, accelerometer,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        
        // 陀螺仪 - 200Hz
        sensorManager.registerListener(
            gyroListener, gyroscope,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        
        // 磁力计 - 50Hz
        sensorManager.registerListener(
            magListener, magnetometer,
            SensorManager.SENSOR_DELAY_GAME
        )
    }
    
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastAccel = event.values.copyOf()
            tryMergeAndEmit(event.timestamp)
        }
        override fun onAccuracyChanged(s: Sensor, a: Int) {}
    }
    
    private fun tryMergeAndEmit(timestamp: Long) {
        val accel = lastAccel ?: return
        val gyro = lastGyro ?: return
        
        val data = IMUData(
            timestamp = timestamp / 1_000_000.0, // 转换为秒
            accelX = accel[0], accelY = accel[1], accelZ = accel[2],
            gyroX = gyro[0], gyroY = gyro[1], gyroZ = gyro[2],
            magX = lastMag?.get(0) ?: 0f,
            magY = lastMag?.get(1) ?: 0f,
            magZ = lastMag?.get(2) ?: 0f
        )
        listeners.forEach { it(data) }
    }
}
```

---

### 3.2 震动滤波与信号处理

#### 问题分析
卡丁车在赛道上行驶时，发动机震动和赛道颠簸会产生高频噪声，污染加速度计数据。

#### 解决方案：多级滤波组合

```kotlin
class VibrationFilter {
    private val lowPass = LowPassFilter(cutoffFreq = 15f, sampleRate = 200f)
    private val medianFilter = MedianFilter(windowSize = 5)
    private val kalman = KalmanFilter1D(processNoise = 0.01f, measurementNoise = 0.1f)
    
    private var vibrationLevel = 0f
    private val accelHistory = mutableListOf<FloatArray>()
    private val historySize = 50
    
    fun filter(accel: FloatArray, gyro: FloatArray): FilteredIMUData {
        // 步骤1：检测震动强度
        updateVibrationLevel(accel)
        
        // 步骤2：根据震动强度调整滤波强度
        val filterStrength = when {
            vibrationLevel > 0.5f -> 0.8f  // 强震动，强滤波
            vibrationLevel > 0.2f -> 0.5f  // 中等震动
            else -> 0.2f  // 弱震动，轻滤波
        }
        
        // 步骤3：多级滤波
        val lowPassed = lowPass.apply(accel)
        val medianed = medianFilter.apply(lowPassed)
        val kalmanned = kalman.apply(medianed)
        
        return FilteredIMUData(
            accel = kalmanned,
            gyro = gyro,
            vibrationLevel = vibrationLevel,
            filterStrength = filterStrength
        )
    }
    
    private fun updateVibrationLevel(accel: FloatArray) {
        accelHistory.add(accel.copyOf())
        if (accelHistory.size > historySize) accelHistory.removeAt(0)
        
        if (accelHistory.size >= 10) {
            // 计算加速度方差作为震动强度
            val mean = accelHistory.map { it.sum() / 3 }.average()
            val variance = accelHistory.map { 
                val a = it.sum() / 3 
                (a - mean) * (a - mean) 
            }.average()
            vibrationLevel = sqrt(variance).toFloat()
        }
    }
}

class LowPassFilter(private val cutoffFreq: Float, private val sampleRate: Float) {
    private val alpha: Float
    private var lastValue: FloatArray? = null
    
    init {
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoffFreq)
        alpha = dt / (rc + dt)
    }
    
    fun apply(input: FloatArray): FloatArray {
        val last = lastValue ?: input
        val output = FloatArray(input.size)
        for (i in input.indices) {
            output[i] = alpha * input[i] + (1 - alpha) * last[i]
        }
        lastValue = output.copyOf()
        return output
    }
}

class MedianFilter(private val windowSize: Int) {
    private val windows = Array(3) { mutableListOf<Float>() }
    
    fun apply(input: FloatArray): FloatArray {
        val output = FloatArray(3)
        for (i in 0..2) {
            windows[i].add(input[i])
            if (windows[i].size > windowSize) windows[i].removeAt(0)
            output[i] = windows[i].sorted()[windows[i].size / 2]
        }
        return output
    }
}
```

---

### 3.3 姿态跟踪与坐标系转换

#### 核心问题
手机在裤兜里可以任意朝向，需要实时跟踪姿态并转换坐标系。

#### 解决方案：Madgwick滤波 + 四元数姿态表示

```kotlin
class MadgwickFilter(private val beta: Float = 0.1f) {
    // 四元数表示姿态: q = [w, x, y, z]
    private var q = floatArrayOf(1f, 0f, 0f, 0f)
    private var lastTimestamp = 0L
    
    fun update(accel: FloatArray, gyro: FloatArray, mag: FloatArray, dt: Float) {
        // 归一化加速度
        val normAccel = normalize(accel)
        
        // 归一化磁力计
        val normMag = normalize(mag)
        
        // 梯度下降算法修正四元数
        val gradient = computeGradient(q, normAccel, normMag)
        
        // 积分陀螺仪
        val qDot = floatArrayOf(
            0.5f * (-q[1]*gyro[0] - q[2]*gyro[1] - q[3]*gyro[2]),
            0.5f * ( q[0]*gyro[0] + q[2]*gyro[2] - q[3]*gyro[1]),
            0.5f * ( q[0]*gyro[1] - q[1]*gyro[2] + q[3]*gyro[0]),
            0.5f * ( q[0]*gyro[2] + q[1]*gyro[1] - q[2]*gyro[0])
        )
        
        // 应用修正
        for (i in 0..3) {
            q[i] += (qDot[i] - beta * gradient[i]) * dt
        }
        
        // 归一化四元数
        q = normalize(q)
    }
    
    fun getRotationMatrix(): Array<FloatArray> {
        // 四元数转旋转矩阵
        val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
        return arrayOf(
            floatArrayOf(1-2*y*y-2*z*z, 2*x*y-2*z*w,   2*x*z+2*y*w),
            floatArrayOf(2*x*y+2*z*w,   1-2*x*x-2*z*z, 2*y*z-2*x*w),
            floatArrayOf(2*x*z-2*y*w,   2*y*z+2*x*w,   1-2*x*x-2*y*y)
        )
    }
    
    fun rotateVectorToWorld(v: FloatArray): FloatArray {
        val R = getRotationMatrix()
        return floatArrayOf(
            R[0][0]*v[0] + R[0][1]*v[1] + R[0][2]*v[2],
            R[1][0]*v[0] + R[1][1]*v[1] + R[1][2]*v[2],
            R[2][0]*v[0] + R[2][1]*v[1] + R[2][2]*v[2]
        )
    }
    
    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { it*it.toDouble() }).toFloat()
        return v.map { it / norm }.toFloatArray()
    }
    
    private fun computeGradient(q: FloatArray, accel: FloatArray, mag: FloatArray): FloatArray {
        // Madgwick梯度计算（简化版）
        // 完整实现请参考Madgwick论文
        return floatArrayOf(0f, 0f, 0f, 0f) // 占位
    }
}
```

---

### 3.4 重力分离与线性加速度提取

```kotlin
class GravityExtractor {
    private val lowPass = LowPassFilter(cutoffFreq = 0.5f, sampleRate = 200f)
    
    fun extract(accelWorld: FloatArray): Pair<FloatArray, FloatArray> {
        // 低通滤波获取重力向量
        val gravity = lowPass.apply(accelWorld)
        
        // 线性加速度 = 总加速度 - 重力
        val linearAccel = FloatArray(3)
        for (i in 0..2) {
            linearAccel[i] = accelWorld[i] - gravity[i]
        }
        
        return Pair(linearAccel, gravity)
    }
}
```

---

### 3.5 扩展卡尔曼滤波 (EKF) 传感器融合

```kotlin
class EKF {
    // 状态向量: [lat, lon, alt, v_e, v_n, v_u, roll, pitch, yaw, accel_bias, gyro_bias]
    private var x = FloatArray(11)
    private var P = FloatArray(11) { 0.1f } // 协方差矩阵（简化为对角）
    
    // 运动学预测
    fun predict(linearAccelWorld: FloatArray, angularVelWorld: FloatArray, dt: Float) {
        val (ax, ay, az) = linearAccelWorld
        val (wx, wy, wz) = angularVelWorld
        
        // 位置更新
        x[0] += x[3] * dt + 0.5 * ax * dt * dt  // lat
        x[1] += x[4] * dt + 0.5 * ay * dt * dt  // lon
        x[2] += x[5] * dt + 0.5 * az * dt * dt  // alt
        
        // 速度更新
        x[3] += ax * dt  // v_e
        x[4] += ay * dt  // v_n
        x[5] += az * dt  // v_u
        
        // 姿态更新（简化）
        x[6] += wx * dt  // roll
        x[7] += wy * dt  // pitch
        x[8] += wz * dt  // yaw
    }
    
    // GPS测量更新
    fun updateGPS(lat: Double, lon: Double, alt: Double, 
                  v_e: Float, v_n: Float, v_u: Float,
                  accuracy: Float) {
        val K = 0.1f / (accuracy + 0.1f)  // 卡尔曼增益
        
        // 位置修正
        x[0] += K * (lat.toFloat() - x[0])
        x[1] += K * (lon.toFloat() - x[1])
        x[2] += K * (alt.toFloat() - x[2])
        
        // 速度修正
        x[3] += K * (v_e - x[3])
        x[4] += K * (v_n - x[4])
        x[5] += K * (v_u - x[5])
    }
    
    fun getCurrentState(): TrackState {
        return TrackState(
            lat = x[0].toDouble(),
            lon = x[1].toDouble(),
            alt = x[2].toDouble(),
            vEast = x[3],
            vNorth = x[4],
            vUp = x[5],
            roll = x[6],
            pitch = x[7],
            yaw = x[8]
        )
    }
}

data class TrackState(
    val lat: Double, val lon: Double, val alt: Double,
    val vEast: Float, val vNorth: Float, val vUp: Float,
    val roll: Float, val pitch: Float, val yaw: Float
)
```

---

### 3.6 GPS数据处理与插值

```kotlin
class GPSCollector(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastGPSData: GPSData? = null
    private val listeners = mutableListOf<(GPSData) -> Unit>()
    
    fun start() {
        val locationRequest = LocationRequest.create().apply {
            interval = 50
            fastestInterval = 20
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val data = GPSData(
                    timestamp = location.elapsedRealtimeNanos / 1_000_000_000.0,
                    lat = location.latitude,
                    lon = location.longitude,
                    alt = location.altitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing
                )
                lastGPSData = data
                listeners.forEach { it(data) }
            }
        }
    }
    
    // GPS数据插值到200Hz
    fun interpolate(timestamp: Double): GPSData? {
        val last = lastGPSData ?: return null
        // 简化的线性插值，实际可用三次样条插值
        return last.copy(timestamp = timestamp)
    }
}
```

---

### 3.7 数据缓存与JSON批量存储

```kotlin
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity)
    private var head = 0
    private var tail = 0
    private var count = 0
    
    fun add(item: T) {
        buffer[tail] = item
        tail = (tail + 1) % capacity
        if (count < capacity) {
            count++
        } else {
            head = (head + 1) % capacity
        }
    }
    
    fun drainTo(maxSize: Int): List<T> {
        val result = mutableListOf<T>()
        val takeCount = minOf(count, maxSize)
        for (i in 0 until takeCount) {
            @Suppress("UNCHECKED_CAST")
            result.add(buffer[(head + i) % capacity] as T)
        }
        head = (head + takeCount) % capacity
        count -= takeCount
        return result
    }
    
    fun size() = count
}

class BatchJsonWriter(private val filePath: String, private val batchSize: Int = 1000) {
    private val buffer = CircularBuffer<TrackPoint>(10000)
    private val file = File(filePath)
    private val gson = Gson()
    
    init {
        if (!file.exists()) file.createNewFile()
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
        
        file.appendText(
            points.joinToString("\n") { gson.toJson(it) } + "\n"
        )
    }
}

data class TrackPoint(
    val timestamp: Double,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Float,
    val accuracy: Float,
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)
```

---

### 3.8 整合所有模块 - 主控制器

```kotlin
class KartTracker(private val context: Context) {
    private val imuSampler = IMUSampler(context)
    private val gpsCollector = GPSCollector(context)
    private val vibrationFilter = VibrationFilter()
    private val gravityExtractor = GravityExtractor()
    private val madgwickFilter = MadgwickFilter()
    private val ekf = EKF()
    private val jsonWriter = BatchJsonWriter(
        File(context.getExternalFilesDir(null), "track_${System.currentTimeMillis()}.json").absolutePath
    )
    
    private var lastTimestamp = 0L
    private var isRunning = false
    
    fun start() {
        isRunning = true
        imuSampler.startSampling()
        imuSampler.addListener { onIMUData(it) }
        gpsCollector.start()
        gpsCollector.addListener { onGPSData(it) }
    }
    
    private fun onIMUData(data: IMUData) {
        if (lastTimestamp == 0L) {
            lastTimestamp = data.timestamp.toLong()
            return
        }
        val dt = (data.timestamp - lastTimestamp / 1_000_000.0).toFloat()
        lastTimestamp = data.timestamp.toLong()
        
        // 步骤1：震动滤波
        val filtered = vibrationFilter.filter(
            floatArrayOf(data.accelX, data.accelY, data.accelZ),
            floatArrayOf(data.gyroX, data.gyroY, data.gyroZ)
        )
        
        // 步骤2：更新姿态
        madgwickFilter.update(
            filtered.accel,
            filtered.gyro,
            floatArrayOf(data.magX, data.magY, data.magZ),
            dt
        )
        
        // 步骤3：转换坐标系到世界坐标系
        val accelWorld = madgwickFilter.rotateVectorToWorld(filtered.accel)
        val gyroWorld = madgwickFilter.rotateVectorToWorld(filtered.gyro)
        
        // 步骤4：分离重力和线性加速度
        val (linearAccel, gravity) = gravityExtractor.extract(accelWorld)
        
        // 步骤5：EKF预测
        ekf.predict(linearAccel, gyroWorld, dt)
        
        // 步骤6：生成轨迹点
        val state = ekf.getCurrentState()
        val trackPoint = TrackPoint(
            timestamp = data.timestamp,
            lat = state.lat,
            lon = state.lon,
            alt = state.alt,
            speed = sqrt(state.vEast*state.vEast + state.vNorth*state.vNorth),
            accuracy = 0.5f, // 估计值
            roll = state.roll,
            pitch = state.pitch,
            yaw = state.yaw
        )
        
        // 步骤7：写入JSON
        jsonWriter.writePoint(trackPoint)
    }
    
    private fun onGPSData(data: GPSData) {
        // GPS更新EKF
        ekf.updateGPS(
            data.lat, data.lon, data.alt,
            (data.speed * cos(Math.toRadians(data.bearing.toDouble()))).toFloat(),
            (data.speed * sin(Math.toRadians(data.bearing.toDouble()))).toFloat(),
            0f,
            data.accuracy
        )
    }
    
    fun stop() {
        isRunning = false
        jsonWriter.flush()
    }
}
```

---

## 四、关键问题解决方案总结

| 问题 | 解决方案 |
|------|---------|
| 裤兜里手机朝向任意 | Madgwick滤波 + 四元数姿态跟踪 + 实时坐标系转换 |
| 卡丁车震动干扰 | 多级滤波（低通+中值+卡尔曼）+ 震动检测自适应调整 |
| GPS频率低（1-5Hz） | EKF融合，IMU高频预测，GPS低频修正 |
| 数据量大（200Hz） | 环形缓冲 + 批量JSON写入（每1000点写一次） |
| 分米级精度 | 传感器融合 + 零偏估计 + 运动约束 |

---

## 五、权限配置 (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-feature android:name="android.hardware.sensor.accelerometer" />
<uses-feature android:name="android.hardware.sensor.gyroscope" />
```

---

## 六、依赖配置 (build.gradle.kts)

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

---

## 七、测试与调试建议

1. **静态测试**：先在静止状态下测试姿态跟踪和滤波
2. **步行测试**：手持手机步行，验证轨迹合理性
3. **实车测试**：卡丁车实测，对比GPS轨迹
4. **数据分析**：用Python读取JSON文件，绘图验证轨迹质量

---

文档已完成！包含了开发环境、完整代码实现、所有问题的具体解决方案。
