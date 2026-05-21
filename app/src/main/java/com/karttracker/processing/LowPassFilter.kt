package com.karttracker.processing

import kotlin.math.PI

class LowPassFilter(
    private val cutoffFreq: Float = 15f,
    private val sampleRate: Float = 200f
) {
    private val alpha: Float
    private var lastValue: FloatArray? = null
    
    init {
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoffFreq)
        alpha = dt / (rc + dt)
    }
    
    fun apply(input: FloatArray): FloatArray {
        val last = lastValue ?: input
        val output = FloatArray(input.size)
        for (i in input.indices) {
            output[i] = alpha * input[i] + (1 - alpha) * last[i]
        }
        lastValue = output.copyOf()
        return output
    }
    
    fun reset() {
        lastValue = null
    }
}
