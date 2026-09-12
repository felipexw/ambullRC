package com.example.ambullrc

import com.example.ambullrc.model.Esp32Connection
import com.example.ambullrc.model.Esp32ConnectionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test double for [Esp32Connection]. Stands in for the Android Bluetooth API so the
 * ConnectionViewModel's state machine can be tested deterministically with no hardware.
 *
 * @param connectError if non-null, [connect] throws it; otherwise [connect] succeeds.
 * @param connectDelayMillis optional delay before [connect] completes, to exercise timeouts.
 */
class FakeEsp32Connection(
    var connectError: Esp32ConnectionException? = null,
    var connectDelayMillis: Long = 0L
) : Esp32Connection {

    var connectCount = 0
        private set
    var disconnectCount = 0
        private set

    /** True after a successful [connect], false after [disconnect]/[simulateDrop]. */
    var isConnected = false
        private set

    /** Whether [send] should report success while connected. */
    var sendShouldSucceed = true

    /** Every message accepted by [send], in order. */
    val sentCommands = mutableListOf<String>()

    /** Completed by a test to simulate the established link dropping. */
    private var disconnectSignal = CompletableDeferred<Unit>()

    /** The simulated ESP32's confirmed lights state. A test pushes a value directly (e.g.
     *  `lightState.value = true`) to simulate a recognized inbound line; reset to null on
     *  [connect], matching the real seam's behavior. */
    override val lightState = MutableStateFlow<Boolean?>(null)

    override suspend fun connect() {
        connectCount++
        if (connectDelayMillis > 0L) {
            kotlinx.coroutines.delay(connectDelayMillis)
        }
        connectError?.let { throw it }
        // Success: arm a fresh drop signal for this connection.
        disconnectSignal = CompletableDeferred()
        isConnected = true
        lightState.value = null
    }

    override suspend fun awaitDisconnect() {
        disconnectSignal.await()
    }

    override fun disconnect() {
        disconnectCount++
        isConnected = false
        if (!disconnectSignal.isCompleted) {
            disconnectSignal.complete(Unit)
        }
    }

    override suspend fun send(message: String): Boolean {
        if (!isConnected || !sendShouldSucceed) return false
        sentCommands.add(message)
        return true
    }

    /** Simulate the ESP32 link dropping mid-session. */
    fun simulateDrop() {
        isConnected = false
        if (!disconnectSignal.isCompleted) {
            disconnectSignal.complete(Unit)
        }
    }
}
