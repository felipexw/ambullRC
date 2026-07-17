package com.example.ambullrc.model

/**
 * The current status of the app-to-ESP32 Bluetooth link. Exactly one value is current at any time;
 * this is the single source of truth the UI renders and the tests assert on.
 */
sealed interface ConnectionState {
    /** No attempt in progress or started yet. */
    data object Idle : ConnectionState

    /** A connection attempt is underway. */
    data object Connecting : ConnectionState

    /** A live link to the ESP32 exists. Entered only after a real connect succeeds. */
    data object Connected : ConnectionState

    /** The last attempt failed, or an established link dropped. */
    data class Failed(val reason: FailureReason) : ConnectionState
}

/** Why the connection is not established. Drives the status text shown to the operator. */
enum class FailureReason {
    /** The operator denied the Bluetooth permission. */
    PERMISSION_DENIED,

    /** The phone's Bluetooth is turned off. */
    BLUETOOTH_DISABLED,

    /** The target ESP32 is not bonded/found/reachable, or the connect attempt timed out. */
    DEVICE_UNAVAILABLE,

    /** An established link dropped. */
    CONNECTION_LOST,

    /** Any other link/IO failure. */
    ERROR
}
