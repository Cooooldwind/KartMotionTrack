package com.karttracker.model

data class FilteredIMUData(
    val accel: FloatArray,
    val gyro: FloatArray,
    val vibrationLevel: Float,
    val filterStrength: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilteredIMUData

        if (!accel.contentEquals(other.accel)) return false
        if (!gyro.contentEquals(other.gyro)) return false
        if (vibrationLevel != other.vibrationLevel) return false
        if (filterStrength != other.filterStrength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accel.contentHashCode()
        result = 31 * result + gyro.contentHashCode()
        result = 31 * result + vibrationLevel.hashCode()
        result = 31 * result + filterStrength.hashCode()
        return result
    }
}
