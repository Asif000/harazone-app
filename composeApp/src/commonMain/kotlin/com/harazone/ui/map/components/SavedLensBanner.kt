package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harazone.ui.theme.SavedLensBg
import com.harazone.ui.theme.SavedLensTeal

@Composable
fun SavedLensBanner(
    totalSavedCount: Int,
    onExit: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SavedLensBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "♥",
                color = Color(0xFFFF5252),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Saved Places",
                color = SavedLensTeal,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "— $totalSavedCount total",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onExit,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit saved lens",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
