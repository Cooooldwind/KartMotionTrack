package com.karttracker.processing

class KalmanGPSFilter {
    private var latEstimate = 0.0
    private var lonEstimate = 0.0
    private var latVariance = 1000.0
    private var lonVariance = 1000.0
    private var initialized = false
    
    companion object {
        private const val MIN_ACCURACY = 1f
        private const val ACCURACY_SCALE = 10.0
    }
    
    fun reset() {
        initialized = false
        latEstimate = 0.0
        lonEstimate = 0.0
        latVariance = 1000.0
        lonVariance = 1000.0
    }
    
    fun process(
        lat: Double, lon: Double, accuracy: Float
    ): Pair<Double, Double> {
        if (!initialized) {
            latEstimate = lat
            lonEstimate = lon
            latVariance = accuracy * ACCURACY_SCALE
            lonVariance = accuracy * ACCURACY_SCALE
            initialized = true
            return Pair(latEstimate, lonEstimate)
        }
        
        latVariance += 0.00001
        lonVariance += 0.00001
        
        val accuracyScaled = maxOf(accuracy, MIN_ACCURACY) * ACCURACY_SCALE
        
        val latKalmanGain = latVariance / (latVariance + accuracyScaled)
        val lonKalmanGain = lonVariance / (lonVariance + accuracyScaled)
        
        latEstimate = latEstimate + latKalmanGain * (lat - latEstimate)
        lonEstimate = lonEstimate + lonKalmanGain * (lon - lonEstimate)
        
        latVariance = (1 - latKalmanGain) * latVariance
        lonVariance = (1 - lonKalmanGain) * lonVariance
        
        return Pair(latEstimate, lonEstimate)
    }
}
