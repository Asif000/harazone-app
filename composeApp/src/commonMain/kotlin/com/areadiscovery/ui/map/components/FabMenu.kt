package com.areadiscovery.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.areadiscovery.ui.theme.MapSurfaceDark

@Composable
fun FabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSavedPlaces: () -> Unit,
    onSettings: () -> Unit,
    savedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier,
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(spring()) { it } + fadeIn(),
            exit = slideOutVertically(spring()) { it } + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                FabMenuItem(
                    label = if (savedCount > 0) "Saved Places ($savedCount)" else "Saved Places",
                    icon = Icons.Default.Bookmark,
                    onClick = onSavedPlaces,
                )
                FabMenuItem(
                    label = "Settings",
                    icon = Icons.Default.Settings,
                    onClick = onSettings,
                )
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MapSurfaceDark,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (isExpanded) "Close menu" else "Open menu",
            )
        }
    }
}

@Composable
fun FabScrim(
    visible: Boolean,
    onClick: () -> Unit,
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MapSurfaceDark.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
