package com.karttracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var kartTracker: KartTracker
    
    private lateinit var tvStatus: TextView
    private lateinit var tvStartGPS: TextView
    private lateinit var tvEndGPS: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvTimeLeft: TextView
    private lateinit var tvPointsInfo: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnHistory: Button
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            startTracking()
        } else {
            Toast.makeText(this, "需要位置权限才能使用追踪功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        checkSensors()
        initTracker()
        setupListeners()
    }
    
    private fun checkSensors() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        if (accelerometer == null) {
            Toast.makeText(this, "设备不支持加速度传感器", Toast.LENGTH_LONG).show()
            btnStart.isEnabled = false
        }
        
        if (gyroscope == null) {
            Toast.makeText(this, "设备不支持陀螺仪传感器", Toast.LENGTH_LONG).show()
            btnStart.isEnabled = false
        }
    }
    
    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvStartGPS = findViewById(R.id.tvStartGPS)
        tvEndGPS = findViewById(R.id.tvEndGPS)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvTimeLeft = findViewById(R.id.tvTimeLeft)
        tvPointsInfo = findViewById(R.id.tvPointsInfo)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnHistory = findViewById(R.id.btnHistory)
    }
    
    private fun initTracker() {
        kartTracker = KartTracker(this)
        
        kartTracker.onStatusUpdate = { status ->
            try {
                tvStatus.text = status
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        kartTracker.onProgressUpdate = { progressInfo ->
            try {
                updateProgressUI(progressInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun updateProgressUI(info: KartTracker.ProgressInfo) {
        val startGPS = info.startGPS
        val endGPS = info.endGPS
        
        if (startGPS != null) {
            tvStartGPS.text = String.format("0s: (%.7f, %.7f)", startGPS.lat, startGPS.lon)
        } else {
            tvStartGPS.text = "0s: --"
        }
        
        if (endGPS != null) {
            val timeDiff = endGPS.timestamp - (startGPS?.timestamp ?: endGPS.timestamp)
            tvEndGPS.text = String.format("%.1fs: (%.7f, %.7f)", timeDiff, endGPS.lat, endGPS.lon)
        } else {
            tvEndGPS.text = "ns: --"
        }
        
        val progressPercent = (info.currentProgress * 100).toInt()
        progressBar.progress = progressPercent
        tvProgressPercent.text = String.format("%d%%", progressPercent)
        
        if (info.speed > 0) {
            tvSpeed.text = String.format("%.2f 秒数据/秒", info.speed)
        } else {
            tvSpeed.text = "-- 秒数据/秒"
        }
        
        if (info.estimatedTimeLeft > 0) {
            if (info.estimatedTimeLeft < 60) {
                tvTimeLeft.text = String.format("%.1f 秒", info.estimatedTimeLeft)
            } else {
                val minutes = (info.estimatedTimeLeft / 60).toInt()
                val seconds = (info.estimatedTimeLeft % 60).toInt()
                tvTimeLeft.text = String.format("%d分%d秒", minutes, seconds)
            }
        } else {
            tvTimeLeft.text = "-- 秒"
        }
        
        tvPointsInfo.text = String.format("已生成: %d / %d 个点", info.pointsGenerated, info.totalPoints)
    }
    
    private fun resetProgressUI() {
        tvStartGPS.text = "0s: --"
        tvEndGPS.text = "ns: --"
        progressBar.progress = 0
        tvProgressPercent.text = "0%"
        tvSpeed.text = "-- 秒数据/秒"
        tvTimeLeft.text = "-- 秒"
        tvPointsInfo.text = "已生成: 0 / 0 个点"
    }
    
    private fun setupListeners() {
        btnStart.setOnClickListener {
            resetProgressUI()
            checkPermissionsAndStart()
        }
        
        btnStop.setOnClickListener {
            stopTracking()
        }
        
        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startTracking()
        }
    }
    
    private fun startTracking() {
        kartTracker.start()
        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }
    
    private fun stopTracking() {
        kartTracker.stop()
        btnStart.isEnabled = true
        btnStop.isEnabled = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        kartTracker.destroy()
    }
}
