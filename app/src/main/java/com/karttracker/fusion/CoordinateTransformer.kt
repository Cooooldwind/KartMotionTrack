package com.karttracker.fusion

class CoordinateTransformer {
    
    fun quaternionToRotationMatrix(q: FloatArray): Array<FloatArray> {
        val w = q[0]
        val x = q[1]
        val y = q[2]
        val z = q[3]
        
        return arrayOf(
            floatArrayOf(
                1 - 2 * y * y - 2 * z * z,
                2 * x * y - 2 * z * w,
                2 * x * z + 2 * y * w
            ),
            floatArrayOf(
                2 * x * y + 2 * z * w,
                1 - 2 * x * x - 2 * z * z,
                2 * y * z - 2 * x * w
            ),
            floatArrayOf(
                2 * x * z - 2 * y * w,
                2 * y * z + 2 * x * w,
                1 - 2 * x * x - 2 * y * y
            )
        )
    }
    
    fun rotateVector(rotationMatrix: Array<FloatArray>, vector: FloatArray): FloatArray {
        return floatArrayOf(
            rotationMatrix[0][0] * vector[0] + rotationMatrix[0][1] * vector[1] + rotationMatrix[0][2] * vector[2],
            rotationMatrix[1][0] * vector[0] + rotationMatrix[1][1] * vector[1] + rotationMatrix[1][2] * vector[2],
            rotationMatrix[2][0] * vector[0] + rotationMatrix[2][1] * vector[1] + rotationMatrix[2][2] * vector[2]
        )
    }
    
    fun quaternionToEuler(q: FloatArray): FloatArray {
        val w = q[0]
        val x = q[1]
        val y = q[2]
        val z = q[3]
        
        val roll = kotlin.math.atan2(
            2 * (w * x + y * z),
            1 - 2 * (x * x + y * y)
        )
        
        val pitch = kotlin.math.asin(
            2 * (w * y - z * x)
        ).coerceIn(-kotlin.math.PI.toFloat() / 2, kotlin.math.PI.toFloat() / 2).toFloat()
        
        val yaw = kotlin.math.atan2(
            2 * (w * z + x * y),
            1 - 2 * (y * y + z * z)
        )
        
        return floatArrayOf(roll.toFloat(), pitch, yaw.toFloat())
    }
}
