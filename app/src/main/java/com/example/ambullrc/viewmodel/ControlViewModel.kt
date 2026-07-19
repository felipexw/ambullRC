package com.example.ambullrc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambullrc.model.Direction
import com.example.ambullrc.model.Esp32Connection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often the held direction is resent while a button stays pressed. */
private const val REPEAT_INTERVAL_MS = 100L

/**
 * Holds the press-handling logic for the control screen. The View forwards each button's press
 * and release here. There is no "stop" command: the ESP32 is expected to stop the motor itself
 * whenever this repeating stream goes quiet (button released, app backgrounded, connection lost),
 * so simply not sending is what makes it stop.
 *
 * On press, [direction] is logged once through [logger] and then resent over [connection] every
 * [REPEAT_INTERVAL_MS] on [ioDispatcher] until released — a send while disconnected is silently
 * dropped by the seam (never throws back here). Each send outcome is also recorded in [debugLog]
 * so it is visible on-screen (feature 004).
 *
 * @param connection the Bluetooth seam commands are sent over (real implementation in production,
 *   fake in tests).
 * @param logger destination for press records. Defaults to the Android Logcat-backed implementation.
 * @param debugLog on-screen diagnostic log; shared with [ConnectionViewModel] by the Activity.
 * @param ioDispatcher dispatcher for the blocking Bluetooth write (injected for test determinism).
 */
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private var activeDirection: Direction? = null
    private var repeatJob: Job? = null

    /** Starts (re)sending [direction]'s command every [REPEAT_INTERVAL_MS] until released. */
    fun onDirectionPressed(direction: Direction) {
        logger.log(direction)
        repeatJob?.cancel()
        activeDirection = direction
        repeatJob = viewModelScope.launch {
            while (isActive) {
                val sent = withContext(ioDispatcher) { connection.send("${direction.name}\n") }
                debugLog.add("${direction.name} -> ${if (sent) "sent" else "dropped (not connected)"}")
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    /** Stops the repeating send started by [onDirectionPressed], if [direction] is still active. */
    fun onDirectionReleased(direction: Direction) {
        if (activeDirection != direction) return
        repeatJob?.cancel()
        repeatJob = null
        activeDirection = null
    }
}
