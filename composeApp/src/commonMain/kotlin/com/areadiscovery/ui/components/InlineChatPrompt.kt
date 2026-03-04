package com.areadiscovery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.areadiscovery.ui.theme.spacing

@Composable
fun InlineChatPrompt(
    areaName: String,
    onNavigateToChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Ask a question about $areaName" },
    ) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.bucketInternal))
        Text(
            text = "Want to know more about $areaName?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToChat("") },
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                placeholder = { Text("Ask anything...") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
