package com.harazone.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.util.appVersionName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        ListItem(
            headlineContent = { Text("Version") },
            trailingContent = { Text(appVersionName) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Send Feedback") },
            leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onSendFeedback() },
        )
        Spacer(Modifier.height(16.dp))
    }
}
