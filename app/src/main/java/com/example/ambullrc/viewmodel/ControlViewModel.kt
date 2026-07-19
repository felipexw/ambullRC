package com.example.ambullrc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambullrc.model.Direction
import com.example.ambullrc.model.Esp32Connection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the tap-handling logic for the control screen. The View forwards each button tap here;
 * this class decides what to record and what to send. Each tap logs the [Direction] through the
 * injected [DirectionLogger] and sends its command over [connection] on [ioDispatcher] — a tap
 * while disconnected is silently dropped by the seam (never throws back here). The outcome is also
 * recorded in [debugLog] so it is visible on-screen (feature 004).
 *
 * @param connection the Bluetooth seam commands are sent over (real implementation in production,
 *   fake in tests).
 * @param logger destination for tap records. Defaults to the Android Logcat-backed implementation.
 * @param debugLog on-screen diagnostic log; shared with [ConnectionViewModel] by the Activity.
 * @param ioDispatcher dispatcher for the blocking Bluetooth write (injected for test determinism).
 */
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    /** Records exactly one occurrence of [direction] and sends its command to the ESP32. */
    fun onDirectionTapped(direction: Direction) {
        logger.log(direction)
        viewModelScope.launch {
            val sent = withContext(ioDispatcher) { connection.send("${direction.name}\n") }
            debugLog.add("${direction.name} -> ${if (sent) "sent" else "dropped (not connected)"}")
        }
    }
}
