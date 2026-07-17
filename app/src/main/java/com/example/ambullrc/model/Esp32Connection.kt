package com.example.ambullrc.model

/**
 * Seam over the Bluetooth link to the ESP32. Keeping this an interface lets the connection-handling
 * logic (in ConnectionViewModel) be exercised against a fake, with no Android Bluetooth classes and
 * no real hardware. It is also the attach point the future command-sending feature will extend.
 */
interface Esp32Connection {
    /**
     * Suspends until a live link to the ESP32 exists. Throws an [Esp32ConnectionException] subtype
     * on failure. Returns normally ONLY when the link is up.
     */
    suspend fun connect()

    /**
     * Suspends while the link is up; resumes when it drops (EOF / IO error). Behavior is undefined
     * if called before a successful [connect].
     */
    suspend fun awaitDisconnect()

    /** Closes the link and releases resources. Idempotent; safe to call in any state. */
    fun disconnect()
}

/** Failure raised by [Esp32Connection.connect]. */
sealed class Esp32ConnectionException(message: String) : Exception(message)

/** The phone's Bluetooth adapter is missing or disabled. */
class BluetoothDisabledException : Esp32ConnectionException("Bluetooth is disabled")

/** The target ESP32 is not bonded, not found, or could not be reached. */
class DeviceUnavailableException : Esp32ConnectionException("ESP32 not available")

/** Any other link/IO failure while establishing the connection. */
class LinkException(cause: String) : Esp32ConnectionException(cause)
