package com.harazone.util

import com.harazone.domain.model.ConnectivityState
import kotlinx.coroutines.flow.Flow

expect class ConnectivityMonitor {
    fun observe(): Flow<ConnectivityState>
}
