package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MenuBg = Color(0xFF1A1A2A)
private val MenuText = Color(0xFFE0E0E0)
private val MenuTextSecondary = Color(0xFF888888)

/**
 * Hamburger menu bottom sheet — rises from the bottom bar.
 * Scrim covers map + carousel (not the bottom bar itself). (D16)
 *
 * Must be placed inside a full-screen Box so the scrim and menu
 * position correctly relative to the screen, not the bottom bar.
 */
@Composable
fun HamburgerMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onAIPersonality: () -> Unit,
    onSettings: () -> Unit,
    onFeedback: () -> Unit,
    bottomBarHeight: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim — covers everything except bottom bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )

        // Menu sheet — bottom-left aligned, above the bottom bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomBarHeight)
                .fillMaxWidth(0.65f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MenuBg)
                .padding(vertical = 12.dp),
        ) {
            MenuItem(
                icon = Icons.Default.Person,
                label = "Profile",
                onClick = { onDismiss(); onProfile() },
            )
            MenuItem(
                icon = Icons.Default.Psychology,
                label = "AI Personality",
                onClick = { onDismiss(); onAIPersonality() },
            )
            MenuItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = { onDismiss(); onSettings() },
            )
            MenuItem(
                icon = Icons.Default.Feedback,
                label = "Send Feedback",
                onClick = { onDismiss(); onFeedback() },
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MenuTextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MenuText,
        )
    }
}
