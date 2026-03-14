package com.harazone.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.util.toImageBitmap
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackPreviewSheet(
    screenshotBytes: ByteArray?,
    onDismiss: () -> Unit,
    onSend: (description: String) -> Unit,
) {
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true) { onDismiss() }
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
            if (screenshotBytes != null) {
                val bitmap = remember(screenshotBytes) { screenshotBytes.toImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Screenshot preview",
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(stringResource(Res.string.feedback_screenshot_captured), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(stringResource(Res.string.feedback_description_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feedback_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSend(description) }) { Text(stringResource(Res.string.feedback_send_report)) }
            }
        }
    }
}
