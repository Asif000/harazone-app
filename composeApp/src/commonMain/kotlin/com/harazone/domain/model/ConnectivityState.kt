package com.harazone.domain.model

sealed class ConnectivityState {
    data object Online : ConnectivityState()
    data object Offline : ConnectivityState()
}
