package com.example.ambullrc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambullrc.model.BluetoothDisabledException
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.model.DeviceUnavailableException
import com.example.ambullrc.model.Esp32Connection
import com.example.ambullrc.model.Esp32ConnectionException
import com.example.ambullrc.model.FailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Owns the app-to-ESP32 connection lifecycle and exposes it as [state]. The View renders [state]
 * and forwards [connect]/[retry]/[onPermissionDenied]; all connection logic lives here, driven
 * through the [connection] seam so it is testable without Bluetooth hardware. Each state
 * transition is also recorded in [debugLog] so it is visible on-screen (feature 004).
 *
 * @param connection the Bluetooth seam (real implementation in production, fake in tests).
 * @param debugLog on-screen diagnostic log; shared with [ControlViewModel] by the Activity.
 * @param connectTimeoutMillis how long a single connect attempt may take before it is failed.
 * @param ioDispatcher dispatcher for the blocking Bluetooth I/O (injected for test determinism).
 */
class ConnectionViewModel(
    private val connection: Esp32Connection,
    private val debugLog: DebugLog = DebugLog(),
    private val connectTimeoutMillis: Long = 12_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var connectJob: Job? = null

    /** Begin (or restart) a connection attempt: Connecting → Connected, then monitor for drops. */
    fun connect() {
        if (_state.value is ConnectionState.Connecting) return
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _state.value = ConnectionState.Connecting
            debugLog.add("Connecting to ESP32…")
            try {
                withTimeout(connectTimeoutMillis) {
                    withContext(ioDispatcher) { connection.connect() }
                }
            } catch (e: TimeoutCancellationException) {
                _state.value = ConnectionState.Failed(FailureReason.DEVICE_UNAVAILABLE)
                debugLog.add("Connect failed: ${FailureReason.DEVICE_UNAVAILABLE}")
                return@launch
            } catch (e: Esp32ConnectionException) {
                val reason = e.toReason()
                _state.value = ConnectionState.Failed(reason)
                debugLog.add("Connect failed: $reason")
                return@launch
            }
            _state.value = ConnectionState.Connected
            debugLog.add("Connected")
            monitorForDrop()
        }
    }

    /** Retry after a failure — a fresh attempt equivalent to [connect]. */
    fun retry() = connect()

    /** Called by the Activity when the operator denied the Bluetooth permission. */
    fun onPermissionDenied() {
        connectJob?.cancel()
        _state.value = ConnectionState.Failed(FailureReason.PERMISSION_DENIED)
        debugLog.add("Connect failed: ${FailureReason.PERMISSION_DENIED}")
    }

    /** After a live link is established, wait for it to drop and reflect the disconnected state. */
    private suspend fun monitorForDrop() {
        try {
            withContext(ioDispatcher) { connection.awaitDisconnect() }
            _state.value = ConnectionState.Failed(FailureReason.CONNECTION_LOST)
            debugLog.add("Connection lost")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = ConnectionState.Failed(FailureReason.CONNECTION_LOST)
            debugLog.add("Connection lost")
        }
    }

    override fun onCleared() {
        connection.disconnect()
    }
}

private fun Esp32ConnectionException.toReason(): FailureReason = when (this) {
    is BluetoothDisabledException -> FailureReason.BLUETOOTH_DISABLED
    is DeviceUnavailableException -> FailureReason.DEVICE_UNAVAILABLE
    else -> FailureReason.ERROR
}
