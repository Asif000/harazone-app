package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.AreaAdvisory
import com.harazone.ui.components.PlatformBackHandler
import org.jetbrains.compose.resources.stringResource
import areadiscovery.composeapp.generated.resources.*

@Composable
fun SafetyBanner(
    advisory: AreaAdvisory,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = isVisible) { onDismiss() }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val bgColor = advisory.level.bannerBackgroundColor()
        val uriHandler = LocalUriHandler.current

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = bannerTitle(advisory.level),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = advisory.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (advisory.sourceUrl.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.advisory_learn_more),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(advisory.sourceUrl)
                        },
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun bannerTitle(level: AdvisoryLevel): String = when (level) {
    AdvisoryLevel.CAUTION -> stringResource(Res.string.advisory_banner_title_2)
    AdvisoryLevel.RECONSIDER -> stringResource(Res.string.advisory_banner_title_3)
    AdvisoryLevel.DO_NOT_TRAVEL -> stringResource(Res.string.advisory_banner_title_4)
    else -> ""
}

private fun AdvisoryLevel.bannerBackgroundColor(): Color = when (this) {
    AdvisoryLevel.CAUTION -> Color(0xCCE3B341)
    AdvisoryLevel.RECONSIDER -> Color(0xCCDB6D28)
    AdvisoryLevel.DO_NOT_TRAVEL -> Color(0xCCDA3633)
    else -> Color(0xCC888888)
}
