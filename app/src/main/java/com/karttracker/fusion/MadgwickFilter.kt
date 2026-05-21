package com.karttracker.fusion

import kotlin.math.sqrt

class MadgwickFilter(
    private val beta: Float = 0.1f
) {
    private var q = floatArrayOf(1f, 0f, 0f, 0f)
    private val transformer = CoordinateTransformer()
    
    fun getQuaternion(): FloatArray = q.copyOf()
    
    fun getRotationMatrix(): Array<FloatArray> = transformer.quaternionToRotationMatrix(q)
    
    fun getEulerAngles(): FloatArray = transformer.quaternionToEuler(q)
    
    fun rotateVectorToWorld(vector: FloatArray): FloatArray {
        return transformer.rotateVector(getRotationMatrix(), vector)
    }
    
    fun update(accel: FloatArray, gyro: FloatArray, mag: FloatArray, dt: Float) {
        val normAccel = normalize(accel)
        val normMag = normalize(mag)
        
        val gradient = computeGradient(q, normAccel, normMag)
        
        val qDot = floatArrayOf(
            0.5f * (-q[1] * gyro[0] - q[2] * gyro[1] - q[3] * gyro[2]),
            0.5f * (q[0] * gyro[0] + q[2] * gyro[2] - q[3] * gyro[1]),
            0.5f * (q[0] * gyro[1] - q[1] * gyro[2] + q[3] * gyro[0]),
            0.5f * (q[0] * gyro[2] + q[1] * gyro[1] - q[2] * gyro[0])
        )
        
        for (i in 0..3) {
            q[i] += (qDot[i] - beta * gradient[i]) * dt
        }
        
        q = normalize(q)
    }
    
    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { it * it.toDouble() }).toFloat()
        return if (norm > 0) v.map { it / norm }.toFloatArray() else v.copyOf()
    }
    
    private fun computeGradient(q: FloatArray, accel: FloatArray, mag: FloatArray): FloatArray {
        val w = q[0]
        val x = q[1]
        val y = q[2]
        val z = q[3]
        
        val hx = 2 * mag[0] * (0.5f - y * y - z * z) + 2 * mag[1] * (x * y - w * z) + 2 * mag[2] * (x * z + w * y)
        val hy = 2 * mag[0] * (x * y + w * z) + 2 * mag[1] * (0.5f - x * x - z * z) + 2 * mag[2] * (y * z - w * x)
        val bx = sqrt((hx * hx + hy * hy).toDouble()).toFloat()
        val bz = 2 * mag[0] * (x * z - w * y) + 2 * mag[1] * (y * z + w * x) + 2 * mag[2] * (0.5f - x * x - y * y)
        
        val f = floatArrayOf(
            2 * (x * z - w * y) - accel[0],
            2 * (w * x + y * z) - accel[1],
            2 * (0.5f - x * x - y * y) - accel[2],
            2 * bx * (0.5f - y * y - z * z) + 2 * bz * (x * z - w * y) - mag[0],
            2 * bx * (x * y - w * z) + 2 * bz * (w * x + y * z) - mag[1],
            2 * bx * (w * y + x * z) + 2 * bz * (0.5f - x * x - y * y) - mag[2]
        )
        
        val j = arrayOf(
            floatArrayOf(-2 * y, 2 * z, -2 * w, 2 * x),
            floatArrayOf(2 * x, 2 * w, 2 * z, 2 * y),
            floatArrayOf(0f, -4 * x, -4 * y, 0f),
            floatArrayOf(-2 * bz * y, 2 * bz * z, -4 * bx * y - 2 * bz * w, -4 * bx * z + 2 * bz * x),
            floatArrayOf(-2 * bx * z + 2 * bz * x, 2 * bx * y + 2 * bz * w, 2 * bx * x + 2 * bz * z, -2 * bx * w + 2 * bz * y),
            floatArrayOf(2 * bx * y, 2 * bx * z - 4 * bz * x, 2 * bx * w - 4 * bz * y, 2 * bx * x)
        )
        
        val gradient = FloatArray(4)
        for (col in 0..3) {
            for (row in 0..5) {
                gradient[col] += j[row][col] * f[row]
            }
        }
        
        return normalize(gradient)
    }
    
    fun reset() {
        q = floatArrayOf(1f, 0f, 0f, 0f)
    }
}
