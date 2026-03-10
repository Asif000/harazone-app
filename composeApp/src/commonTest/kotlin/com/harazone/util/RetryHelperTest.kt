package com.harazone.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetryHelperTest {

    @Test
    fun successOnFirstAttemptReturnsImmediately() = runTest {
        var attempts = 0
        val result = withRetry { attempts++; "result" }
        assertEquals(Result.success("result"), result)
        assertEquals(1, attempts)
    }

    @Test
    fun successAfterRetryReturnsResult() = runTest {
        var attempts = 0
        val result = withRetry(maxAttempts = 3, initialDelayMs = 100) {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "success"
        }
        assertEquals(Result.success("success"), result)
        assertEquals(2, attempts)
    }

    @Test
    fun allAttemptsExhaustedReturnsFailure() = runTest {
        var attempts = 0
        val error = RuntimeException("persistent failure")
        val result = withRetry(maxAttempts = 3, initialDelayMs = 100) {
            attempts++
            throw error
        }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun delayIsCappedAtMaxDelayMs() = runTest {
        var attempts = 0
        val result = withRetry(
            maxAttempts = 3,
            initialDelayMs = 5000,
            maxDelayMs = 6000
        ) {
            attempts++
            if (attempts < 3) throw RuntimeException("fail")
            "done"
        }
        assertEquals(Result.success("done"), result)
        assertEquals(3, attempts)
        // attempt 2 delay = min(5000, 6000) = 5000, attempt 3 delay = min(10000, 6000) = 6000
        assertEquals(11000L, testScheduler.currentTime)
    }

    @Test
    fun cancellationExceptionIsNotCaught() = runTest {
        assertFailsWith<CancellationException> {
            withRetry {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun singleAttemptSucceeds() = runTest {
        val result = withRetry(maxAttempts = 1) { 42 }
        assertEquals(Result.success(42), result)
    }

    @Test
    fun singleAttemptFailsReturnsFailure() = runTest {
        val error = IllegalStateException("boom")
        val result = withRetry(maxAttempts = 1, initialDelayMs = 100) {
            throw error
        }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun nonRetryableErrorFailsImmediately() = runTest {
        var attempts = 0
        val error = IllegalArgumentException("bad input")
        val result = withRetry(
            maxAttempts = 3,
            initialDelayMs = 1000,
            isRetryable = { it !is IllegalArgumentException }
        ) {
            attempts++
            throw error
        }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        assertEquals(1, attempts, "Non-retryable error should not trigger retries")
    }

    @Test
    fun zeroMaxAttemptsReturnsFailure() = runTest {
        val result = withRetry(maxAttempts = 0) { "should not run" }
        assertTrue(result.isFailure)
    }
}
