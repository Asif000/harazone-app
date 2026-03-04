package com.areadiscovery.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.areadiscovery.domain.model.BucketType

fun BucketType.displayTitle(): String = when (this) {
    BucketType.SAFETY -> "Safety"
    BucketType.CHARACTER -> "Character"
    BucketType.WHATS_HAPPENING -> "What's Happening"
    BucketType.COST -> "Cost"
    BucketType.HISTORY -> "History"
    BucketType.NEARBY -> "Nearby"
}

fun BucketType.icon(): ImageVector = when (this) {
    BucketType.SAFETY -> Icons.Filled.Shield
    BucketType.CHARACTER -> Icons.Filled.Palette
    BucketType.WHATS_HAPPENING -> Icons.Filled.Event
    BucketType.COST -> Icons.Filled.AttachMoney
    BucketType.HISTORY -> Icons.Filled.History
    BucketType.NEARBY -> Icons.Filled.Explore
}
