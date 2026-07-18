package com.example.ambullrc

import com.example.ambullrc.model.Esp32Connection
import com.example.ambullrc.model.Esp32ConnectionException
import kotlinx.coroutines.CompletableDeferred

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

    /** Completed by a test to simulate the established link dropping. */
    private var disconnectSignal = CompletableDeferred<Unit>()

    override suspend fun connect() {
        connectCount++
        if (connectDelayMillis > 0L) {
            kotlinx.coroutines.delay(connectDelayMillis)
        }
        connectError?.let { throw it }
        // Success: arm a fresh drop signal for this connection.
        disconnectSignal = CompletableDeferred()
    }

    override suspend fun awaitDisconnect() {
        disconnectSignal.await()
    }

    override fun disconnect() {
        disconnectCount++
        if (!disconnectSignal.isCompleted) {
            disconnectSignal.complete(Unit)
        }
    }

    /** Simulate the ESP32 link dropping mid-session. */
    fun simulateDrop() {
        if (!disconnectSignal.isCompleted) {
            disconnectSignal.complete(Unit)
        }
    }
}
