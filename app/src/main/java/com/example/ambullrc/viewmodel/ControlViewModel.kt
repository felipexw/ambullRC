package com.example.ambullrc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambullrc.model.Direction
import com.example.ambullrc.model.Esp32Connection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often each held direction is resent while its button stays pressed. */
private const val REPEAT_INTERVAL_MS = 100L

/** How long the horn control stays disabled after a successfully sent trigger (feature 008). */
private const val HORN_COOLDOWN_MS = 1500L

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
 *
 * Also owns the lights toggle (feature 007): unlike a direction, a tap ([onLightsTapped]) is a
 * plain toggle, not press/release. The app never polls or queries the ESP32 for lights state — the
 * only lights-related bytes it ever sends are the toggle commands themselves. [lightsOn] is
 * confirmed-only: it only changes when [connection]'s [Esp32Connection.lightState] reports a value
 * (whatever the ESP32 chooses to send, unprompted), never as a direct effect of the tap itself.
 * [lightsEnabled] simply tracks [setConnected], same as the directional buttons' `connected` gate.
 *
 * Also owns the horn trigger (feature 008): a tap ([onHornTapped]) sends a single one-way `"HORN\n"`
 * command — the ESP32 owns the actual sound entirely, and the app never reads anything back about
 * it. [hornPlaying] is a purely local timer: it flips true the instant a trigger is successfully
 * sent and flips back to false [hornCooldownMillis] later, with no dependency on any ESP32
 * response. [hornAvailable] mirrors [setConnected] combined with [hornPlaying], so a tap is a
 * no-op while disconnected or still within the cooldown window.
 *
 * @param hornCooldownMillis how long [hornPlaying] stays true after a successful trigger
 *   (injected for test determinism; see [HORN_COOLDOWN_MS]).
 */
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hornCooldownMillis: Long = HORN_COOLDOWN_MS
) : ViewModel() {
    private val pressedDirections = mutableSetOf<Direction>()
    private val repeatJobs = mutableMapOf<Direction, Job>()

    private val _lightsOn = MutableStateFlow(false)

    /** The ESP32's last-confirmed lights state; false ("off") until the first confirmation. Only
     *  ever changes in response to a confirmed report from [connection]'s [Esp32Connection.lightState]
     *  — never as a direct effect of [onLightsTapped]. */
    val lightsOn: StateFlow<Boolean> = _lightsOn.asStateFlow()

    private val _lightsEnabled = MutableStateFlow(false)

    /** Whether the lights control currently accepts taps: mirrors [setConnected], same as the
     *  directional buttons' `connected` gate — the app never queries the ESP32 to decide this. */
    val lightsEnabled: StateFlow<Boolean> = _lightsEnabled.asStateFlow()

    private var connected = false
    private var hornResetJob: Job? = null

    private val _hornPlaying = MutableStateFlow(false)

    /** True for [hornCooldownMillis] after a successfully sent `"HORN\n"` trigger; false
     *  otherwise. Driven entirely by a local timer — never by any signal from the ESP32. */
    val hornPlaying: StateFlow<Boolean> = _hornPlaying.asStateFlow()

    private val _hornAvailable = MutableStateFlow(false)

    /** Whether the horn control currently accepts taps: true only while connected AND
     *  [hornPlaying] is false. */
    val hornAvailable: StateFlow<Boolean> = _hornAvailable.asStateFlow()

    init {
        // Long-lived for the ViewModel's lifetime: whenever the ESP32 reports its lights state
        // (unprompted — the app never asks), the icon picks it up. Never gates lightsEnabled.
        viewModelScope.launch {
            connection.lightState.collect { state ->
                if (state != null) {
                    _lightsOn.value = state
                    debugLog.add(
                        LogCategory.SENT,
                        LogLevel.INFO,
                        "ESP32 reported lights ${if (state) "ON" else "OFF"}"
                    )
                }
            }
        }
    }

    /** Called by the Activity whenever the connected/disconnected boolean it already computes for
     *  ControlScreen changes. Simply gates whether taps are accepted — the app sends no signal of
     *  its own when this changes, and [lightsOn] is left untouched either way. Also gates the horn:
     *  going disconnected immediately cancels any pending cooldown and resets [hornPlaying] to
     *  false, so a later reconnect always starts fully available rather than waiting out a stale
     *  timer from before the drop. */
    fun setConnected(connected: Boolean) {
        _lightsEnabled.value = connected
        this.connected = connected
        if (!connected) {
            hornResetJob?.cancel()
            _hornPlaying.value = false
        }
        recomputeHornAvailable()
    }

    /** Sends exactly one toggle command — the opposite of [lightsOn]'s current value — unless the
     *  control is currently disabled. This is the only lights-related signal the app ever sends;
     *  it never changes [lightsOn] itself, only whatever the ESP32 reports back on its own. */
    fun onLightsTapped() {
        if (!_lightsEnabled.value) return
        val command = if (_lightsOn.value) "LIGHTS_OFF\n" else "LIGHTS_ON\n"
        // Debug aid: pair the outgoing command with the ESP32's last-confirmed state (from
        // connection.lightState, not just _lightsOn) so a mismatch between what was sent and what
        // the ESP32 last actually reported is visible in the on-screen log while testing hardware.
        val lastKnown = connection.lightState.value?.let { if (it) "ON" else "OFF" } ?: "unconfirmed"
        viewModelScope.launch {
            val sent = withContext(ioDispatcher) { connection.send(command) }
            if (sent) {
                debugLog.add(
                    LogCategory.SENT,
                    LogLevel.INFO,
                    "${command.trim()} -> sent (ESP32 last reported: $lastKnown)"
                )
            } else {
                debugLog.add(
                    LogCategory.SENT,
                    LogLevel.WARN,
                    "${command.trim()} -> dropped (not connected) (ESP32 last reported: $lastKnown)"
                )
            }
        }
    }

    /** Sends exactly one `"HORN\n"` trigger over [connection], unless [hornAvailable] is false
     *  (not connected, or still within the cooldown window). On a successful send, starts (or
     *  restarts) the [hornCooldownMillis] timer that flips [hornPlaying] back to false — no ESP32
     *  response of any kind is awaited or parsed. */
    fun onHornTapped() {
        if (!_hornAvailable.value) return
        viewModelScope.launch {
            val sent = withContext(ioDispatcher) { connection.send("HORN\n") }
            if (sent) {
                debugLog.add(LogCategory.SENT, LogLevel.INFO, "HORN -> sent")
                _hornPlaying.value = true
                recomputeHornAvailable()
                hornResetJob?.cancel()
                hornResetJob = viewModelScope.launch {
                    delay(hornCooldownMillis)
                    _hornPlaying.value = false
                    recomputeHornAvailable()
                }
            } else {
                debugLog.add(LogCategory.SENT, LogLevel.WARN, "HORN -> dropped (not connected)")
            }
        }
    }

    private fun recomputeHornAvailable() {
        _hornAvailable.value = connected && !_hornPlaying.value
    }

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
