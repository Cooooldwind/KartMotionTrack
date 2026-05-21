package com.karttracker.storage

class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity)
    private var head = 0
    private var tail = 0
    private var count = 0
    
    fun add(item: T) {
        buffer[tail] = item
        tail = (tail + 1) % capacity
        if (count < capacity) {
            count++
        } else {
            head = (head + 1) % capacity
        }
    }
    
    fun drainTo(maxSize: Int): List<T> {
        val result = mutableListOf<T>()
        val takeCount = minOf(count, maxSize)
        for (i in 0 until takeCount) {
            @Suppress("UNCHECKED_CAST")
            result.add(buffer[(head + i) % capacity] as T)
        }
        head = (head + takeCount) % capacity
        count -= takeCount
        return result
    }
    
    fun size(): Int = count
    
    fun isEmpty(): Boolean = count == 0
    
    fun isFull(): Boolean = count == capacity
    
    fun clear() {
        head = 0
        tail = 0
        count = 0
        buffer.fill(null)
    }
}
