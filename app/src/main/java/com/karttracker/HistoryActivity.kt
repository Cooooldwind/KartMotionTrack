package com.karttracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karttracker.model.TrackData
import com.karttracker.storage.TrackFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private lateinit var trackFileManager: TrackFileManager
    private lateinit var adapter: TrackListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "历史轨迹"
        
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.visibility = View.GONE
        
        progressBar = findViewById(R.id.progressBar)
        tvLoading = findViewById(R.id.tvLoading)
        
        trackFileManager = TrackFileManager(this)
        loadTrackFilesAsync()
    }
    
    private fun loadTrackFilesAsync() {
        progressBar.visibility = View.VISIBLE
        tvLoading.visibility = View.VISIBLE
        tvLoading.text = "正在加载轨迹列表..."
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val trackFiles = trackFileManager.getAllTrackFiles()
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvLoading.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    
                    if (trackFiles.isEmpty()) {
                        tvLoading.visibility = View.VISIBLE
                        tvLoading.text = "暂无轨迹记录"
                        return@withContext
                    }
                    
                    adapter = TrackListAdapter(trackFiles) { trackData ->
                        val intent = Intent(this@HistoryActivity, TrackDetailActivity::class.java)
                        intent.putExtra("filePath", trackData.filePath)
                        startActivity(intent)
                    }
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvLoading.visibility = View.VISIBLE
                    tvLoading.text = "加载失败: ${e.message}"
                    Toast.makeText(this@HistoryActivity, "加载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadTrackFilesAsync()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    class TrackListAdapter(
        private val tracks: List<TrackData>,
        private val onItemClick: (TrackData) -> Unit
    ) : RecyclerView.Adapter<TrackListAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val tvStats: TextView = itemView.findViewById(R.id.tvStats)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = tracks[position]
            holder.itemView.setOnClickListener { onItemClick(track) }
            
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val date = dateFormat.parse(track.startTime)
            val displayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            val displayTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
            
            holder.tvDate.text = displayDate
            holder.tvTime.text = "开始时间: $displayTime"
            
            val durationStr = formatDuration(track.duration)
            val maxSpeedKm = (track.maxSpeed * 3.6).toInt()
            holder.tvStats.text = "点数: ${track.pointCount} | 时长: $durationStr | 最高速: ${maxSpeedKm} km/h"
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
        
        override fun getItemCount() = tracks.size
    }
}
