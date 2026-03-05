package com.areadiscovery.util

import com.areadiscovery.domain.model.ConnectivityState
import kotlinx.coroutines.flow.Flow

expect class ConnectivityMonitor {
    fun observe(): Flow<ConnectivityState>
}
