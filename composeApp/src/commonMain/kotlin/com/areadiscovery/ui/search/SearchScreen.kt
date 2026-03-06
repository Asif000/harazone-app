package com.areadiscovery.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.ui.components.BucketCard
import com.areadiscovery.ui.summary.BucketDisplayState
import com.areadiscovery.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search any area...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.search(query)
                        }),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Search for an area" },
                    )
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is SearchUiState.Loading -> LoadingContent(
                query = state.query,
                modifier = Modifier.padding(innerPadding),
            )

            is SearchUiState.Idle -> IdleContent(
                recentSearches = state.recentSearches,
                categoryChips = state.categoryChips,
                onSearch = { chipText ->
                    query = chipText
                    viewModel.search(chipText)
                },
                modifier = Modifier.padding(innerPadding),
            )

            is SearchUiState.Streaming -> PortraitContent(
                areaName = state.areaName,
                buckets = state.buckets,
                modifier = Modifier.padding(innerPadding),
            )

            is SearchUiState.Complete -> PortraitContent(
                areaName = state.areaName,
                buckets = state.buckets,
                modifier = Modifier.padding(innerPadding),
            )

            is SearchUiState.Error -> ErrorContent(
                query = state.query,
                message = state.message,
                onRetry = { viewModel.search(state.query) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun IdleContent(
    recentSearches: List<String>,
    categoryChips: List<String>,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.md),
    ) {
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        if (recentSearches.isNotEmpty()) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                recentSearches.forEach { recent ->
                    SuggestionChip(
                        onClick = { onSearch(recent) },
                        label = { Text(recent) },
                    )
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.md))
        }

        Text(
            text = "Explore",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            categoryChips.forEach { chip ->
                SuggestionChip(
                    onClick = { onSearch(chip) },
                    label = { Text(chip) },
                )
            }
        }
    }
}

@Composable
private fun PortraitContent(
    areaName: String,
    buckets: Map<BucketType, BucketDisplayState>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md),
    ) {
        item {
            Text(
                text = areaName,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.sm),
            )
        }
        items(BucketType.entries.toList()) { bucketType ->
            val bucketState = buckets[bucketType]
            if (bucketState != null) {
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
        }
        item {
            Spacer(Modifier.height(MaterialTheme.spacing.touchTarget))
        }
    }
}

@Composable
private fun LoadingContent(
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
        Text(
            text = "Discovering $query...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md),
        )
    }
}

@Composable
private fun ErrorContent(
    query: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.md),
    ) {
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
        Text(
            text = "Couldn't load portrait for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Button(onClick = onRetry) {
            Text("Try again")
        }
    }
}
