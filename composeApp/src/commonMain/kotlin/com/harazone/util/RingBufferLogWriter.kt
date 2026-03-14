package com.harazone.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class RingBufferLogWriter(
    private val maxLines: Int = 50,
    private val maxLineLength: Int = 200,
) : LogWriter() {

    private val lock = SynchronizedObject()
    private val deque = ArrayDeque<String>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) =
        synchronized(lock) {
            val line = "[${severity.name}] $tag: $message".take(maxLineLength)
            if (deque.size >= maxLines) deque.removeFirst()
            deque.addLast(line)
        }

    fun getLines(): String {
        val joined = synchronized(lock) { deque.joinToString("\n") }
        val bytes = joined.encodeToByteArray()
        return if (bytes.size <= 8000) joined else joined.take(7500)
    }
}

val ringBufferLogWriter = RingBufferLogWriter()
