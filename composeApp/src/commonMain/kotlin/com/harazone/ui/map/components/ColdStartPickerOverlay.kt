package com.harazone.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harazone.ui.components.PlatformBackHandler

private data class VibeArchetype(val label: String, val icon: String)

private val archetypes = listOf(
    VibeArchetype("Art & Culture", "\uD83C\uDFA8"),
    VibeArchetype("Food Scene", "\uD83C\uDF5C"),
    VibeArchetype("History", "\uD83C\uDFDB\uFE0F"),
    VibeArchetype("Nature", "\uD83C\uDF3F"),
    VibeArchetype("Music & Nightlife", "\uD83C\uDFB6"),
    VibeArchetype("Adventure", "\uD83C\uDFC4"),
    VibeArchetype("Shopping", "\uD83D\uDECD\uFE0F"),
    VibeArchetype("Cafes & Slow Days", "\u2615"),
    VibeArchetype("Architecture", "\uD83C\uDFD7\uFE0F"),
    VibeArchetype("After Dark", "\uD83C\uDF19"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColdStartPickerOverlay(
    onConfirm: (List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }

    PlatformBackHandler(enabled = true) { onSkip() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1A1A2E))
                .padding(24.dp),
        ) {
            Text(
                text = "What excites you? \u2728",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick 2-3 to personalize your discovery",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                archetypes.forEach { archetype ->
                    val isSelected = archetype.label in selected
                    PickerCard(
                        icon = archetype.icon,
                        label = archetype.label,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selected.remove(archetype.label)
                            } else if (selected.size < 3) {
                                selected.add(archetype.label)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "${selected.size} of 3 selected",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Let's Go \u2192", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSkip) {
                Text("Skip", color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PickerCard(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.15f)
    val bgColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 96.dp, height = 80.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
