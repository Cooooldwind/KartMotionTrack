package com.karttracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TrackVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var trackPoints: List<TrackPointData> = emptyList()
    
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val startMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    
    private val endMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        style = Paint.Style.FILL
    }
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    data class TrackPointData(
        val lat: Double,
        val lon: Double
    )

    fun setTrackData(points: List<TrackPointData>) {
        this.trackPoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (trackPoints.size < 2) {
            drawEmptyState(canvas)
            return
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val bounds = calculateBounds()
        val scale = calculateScale(bounds)
        val offset = calculateOffset(bounds, scale)

        drawGrid(canvas, bounds, scale, offset)
        drawTrack(canvas, scale, offset)
        drawMarkers(canvas, scale, offset)
    }

    private fun calculateBounds(): RectF {
        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE

        for (point in trackPoints) {
            minLat = min(minLat, point.lat)
            maxLat = max(maxLat, point.lat)
            minLon = min(minLon, point.lon)
            maxLon = max(maxLon, point.lon)
        }

        val paddingLat = (maxLat - minLat) * 0.1
        val paddingLon = (maxLon - minLon) * 0.1

        return RectF(
            minLon.toFloat(),
            maxLat.toFloat(),
            maxLon.toFloat(),
            minLat.toFloat()
        )
    }

    private fun calculateScale(bounds: RectF): Float {
        val latRange = abs(bounds.top - bounds.bottom)
        val lonRange = abs(bounds.right - bounds.left)

        val padding = 60f
        val drawWidth = width - padding * 2
        val drawHeight = height - padding * 2

        val scaleX = if (lonRange > 0) drawWidth / lonRange else 1f
        val scaleY = if (latRange > 0) drawHeight / latRange else 1f

        return min(scaleX, scaleY)
    }

    private fun calculateOffset(bounds: RectF, scale: Float): FloatArray {
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2

        val offsetX = (width / 2f) - (centerX - bounds.left) * scale
        val offsetY = (height / 2f) - (centerY - bounds.top) * scale

        return floatArrayOf(offsetX, offsetY)
    }

    private fun drawGrid(canvas: Canvas, bounds: RectF, scale: Float, offset: FloatArray) {
        val left = bounds.left - 0.0001
        val right = bounds.right + 0.0001
        val top = bounds.top + 0.0001
        val bottom = bounds.bottom - 0.0001

        val gridStep = 0.0005
        var x = left
        while (x < right) {
            val px = (x - bounds.left) * scale + offset[0]
            canvas.drawLine(px, 0f, px, height.toFloat(), gridPaint)
            x += gridStep
        }

        var y = bottom
        while (y < top) {
            val py = (y - bounds.bottom) * scale + offset[1]
            canvas.drawLine(0f, py, width.toFloat(), py, gridPaint)
            y += gridStep
        }
    }

    private fun drawTrack(canvas: Canvas, scale: Float, offset: FloatArray) {
        if (trackPoints.size < 2) return

        val bounds = calculateBounds()
        val path = Path()

        for ((index, point) in trackPoints.withIndex()) {
            val x = (point.lon - bounds.left) * scale + offset[0]
            val y = (point.lat - bounds.bottom) * scale + offset[1]

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, trackPaint)
    }

    private fun drawMarkers(canvas: Canvas, scale: Float, offset: FloatArray) {
        if (trackPoints.isEmpty()) return

        val bounds = calculateBounds()

        val startPoint = trackPoints.first()
        val startX = (startPoint.lon - bounds.left) * scale + offset[0]
        val startY = (startPoint.lat - bounds.bottom) * scale + offset[1]
        canvas.drawCircle(startX, startY, 12f, startMarkerPaint)

        val endPoint = trackPoints.last()
        val endX = (endPoint.lon - bounds.left) * scale + offset[0]
        val endY = (endPoint.lat - bounds.bottom) * scale + offset[1]
        canvas.drawCircle(endX, endY, 12f, endMarkerPaint)
    }

    private fun drawEmptyState(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("暂无轨迹数据", width / 2f, height / 2f, textPaint)
    }
}
