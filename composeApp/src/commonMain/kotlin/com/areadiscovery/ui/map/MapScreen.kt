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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areadiscovery.ui.summary.ContentNoteBanner
import com.areadiscovery.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    onPoiCountChanged: (Int) -> Unit = {},
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

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 88.dp,
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                    ) {
                        Text(
                            text = state.areaName,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        Text(
                            text = "Explore this area",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                )
            }
        }
    }
}
