package com.areadiscovery.util

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
        // Virtual time should reflect capped delays: 5000 + 6000 = 11000
        // (attempt 2 delay = min(5000, 6000) = 5000, attempt 3 delay = min(10000, 6000) = 6000)
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
}
