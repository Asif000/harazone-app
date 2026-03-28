package com.harazone.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.DataClassification
import com.harazone.domain.model.DataConfidence
import com.harazone.domain.model.ResidentCategory
import com.harazone.domain.model.ResidentData
import com.harazone.domain.model.ResidentData.Companion.CAT_COL
import com.harazone.domain.model.ResidentData.Companion.CAT_RENTAL
import com.harazone.domain.model.ResidentData.Companion.CAT_SAFETY
import com.harazone.domain.model.ResidentDataPoint
import com.harazone.ui.components.PlatformBackHandler
import com.harazone.ui.theme.MapFloatingUiDark
import com.harazone.ui.theme.MetaContextTeal

@Composable
fun ResidentDashboardCard(
    residentData: ResidentData?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLoading && residentData == null) {
        ShimmerDashboard(modifier)
        return
    }
    if (residentData == null) return

    var isExpanded by remember { mutableStateOf(false) }

    PlatformBackHandler(enabled = isExpanded) { isExpanded = false }

    Surface(
        onClick = { isExpanded = !isExpanded },
        color = MapFloatingUiDark.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            residentData.originContext?.let { origin ->
                Text(
                    text = origin,
                    style = MaterialTheme.typography.labelSmall,
                    color = MetaContextTeal,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            CollapsedHeadlines(
                categories = residentData.categories,
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                // TODO(BACKLOG-LOW): verticalScroll inside AnimatedVisibility(expandVertically) will conflict with any parent scroll container — add nestedScrollConnection if MapScreen ever wraps this area in a scroll context
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    residentData.categories.forEach { category ->
                        CategorySection(category)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = "Data for informational purposes only. Verify before making decisions.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedHeadlines(
    categories: List<ResidentCategory>,
) {
    val headlines = listOf(ResidentData.CAT_RENTAL, ResidentData.CAT_COL, ResidentData.CAT_SAFETY).mapNotNull { id ->
        categories.firstOrNull { it.id == id }?.let { cat ->
            "${cat.icon} ${cat.points.firstOrNull()?.value ?: ""}"
        }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        headlines.forEach { headline ->
            Text(
                text = headline,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategorySection(category: ResidentCategory) {
    val uriHandler = LocalUriHandler.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${category.icon} ${category.label}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(Modifier.width(6.dp))
            AiBadge()
        }
        Spacer(Modifier.height(4.dp))

        category.points.forEach { point ->
            DataPointRow(point)
            if (point.classification == DataClassification.VOLATILE && point.verifyUrl != null) {
                Text(
                    text = "Verify locally \u2192",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MetaContextTeal,
                    modifier = Modifier
                        .clickable { uriHandler.openUri(point.verifyUrl) }
                        .padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DataPointRow(point: ResidentDataPoint) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = point.value,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Spacer(Modifier.width(6.dp))
            ConfidenceDot(point.confidence)
        }
        if (point.detail.isNotBlank()) {
            Text(
                text = point.detail,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Text(
            text = point.sourceLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun ConfidenceDot(confidence: DataConfidence) {
    val color = when (confidence) {
        DataConfidence.HIGH -> Color(0xFF4CAF50)
        DataConfidence.MEDIUM -> Color(0xFFFFB300)
        DataConfidence.LOW -> Color(0xFFE53935)
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun AiBadge() {
    Text(
        text = "\uD83E\uDD16 AI Insight",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = Color.White.copy(alpha = 0.5f),
    )
}

@Composable
private fun ShimmerDashboard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Surface(
        color = MapFloatingUiDark.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "\uD83C\uDFE0 Loading resident data\u2026",
                style = MaterialTheme.typography.labelSmall,
                color = MetaContextTeal.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
