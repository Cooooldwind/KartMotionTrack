package com.karttracker.processing

class GravityExtractor(
    private val cutoffFreq: Float = 0.5f,
    private val sampleRate: Float = 200f
) {
    private val lowPass = LowPassFilter(cutoffFreq = cutoffFreq, sampleRate = sampleRate)
    
    fun extract(accelWorld: FloatArray): Pair<FloatArray, FloatArray> {
        val gravity = lowPass.apply(accelWorld)
        
        val linearAccel = FloatArray(3)
        for (i in 0..2) {
            linearAccel[i] = accelWorld[i] - gravity[i]
        }
        
        return Pair(linearAccel, gravity)
    }
    
    fun reset() {
        lowPass.reset()
    }
}
