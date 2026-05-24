package com.karttracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.karttracker.R
import com.karttracker.model.TrackData

class TrackAdapter(
    private val onTrackClick: (TrackData) -> Unit
) : ListAdapter<TrackData, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardTrack)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStats: TextView = itemView.findViewById(R.id.tvStats)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(track: TrackData) {
            tvDate.text = track.startTime
            tvTime.text = formatDuration(track.duration)
            
            if (track.isProcessed) {
                tvStats.text = "已处理 | ${track.maxSpeed?.let { "${String.format("%.1f", it * 3.6)} km/h" } ?: ""}"
                tvStatus.text = "✓ 已处理"
                tvStatus.setBackgroundColor(0xFF4CAF50.toInt())
                tvStatus.setTextColor(0xFFFFFFFF.toInt())
            } else {
                tvStats.text = "GPS: ${track.gpsPointCount} | IMU: ${track.imuPointCount}"
                tvStatus.text = "未处理"
                tvStatus.setBackgroundColor(0xFF9E9E9E.toInt())
                tvStatus.setTextColor(0xFFFFFFFF.toInt())
            }
            
            cardView.setOnClickListener {
                onTrackClick(track)
            }
        }
        
        private fun formatDuration(millis: Long): String {
            val seconds = millis / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            return if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class TrackDiffCallback : DiffUtil.ItemCallback<TrackData>() {
        override fun areItemsTheSame(oldItem: TrackData, newItem: TrackData): Boolean {
            return oldItem.filePath == newItem.filePath
        }

        override fun areContentsTheSame(oldItem: TrackData, newItem: TrackData): Boolean {
            return oldItem == newItem
        }
    }
}
