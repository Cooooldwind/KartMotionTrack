package com.karttracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var kartTracker: KartTracker
    
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
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
        tvStats = findViewById(R.id.tvStats)
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
    }
    
    private fun setupListeners() {
        btnStart.setOnClickListener {
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
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS) != PackageManager.PERMISSION_GRANTED) {
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
        tvStats.visibility = TextView.GONE
    }
    
    private fun stopTracking() {
        val stats = kartTracker.stop()
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        
        tvStats.text = "本次记录: GPS ${stats.gpsPoints}点, IMU ${stats.imuPoints}点, 时长 ${formatDuration(stats.duration)}"
        tvStats.visibility = TextView.VISIBLE
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        kartTracker.destroy()
    }
}
