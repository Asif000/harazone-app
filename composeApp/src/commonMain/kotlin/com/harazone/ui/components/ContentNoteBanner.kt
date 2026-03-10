package com.harazone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harazone.ui.theme.spacing

// UX spec: warm beige bg (#F5EDE3) + warm gray text (#6B5E54)
// These match our theme's surface and onSurfaceVariant tokens (see Color.kt)
@Composable
fun ContentNoteBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
    )
}
