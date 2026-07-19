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

/** How often each held direction is resent while its button stays pressed. */
private const val REPEAT_INTERVAL_MS = 100L

/**
 * Holds the press-handling logic for the control screen. The View forwards each button's press
 * and release here. There is no "stop" command: the ESP32 is expected to stop the motor itself
 * whenever a direction's repeating stream goes quiet (button released, app backgrounded,
 * connection lost), so simply not sending is what makes it stop.
 *
 * More than one direction can be held at once — e.g. UP+LEFT for a diagonal turn — and each gets
 * its own independent repeat. The one thing not permitted is a direction held together with its
 * opposite (UP+DOWN, or LEFT+RIGHT): the car cannot drive forward and backward, or left and
 * right, at the same time. Whichever of the pair is pressed first simply wins: a press on a
 * direction is entirely ignored — not logged, never sent — while its opposite is still held.
 * Releasing the winner frees the axis up for a fresh press on either direction.
 *
 * On press, [direction] is logged once through [logger]; while it is actively sending, its
 * command is resent over [connection] every [REPEAT_INTERVAL_MS] on [ioDispatcher] — a send
 * while disconnected is silently dropped by the seam (never throws back here). Each send outcome
 * is also recorded in [debugLog] so it is visible on-screen (feature 004).
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
    private val pressedDirections = mutableSetOf<Direction>()
    private val repeatJobs = mutableMapOf<Direction, Job>()

    /** Starts sending [direction]'s command, unless its opposite is already held. */
    fun onDirectionPressed(direction: Direction) {
        if (direction.opposite() in pressedDirections) return
        if (!pressedDirections.add(direction)) return
        logger.log(direction)
        repeatJobs[direction] = startRepeating(direction)
    }

    /** Stops the repeating send started by [onDirectionPressed], if [direction] is held. */
    fun onDirectionReleased(direction: Direction) {
        if (!pressedDirections.remove(direction)) return
        repeatJobs.remove(direction)?.cancel()
    }

    private fun startRepeating(direction: Direction): Job = viewModelScope.launch {
        while (isActive) {
            val sent = withContext(ioDispatcher) { connection.send("${direction.name}\n") }
            if (sent) {
                debugLog.add(LogCategory.SENT, LogLevel.INFO, "${direction.name} -> sent")
            } else {
                debugLog.add(LogCategory.SENT, LogLevel.WARN, "${direction.name} -> dropped (not connected)")
            }
            delay(REPEAT_INTERVAL_MS)
        }
    }

    private fun Direction.opposite(): Direction = when (this) {
        Direction.UP -> Direction.DOWN
        Direction.DOWN -> Direction.UP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
    }
}
