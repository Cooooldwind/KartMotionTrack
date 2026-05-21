package com.karttracker

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.karttracker.model.TrackPoint
import com.karttracker.storage.TrackFileManager
import java.text.SimpleDateFormat
import java.util.*

class TrackDetailActivity : AppCompatActivity() {
    private lateinit var trackFileManager: TrackFileManager
    private lateinit var tvInfo: TextView
    private lateinit var btnExportGPX: Button
    private lateinit var btnExportCSV: Button
    private lateinit var btnDelete: Button
    private var filePath: String = ""
    private var points: List<TrackPoint> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_detail)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "轨迹详情"
        
        trackFileManager = TrackFileManager(this)
        
        tvInfo = findViewById(R.id.tvInfo)
        btnExportGPX = findViewById(R.id.btnExportGPX)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        btnDelete = findViewById(R.id.btnDelete)
        
        filePath = intent.getStringExtra("filePath") ?: ""
        
        loadTrackData()
        
        btnExportGPX.setOnClickListener { exportGPX() }
        btnExportCSV.setOnClickListener { exportCSV() }
        btnDelete.setOnClickListener { deleteTrack() }
    }
    
    private fun loadTrackData() {
        points = trackFileManager.loadTrackPoints(filePath)
        
        if (points.isEmpty()) {
            tvInfo.text = "轨迹数据为空"
            return
        }
        
        val start = points.first()
        val end = points.last()
        
        val duration = ((end.timestamp - start.timestamp) * 1000).toLong()
        val distance = calculateDistance()
        val avgSpeed = if (duration > 0) (distance / (duration / 1000.0)) else 0.0
        val maxSpeed = points.maxOf { it.speed }
        
        val info = StringBuilder()
        info.append("轨迹点数: ${points.size}\n\n")
        info.append("开始位置:\n")
        info.append("  纬度: ${String.format("%.6f", start.lat)}\n")
        info.append("  经度: ${String.format("%.6f", start.lon)}\n\n")
        info.append("结束位置:\n")
        info.append("  纬度: ${String.format("%.6f", end.lat)}\n")
        info.append("  经度: ${String.format("%.6f", end.lon)}\n\n")
        info.append("时长: ${formatDuration(duration)}\n")
        info.append("距离: ${String.format("%.2f", distance)} 米\n")
        info.append("平均速度: ${String.format("%.2f", avgSpeed * 3.6)} km/h\n")
        info.append("最高速度: ${String.format("%.2f", maxSpeed * 3.6)} km/h")
        
        tvInfo.text = info.toString()
    }
    
    private fun calculateDistance(): Double {
        var distance = 0.0
        for (i in 1 until points.size) {
            val p1 = points[i-1]
            val p2 = points[i]
            distance += haversine(p1.lat, p1.lon, p2.lat, p2.lon)
        }
        return distance
    }
    
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
        return R * c
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            "${minutes}分${secs}秒"
        } else {
            "${secs}秒"
        }
    }
    
    private fun exportGPX() {
        val outputPath = trackFileManager.exportToGPX(filePath)
        if (outputPath.isNotEmpty()) {
            Toast.makeText(this, "GPX导出成功: $outputPath", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun exportCSV() {
        val outputPath = trackFileManager.exportToCSV(filePath)
        if (outputPath.isNotEmpty()) {
            Toast.makeText(this, "CSV导出成功: $outputPath", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteTrack() {
        if (trackFileManager.deleteTrackFile(filePath)) {
            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
