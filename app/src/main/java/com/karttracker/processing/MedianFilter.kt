package com.karttracker.processing

class MedianFilter(
    private val windowSize: Int = 5
) {
    private val windows = Array(3) { mutableListOf<Float>() }
    
    fun apply(input: FloatArray): FloatArray {
        val output = FloatArray(3)
        for (i in 0..2) {
            windows[i].add(input[i])
            if (windows[i].size > windowSize) {
                windows[i].removeAt(0)
            }
            output[i] = windows[i].sorted()[windows[i].size / 2]
        }
        return output
    }
    
    fun reset() {
        for (i in 0..2) {
            windows[i].clear()
        }
    }
}
