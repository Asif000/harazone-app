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
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_version)) },
            trailingContent = { Text(appVersionName) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_send_feedback)) },
            leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
            modifier = Modifier.clickable { onSendFeedback() },
        )
        Spacer(Modifier.height(16.dp))
    }
}
