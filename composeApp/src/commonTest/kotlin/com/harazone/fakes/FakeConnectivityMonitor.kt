package com.harazone.fakes

import com.harazone.domain.model.ConnectivityState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeConnectivityMonitor(
    initialState: ConnectivityState = ConnectivityState.Online
) {
    private val _state = MutableStateFlow(initialState)

    fun observe(): Flow<ConnectivityState> = _state.asStateFlow()

    fun setState(state: ConnectivityState) {
        _state.value = state
    }
}
