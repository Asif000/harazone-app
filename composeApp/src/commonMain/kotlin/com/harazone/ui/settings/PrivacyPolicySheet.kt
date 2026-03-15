package com.harazone.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.ui.components.PlatformBackHandler
import areadiscovery.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun PrivacyPolicySheet(onDismiss: () -> Unit) {
    var content by remember { mutableStateOf<AnnotatedString?>(null) }

    LaunchedEffect(Unit) {
        val bytes = Res.readBytes("files/privacy-policy.md")
        val raw = bytes.decodeToString()
        content = parseMarkdown(raw)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        content?.let { text ->
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

private fun parseMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    for (line in raw.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    append(trimmed.removePrefix("# "))
                }
                append("\n\n")
            }
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    append(trimmed.removePrefix("## "))
                }
                append("\n\n")
            }
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                    append(trimmed.removePrefix("### "))
                }
                append("\n\n")
            }
            trimmed == "---" -> {
                append("\n")
            }
            trimmed.startsWith("|") -> {
                // Table row — strip pipes and show as plain text
                val cells = trimmed.split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.all { c -> c == '-' } }
                if (cells.isNotEmpty()) {
                    append(cells.joinToString("  •  "))
                    append("\n")
                }
            }
            trimmed.startsWith("- ") -> {
                append("  • ")
                appendInlineFormatted(trimmed.removePrefix("- "))
                append("\n")
            }
            trimmed.isEmpty() -> {
                append("\n")
            }
            else -> {
                appendInlineFormatted(trimmed)
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatted(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '[' -> {
                // Link: [text](url) — show just the text
                val closeBracket = text.indexOf(']', i)
                val openParen = if (closeBracket != -1) closeBracket + 1 else -1
                if (openParen < text.length && openParen != -1 && text.getOrNull(openParen) == '(') {
                    val closeParen = text.indexOf(')', openParen)
                    if (closeParen != -1) {
                        append(text.substring(i + 1, closeBracket))
                        i = closeParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
