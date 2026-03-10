package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BucketTypeTest {

    @Test
    fun bucketTypeContainsAllSixTypes() {
        val expected = setOf("SAFETY", "CHARACTER", "WHATS_HAPPENING", "COST", "HISTORY", "NEARBY")
        val actual = BucketType.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun bucketTypeHasExactlySixEntries() {
        assertEquals(6, BucketType.entries.size)
    }

    @Test
    fun confidenceContainsAllThreeLevels() {
        val expected = setOf("HIGH", "MEDIUM", "LOW")
        val actual = Confidence.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun messageRoleContainsUserAndAi() {
        val expected = setOf("USER", "AI")
        val actual = MessageRole.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
