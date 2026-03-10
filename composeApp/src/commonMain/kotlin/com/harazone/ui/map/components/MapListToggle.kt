package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harazone.ui.theme.MapFloatingUiDark

@Composable
fun MapListToggle(
    showListView: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(MapFloatingUiDark.copy(alpha = 0.80f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(4.dp),
    ) {
        // Map button
        IconButton(
            onClick = onToggle,
            enabled = showListView,
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (!showListView) Modifier.background(
                        Color.White.copy(alpha = 0.18f),
                        CircleShape,
                    ) else Modifier,
                )
                .semantics { role = Role.Tab; selected = !showListView },
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Map view",
                tint = if (!showListView) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }

        // List button
        IconButton(
            onClick = onToggle,
            enabled = !showListView,
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (showListView) Modifier.background(
                        Color.White.copy(alpha = 0.18f),
                        CircleShape,
                    ) else Modifier,
                )
                .semantics { role = Role.Tab; selected = showListView },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.List,
                contentDescription = "List view",
                tint = if (showListView) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
