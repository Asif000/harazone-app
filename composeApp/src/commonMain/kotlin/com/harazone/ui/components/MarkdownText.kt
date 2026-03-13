package com.harazone.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(text = parseMarkdown(text), modifier = modifier, style = style)
}

fun parseMarkdown(text: String): AnnotatedString {
    try {
        return buildAnnotatedString {
            val lines = text.lines()
            for ((lineIndex, rawLine) in lines.withIndex()) {
                val line = if (rawLine.startsWith("- ")) "\u2022 ${rawLine.drop(2)}" else rawLine
                parseInlineMarkdown(this, line)
                if (lineIndex < lines.lastIndex) append("\n")
            }
        }
    } catch (_: Exception) {
        return AnnotatedString(text.replace(Regex("[*`]"), ""))
    }
}

private val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
private val italicPattern = Regex("\\*(.+?)\\*")
private val codePattern = Regex("`(.+?)`")

private fun parseInlineMarkdown(builder: AnnotatedString.Builder, line: String) {
    // Find all spans and sort by position
    data class Span(val start: Int, val end: Int, val content: String, val style: SpanStyle)

    val spans = mutableListOf<Span>()

    boldPattern.findAll(line).forEach { match ->
        spans.add(Span(match.range.first, match.range.last + 1, match.groupValues[1], SpanStyle(fontWeight = FontWeight.Bold)))
    }
    codePattern.findAll(line).forEach { match ->
        spans.add(Span(match.range.first, match.range.last + 1, match.groupValues[1], SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF))))
    }
    italicPattern.findAll(line).forEach { match ->
        // Skip if this overlaps with a bold match (bold uses ** which contains *)
        val overlaps = spans.any { s -> match.range.first >= s.start && match.range.first < s.end }
        if (!overlaps) {
            spans.add(Span(match.range.first, match.range.last + 1, match.groupValues[1], SpanStyle(fontStyle = FontStyle.Italic)))
        }
    }

    // Sort by start position and filter overlaps
    val sorted = spans.sortedBy { it.start }
    val nonOverlapping = mutableListOf<Span>()
    var lastEnd = 0
    for (span in sorted) {
        if (span.start >= lastEnd) {
            nonOverlapping.add(span)
            lastEnd = span.end
        }
    }

    // Build the string
    var pos = 0
    for (span in nonOverlapping) {
        if (span.start > pos) {
            builder.append(line.substring(pos, span.start))
        }
        builder.withStyle(span.style) {
            append(span.content)
        }
        pos = span.end
    }
    if (pos < line.length) {
        builder.append(line.substring(pos))
    }
}
