// TODO(BACKLOG-MEDIUM): Double-tap to zoom/reset
// TODO(BACKLOG-LOW): Video support — requires ExoPlayer/AVPlayer KMP expect/actual bridge
package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.harazone.ui.components.PlatformBackHandler

private const val MAX_DOTS = 9

@Composable
fun FullscreenImageGallery(
    images: List<String>,
    poiName: String,
    initialIndex: Int = 0,
    vibeColor: Color,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = true) { onDismiss() }

    var isZoomed by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }

    // Reset zoom when page changes
    LaunchedEffect(pagerState.currentPage) { isZoomed = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            var scale by remember(page) { mutableFloatStateOf(1f) }
            var offset by remember(page) { mutableStateOf(Offset.Zero) }
            var intrinsicSize by remember(page) { mutableStateOf(Size.Zero) }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()

                val knownSize = intrinsicSize.isSpecified && intrinsicSize.width > 0 && intrinsicSize.height > 0
                val imageRatio = if (knownSize) intrinsicSize.width / intrinsicSize.height else boxW / boxH
                val boxRatio = boxW / boxH
                val renderedW = if (imageRatio > boxRatio) boxW else boxH * imageRatio
                val renderedH = if (imageRatio > boxRatio) boxW / imageRatio else boxH

                AsyncImage(
                    model = images[page],
                    contentDescription = "Photo ${page + 1} of ${images.size} for $poiName",
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(vibeColor.copy(alpha = 0.3f)),
                    error = ColorPainter(vibeColor.copy(alpha = 0.2f)),
                    onSuccess = { state -> intrinsicSize = state.painter.intrinsicSize },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        // Only consume gestures for multi-touch (pinch-to-zoom).
                        // Single-finger swipes pass through to HorizontalPager.
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.size
                                    if (pointerCount >= 2) {
                                        // Multi-touch: handle zoom + pan
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        isZoomed = scale > 1.01f
                                        if (isZoomed) {
                                            val maxX = (renderedW * (scale - 1)) / 2f
                                            val maxY = (renderedH * (scale - 1)) / 2f
                                            offset = Offset(
                                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                                            )
                                        }
                                        // Consume to prevent pager interference during pinch
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (isZoomed && pointerCount == 1) {
                                        // Single-finger pan only when zoomed
                                        val pan = event.calculatePan()
                                        val maxX = (renderedW * (scale - 1)) / 2f
                                        val maxY = (renderedH * (scale - 1)) / 2f
                                        offset = Offset(
                                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                    // Single-finger at 1x: do NOT consume — pager handles swipe
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
            }
        }

        // Top bar: counter + close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${images.size} photos",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close gallery",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Bottom dots — cap at MAX_DOTS
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dotCount = images.size.coerceAtMost(MAX_DOTS)
                repeat(dotCount) { index ->
                    val isActive = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .background(
                                Color.White.copy(alpha = if (isActive) 1f else 0.4f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
