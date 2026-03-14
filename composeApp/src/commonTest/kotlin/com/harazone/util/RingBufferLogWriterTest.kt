package com.harazone.util

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingBufferLogWriterTest {

    @Test
    fun ringEviction_givenMoreThanMaxLines_returnsExactlyMaxLines() {
        val writer = RingBufferLogWriter(maxLines = 50, maxLineLength = 200)
        repeat(60) { i ->
            writer.log(Severity.Info, "message $i", "tag", null)
        }
        val lines = writer.getLines().split("\n")
        assertEquals(50, lines.size)
        // First 10 should have been evicted; line 0 should contain "message 10"
        assertTrue(lines.first().contains("message 10"))
        assertTrue(lines.last().contains("message 59"))
    }

    @Test
    fun empty_givenNoEntries_returnsEmptyString() {
        val writer = RingBufferLogWriter()
        assertEquals("", writer.getLines())
    }

    @Test
    fun lineCap_givenLongMessage_truncatesTo200Chars() {
        val writer = RingBufferLogWriter(maxLines = 50, maxLineLength = 200)
        val longMessage = "A".repeat(500)
        writer.log(Severity.Warn, longMessage, "tag", null)
        val output = writer.getLines()
        assertTrue(output.length <= 200, "Line should be truncated to maxLineLength")
    }

    @Test
    fun bytesCap_givenMaxAsciiContent_staysUnder8000Bytes() {
        val writer = RingBufferLogWriter(maxLines = 50, maxLineLength = 200)
        // Fill with max-length ASCII lines
        repeat(50) { i ->
            writer.log(Severity.Info, "M".repeat(180), "tag$i", null)
        }
        val output = writer.getLines()
        val byteSize = output.encodeToByteArray().size
        assertTrue(byteSize <= 8000, "Output bytes ($byteSize) should be <= 8000")
    }
}
