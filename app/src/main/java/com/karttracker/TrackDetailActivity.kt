package com.karttracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.karttracker.model.TrackData
import com.karttracker.storage.TrackFileManager
import com.karttracker.storage.TrackProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class TrackDetailActivity : AppCompatActivity() {
    private lateinit var trackFileManager: TrackFileManager
    private lateinit var trackProcessor: TrackProcessor
    private lateinit var tvInfo: TextView
    private lateinit var btnGenerate: Button
    private lateinit var btnExportGPX: Button
    private lateinit var btnExportCSV: Button
    private lateinit var btnDelete: Button
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    
    private var trackData: TrackData? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_detail)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "轨迹详情"
        
        trackFileManager = TrackFileManager(this)
        trackProcessor = TrackProcessor(this)
        
        tvInfo = findViewById(R.id.tvInfo)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnExportGPX = findViewById(R.id.btnExportGPX)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        btnDelete = findViewById(R.id.btnDelete)
        progressLayout = findViewById(R.id.progressLayout)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        
        val filePath = intent.getStringExtra("filePath") ?: ""
        trackData = trackFileManager.getTrackStatus(filePath)
        
        updateUI()
        setupListeners()
    }
    
    private fun updateUI() {
        val data = trackData ?: return
        
        val info = StringBuilder()
        info.append("记录信息:\n")
        info.append("  开始时间: ${data.startTime}\n")
        info.append("  结束时间: ${data.endTime}\n")
        info.append("  时长: ${formatDuration(data.duration)}\n\n")
        
        if (data.isProcessed && data.processedFilePath != null) {
            val points = trackFileManager.loadTrackPoints(data.processedFilePath)
            if (points.isNotEmpty()) {
                val start = points.first()
                val end = points.last()
                val distance = calculateDistance(points)
                val avgSpeed = if (data.duration > 0) distance / (data.duration / 1000.0) else 0.0
                
                info.append("处理后轨迹:\n")
                info.append("  点数: ${points.size}\n")
                info.append("  采样率: 100Hz\n")
                info.append("  距离: ${String.format("%.2f", distance)} 米\n")
                info.append("  平均速度: ${String.format("%.2f", avgSpeed * 3.6)} km/h\n")
                info.append("  最高速度: ${String.format("%.2f", data.maxSpeed * 3.6)} km/h\n")
            }
            
            btnGenerate.visibility = View.GONE
            btnExportGPX.visibility = View.VISIBLE
            btnExportCSV.visibility = View.VISIBLE
        } else {
            info.append("原始数据:\n")
            info.append("  GPS点数: ${data.gpsPointCount}\n")
            info.append("  IMU点数: ${data.imuPointCount}\n\n")
            info.append("状态: 未处理\n")
            info.append("提示: 点击「生成轨迹」进行后处理")
            
            btnGenerate.visibility = View.VISIBLE
            btnExportGPX.visibility = View.GONE
            btnExportCSV.visibility = View.GONE
        }
        
        tvInfo.text = info.toString()
    }
    
    private fun setupListeners() {
        btnGenerate.setOnClickListener {
            generateTrack()
        }
        
        btnExportGPX.setOnClickListener {
            exportGPX()
        }
        
        btnExportCSV.setOnClickListener {
            exportCSV()
        }
        
        btnDelete.setOnClickListener {
            deleteTrack()
        }
    }
    
    private fun generateTrack() {
        val data = trackData ?: return
        
        showProgressDialog("正在生成轨迹...")
        
        GlobalScope.launch(Dispatchers.IO) {
            val result = trackProcessor.processTrack(data.filePath) { progress ->
                GlobalScope.launch(Dispatchers.Main) {
                    updateProgress(progress.phase, progress.progress)
                }
            }
            
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                
                if (result.success) {
                    trackData = trackFileManager.getTrackStatus(data.filePath)
                    updateUI()
                    Toast.makeText(this@TrackDetailActivity, 
                        "轨迹生成完成: ${result.pointCount}点", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TrackDetailActivity, 
                        "生成失败: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun exportGPX() {
        val data = trackData ?: return
        val processedPath = data.processedFilePath ?: return
        
        showProgressDialog("正在导出GPX...")
        
        GlobalScope.launch(Dispatchers.IO) {
            val outputPath = trackFileManager.exportToGPX(processedPath) { progress ->
                GlobalScope.launch(Dispatchers.Main) {
                    updateProgress("导出GPX", progress)
                }
            }
            
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                
                if (outputPath.isNotEmpty()) {
                    Toast.makeText(this@TrackDetailActivity, 
                        "GPX导出成功: $outputPath", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TrackDetailActivity, "导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportCSV() {
        val data = trackData ?: return
        val processedPath = data.processedFilePath ?: return
        
        showProgressDialog("正在导出CSV...")
        
        GlobalScope.launch(Dispatchers.IO) {
            val outputPath = trackFileManager.exportToCSV(processedPath) { progress ->
                GlobalScope.launch(Dispatchers.Main) {
                    updateProgress("导出CSV", progress)
                }
            }
            
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                
                if (outputPath.isNotEmpty()) {
                    Toast.makeText(this@TrackDetailActivity, 
                        "CSV导出成功: $outputPath", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TrackDetailActivity, "导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteTrack() {
        val data = trackData ?: return
        if (trackFileManager.deleteTrackFile(data.filePath)) {
            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showProgressDialog(message: String) {
        tvProgress.text = message
        progressBar.progress = 0
        progressLayout.visibility = View.VISIBLE
    }
    
    private fun updateProgress(phase: String, progress: Int) {
        tvProgress.text = "$phase ($progress%)"
        progressBar.progress = progress
    }
    
    private fun dismissProgressDialog() {
        progressLayout.visibility = View.GONE
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
    }
    
    private fun calculateDistance(points: List<com.karttracker.model.TrackPoint>): Double {
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += haversine(
                points[i-1].lat, points[i-1].lon,
                points[i].lat, points[i].lon
            )
        }
        return distance
    }
    
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
