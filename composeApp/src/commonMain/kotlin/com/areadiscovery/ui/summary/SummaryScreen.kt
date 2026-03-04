package com.areadiscovery.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.ui.components.BucketCard
import com.areadiscovery.ui.components.InlineChatPrompt
import com.areadiscovery.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onNavigateToChat: (String) -> Unit,
    viewModel: SummaryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val areaName = when (val state = uiState) {
        is SummaryUiState.Streaming -> state.areaName
        is SummaryUiState.Complete -> state.areaName
        else -> "Alfama, Lisbon"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = areaName,
                            style = MaterialTheme.typography.displayMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "First visit \u00b7 Morning",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val isRefreshing = uiState is SummaryUiState.Loading

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is SummaryUiState.Loading -> {
                    BucketList(
                        buckets = BucketType.entries.associateWith { BucketDisplayState(bucketType = it) },
                        areaName = areaName,
                        isComplete = false,
                        onNavigateToChat = onNavigateToChat,
                    )
                }

                is SummaryUiState.Streaming -> {
                    BucketList(
                        buckets = state.buckets,
                        areaName = state.areaName,
                        isComplete = false,
                        onNavigateToChat = onNavigateToChat,
                    )
                }

                is SummaryUiState.Complete -> {
                    BucketList(
                        buckets = state.buckets,
                        areaName = state.areaName,
                        isComplete = true,
                        onNavigateToChat = onNavigateToChat,
                    )
                }

                is SummaryUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = MaterialTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.md))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketList(
    buckets: Map<BucketType, BucketDisplayState>,
    areaName: String,
    isComplete: Boolean,
    onNavigateToChat: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md),
    ) {
        items(BucketType.entries) { bucketType ->
            val bucketState = buckets[bucketType] ?: BucketDisplayState(bucketType = bucketType)
            BucketCard(state = bucketState)

            if (bucketType != BucketType.NEARBY) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.lg),
                    color = MaterialTheme.colorScheme.surface,
                )
            }
        }

        if (isComplete) {
            item {
                InlineChatPrompt(
                    areaName = areaName,
                    onNavigateToChat = onNavigateToChat,
                )
            }
        }

        item {
            Spacer(Modifier.height(MaterialTheme.spacing.touchTarget))
        }
    }
}
