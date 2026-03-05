package com.areadiscovery.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 10000,
    isRetryable: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): Result<T> {
    var lastError: Throwable? = null
    var delayMs = initialDelayMs

    for (attempt in 0 until maxAttempts) {
        if (attempt > 0) {
            delay(delayMs.coerceAtMost(maxDelayMs))
            delayMs *= 2
        }
        try {
            return Result.success(block())
        } catch (e: CancellationException) {
            throw e // NEVER swallow CancellationException
        } catch (e: Throwable) {
            lastError = e
            AppLogger.d { "RetryHelper: attempt ${attempt + 1} failed — ${e.message}" }
            if (!isRetryable(e)) {
                return Result.failure(e)
            }
        }
    }

    return Result.failure(lastError ?: Exception("withRetry: unknown failure after $maxAttempts attempts"))
}
