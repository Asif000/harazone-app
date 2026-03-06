package com.areadiscovery.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.ui.components.POIDetailCard
import com.areadiscovery.ui.summary.ContentNoteBanner
import com.areadiscovery.ui.theme.spacing
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    onPoiCountChanged: (Int) -> Unit = {},
    onNavigateToMaps: (lat: Double, lon: Double, name: String) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val poiCount = (uiState as? MapUiState.Ready)?.pois?.size ?: 0
    LaunchedEffect(poiCount) {
        onPoiCountChanged(poiCount)
    }

    when (val state = uiState) {
        is MapUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is MapUiState.LocationFailed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ContentNoteBanner(message = state.message)
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                Button(onClick = { viewModel.retry() }) {
                    Text("Retry")
                }
            }
        }

        is MapUiState.Ready -> {
            val scaffoldState = rememberBottomSheetScaffoldState()
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            // Auto-expand/collapse sheet based on POI selection
            LaunchedEffect(state.selectedPoi) {
                if (state.selectedPoi != null) {
                    scaffoldState.bottomSheetState.expand()
                } else {
                    scaffoldState.bottomSheetState.partialExpand()
                }
            }

            // Clear selection when user drags sheet down
            val sheetValue = scaffoldState.bottomSheetState.currentValue
            LaunchedEffect(sheetValue) {
                if (sheetValue == SheetValue.PartiallyExpanded && state.selectedPoi != null) {
                    viewModel.selectPoi(null)
                }
            }

            Box(Modifier.fillMaxSize()) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 88.dp,
                    sheetContent = {
                        if (state.selectedPoi != null) {
                            POIDetailCard(
                                poi = state.selectedPoi,
                                onSaveClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Bookmarks coming soon")
                                    }
                                },
                                onShareClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Sharing coming soon")
                                    }
                                },
                                onNavigateClick = {
                                    val lat = state.selectedPoi.latitude
                                    val lon = state.selectedPoi.longitude
                                    if (lat != null && lon != null) {
                                        onNavigateToMaps(lat, lon, state.selectedPoi.name)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Location not available for this place")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical = MaterialTheme.spacing.sm,
                                    ),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical = MaterialTheme.spacing.sm,
                                    ),
                            ) {
                                Text(
                                    text = state.areaName,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                                Text(
                                    text = if (state.pois.isNotEmpty()) {
                                        "${state.pois.size} places to explore"
                                    } else {
                                        "Explore this area"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    MapComposable(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        latitude = state.latitude,
                        longitude = state.longitude,
                        zoomLevel = 14.0,
                        pois = state.pois,
                        onPoiSelected = { poi -> viewModel.selectPoi(poi) },
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
