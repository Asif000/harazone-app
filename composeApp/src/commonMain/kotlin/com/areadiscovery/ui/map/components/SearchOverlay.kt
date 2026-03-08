package com.areadiscovery.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.areadiscovery.ui.components.StreamingTextContent
import com.areadiscovery.ui.theme.MapBackground
import com.areadiscovery.ui.theme.MapSurfaceDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    query: String,
    aiResponse: String,
    isAiResponding: Boolean,
    followUpChips: List<String>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapBackground.copy(alpha = 0.90f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clickable(enabled = false, onClick = {}), // prevent dismiss on content click
        ) {
            // Top: close + search field
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color.White)
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_overlay_input"),
                    placeholder = { Text("Ask anything...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MapSurfaceDark,
                        unfocusedContainerColor = MapSurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isAiResponding || aiResponse.isNotEmpty()) {
                    // AI response card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MapSurfaceDark),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            StreamingTextContent(
                                text = aiResponse,
                                isStreaming = isAiResponding,
                            )
                        }
                    }

                    if (followUpChips.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (chip in followUpChips) {
                                AssistChip(
                                    onClick = { onSubmit(chip) },
                                    label = { Text(chip) },
                                )
                            }
                        }
                    }
                } else {
                    // Zero state — suggested areas
                    Text(
                        text = "Try searching for an area:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (suggestion in listOf("Alfama, Lisbon", "Shibuya, Tokyo", "Brooklyn, NY")) {
                            AssistChip(
                                onClick = { onQueryChange(suggestion); onSubmit(suggestion) },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}
