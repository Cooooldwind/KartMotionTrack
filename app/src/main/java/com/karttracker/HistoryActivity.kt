package com.karttracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karttracker.adapter.TrackAdapter
import com.karttracker.model.TrackData
import com.karttracker.storage.TrackFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {
    private lateinit var trackFileManager: TrackFileManager
    private lateinit var rvTracks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var trackAdapter: TrackAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "历史轨迹"
        
        trackFileManager = TrackFileManager(this)
        
        rvTracks = findViewById(R.id.rvTracks)
        tvEmpty = findViewById(R.id.tvEmpty)
        progressLayout = findViewById(R.id.progressLayout)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        
        rvTracks.layoutManager = LinearLayoutManager(this)
        trackAdapter = TrackAdapter { track ->
            val intent = Intent(this, TrackDetailActivity::class.java)
            intent.putExtra("filePath", track.filePath)
            startActivity(intent)
        }
        rvTracks.adapter = trackAdapter
        
        loadTracks()
    }
    
    private fun loadTracks() {
        showProgressDialog("加载中...")
        
        GlobalScope.launch(Dispatchers.IO) {
            val tracks = trackFileManager.getAllTrackFiles()
            
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                
                if (tracks.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvTracks.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvTracks.visibility = View.VISIBLE
                    trackAdapter.submitList(tracks)
                }
            }
        }
    }
    
    private fun showProgressDialog(message: String) {
        tvProgress.text = message
        progressBar.progress = 0
        progressLayout.visibility = View.VISIBLE
    }
    
    private fun dismissProgressDialog() {
        progressLayout.visibility = View.GONE
    }
    
    override fun onResume() {
        super.onResume()
        loadTracks()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
