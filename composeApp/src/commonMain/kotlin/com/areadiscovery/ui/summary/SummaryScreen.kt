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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.ui.components.BucketCard
import com.areadiscovery.ui.components.InlineChatPrompt
import com.areadiscovery.ui.theme.spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: SummaryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val areaName = when (val state = uiState) {
        is SummaryUiState.Streaming -> state.areaName
        is SummaryUiState.Complete -> state.areaName
        is SummaryUiState.LocationFailed -> "Location unavailable"
        else -> "Discovering area..."
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenExit() }
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
                            text = "First visit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search areas",
                            tint = MaterialTheme.colorScheme.onPrimary,
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
        val isRefreshing = uiState is SummaryUiState.Loading || uiState is SummaryUiState.LocationResolving

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
                        contentNote = null,
                        onNavigateToChat = onNavigateToChat,
                        onScrollDepthChanged = { viewModel.updateScrollDepth(it) },
                    )
                }

                is SummaryUiState.LocationResolving -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        BucketList(
                            buckets = BucketType.entries.associateWith { BucketDisplayState(bucketType = it) },
                            areaName = areaName,
                            isComplete = false,
                            contentNote = null,
                            onNavigateToChat = onNavigateToChat,
                            onScrollDepthChanged = { viewModel.updateScrollDepth(it) },
                        )
                    }
                }

                is SummaryUiState.LocationFailed -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ContentNoteBanner(message = state.message)
                        Button(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.spacing.md,
                                    vertical = MaterialTheme.spacing.sm,
                                ),
                        ) {
                            Text("Retry")
                        }
                        Button(
                            onClick = onNavigateToSearch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacing.md),
                        ) {
                            Text("Search an area")
                        }
                        BucketList(
                            buckets = BucketType.entries.associateWith { BucketDisplayState(bucketType = it) },
                            areaName = areaName,
                            isComplete = false,
                            contentNote = null,
                            onNavigateToChat = onNavigateToChat,
                            onScrollDepthChanged = { viewModel.updateScrollDepth(it) },
                        )
                    }
                }

                is SummaryUiState.Streaming -> {
                    BucketList(
                        buckets = state.buckets,
                        areaName = state.areaName,
                        isComplete = false,
                        contentNote = state.contentNote,
                        onNavigateToChat = onNavigateToChat,
                        onScrollDepthChanged = { viewModel.updateScrollDepth(it) },
                    )
                }

                is SummaryUiState.Complete -> {
                    BucketList(
                        buckets = state.buckets,
                        areaName = state.areaName,
                        isComplete = true,
                        contentNote = state.contentNote,
                        onNavigateToChat = onNavigateToChat,
                        onScrollDepthChanged = { viewModel.updateScrollDepth(it) },
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
                            color = MaterialTheme.colorScheme.onSurface,
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
    contentNote: String?,
    onNavigateToChat: (String) -> Unit,
    onScrollDepthChanged: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@snapshotFlow 0
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ((lastVisible + 1) * 100) / totalItems
        }.collect { depthPercent ->
            onScrollDepthChanged(depthPercent)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md),
    ) {
        if (contentNote != null) {
            item(key = "content_note") {
                ContentNoteBanner(message = contentNote)
            }
        }

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

