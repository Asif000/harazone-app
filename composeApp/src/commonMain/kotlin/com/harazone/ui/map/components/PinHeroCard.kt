package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.domain.model.POI
import com.harazone.ui.theme.MapFloatingUiDark

@Composable
fun PinHeroCard(
    poi: POI,
    isSaved: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClosed = poi.liveStatus?.contains("closed", ignoreCase = true) == true
    val isOpen = poi.liveStatus?.contains("open", ignoreCase = true) == true
    val stripeColor = when {
        isOpen   -> Color(0xFF4CAF50)
        isClosed -> Color(0xFFF44336)
        else     -> Color(0xFFFFAB40)
    }
    val borderColor = if (isSaved) Color(0xFFFFD700).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .width(180.dp)
            .height(110.dp)
            .clickable { onTap() }
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MapFloatingUiDark.copy(alpha = 0.96f)),
    ) {
        // Status stripe — left border, full card height
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(stripeColor),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            // Vibe label
            Text(
                text = poi.vibes.firstOrNull() ?: poi.vibe,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            // Name
            Text(
                text = poi.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Status pill
            if (poi.liveStatus != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(stripeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = poi.liveStatus,
                        fontSize = 10.sp,
                        color = stripeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Rating
            if (poi.rating != null) {
                Text(
                    text = "★ ${poi.rating}",
                    fontSize = 11.sp,
                    color = Color(0xFFFFD700),
                )
            }
        }
    }
}
