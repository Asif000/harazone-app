package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.AreaAdvisory
import com.harazone.ui.components.PlatformBackHandler
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@Composable
fun SafetyGateModal(
    advisory: AreaAdvisory,
    hasPreviousArea: Boolean,
    onGoBack: () -> Unit,
    onContinue: () -> Unit,
) {
    PlatformBackHandler(enabled = true) { onGoBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { /* consume taps on scrim */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF85149),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.advisory_banner_title_4),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Red badge
            Text(
                text = advisory.countryName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF85149))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Details bullets
            if (advisory.details.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (detail in advisory.details.take(5)) {
                        Text(
                            text = "\u2022 $detail",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Source attribution
            Text(
                text = stringResource(Res.string.advisory_source),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
            if (advisory.lastUpdated > 0) {
                Text(
                    text = stringResource(Res.string.advisory_last_updated, formatTimestamp(advisory.lastUpdated)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Go Back button
            Button(
                onClick = onGoBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EA043)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (hasPreviousArea) {
                        "\u2190 ${stringResource(Res.string.advisory_go_back)}"
                    } else {
                        stringResource(Res.string.advisory_go_back)
                    },
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Continue button
            OutlinedButton(
                onClick = onContinue,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.advisory_understand_risks))
            }
        }
    }
}

// TODO(BACKLOG-LOW): Replace with kotlinx-datetime formatting when dependency is added
private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0) return ""
    // Simple date formatting without external dependency
    val totalSeconds = epochMs / 1000
    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60
    val totalDays = totalHours / 24

    // Approximate year/month/day from epoch
    val year = 1970 + (totalDays / 365.25).toInt()
    val dayOfYear = (totalDays % 365.25).toInt()
    val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    if (isLeap) monthDays[1] = 29
    var remaining = dayOfYear
    var month = 1
    for (days in monthDays) {
        if (remaining < days) break
        remaining -= days
        month++
    }
    val day = remaining + 1

    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
