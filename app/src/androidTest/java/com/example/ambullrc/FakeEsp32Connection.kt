package com.example.ambullrc

import com.example.ambullrc.model.Esp32Connection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal test double for [Esp32Connection], scoped to instrumented UI tests (the `src/test` fake
 * lives in a separate Gradle source set and isn't visible here). Starts connected so a
 * `ControlScreen` tap can be asserted to send its command without an async connect step.
 */
class FakeEsp32Connection : Esp32Connection {
    var isConnected = true
    val sentCommands = mutableListOf<String>()
    private val disconnectSignal = CompletableDeferred<Unit>()

    /** The simulated ESP32's confirmed lights state; a test pushes a value directly
     *  (`lightState.value = true`) to simulate a recognized inbound line. */
    override val lightState = MutableStateFlow<Boolean?>(null)

    override suspend fun connect() {
        isConnected = true
        lightState.value = null
    }

    override suspend fun awaitDisconnect() {
        disconnectSignal.await()
    }

    override fun disconnect() {
        isConnected = false
        if (!disconnectSignal.isCompleted) disconnectSignal.complete(Unit)
    }

    override suspend fun send(message: String): Boolean {
        if (!isConnected) return false
        sentCommands.add(message)
        return true
    }
}
