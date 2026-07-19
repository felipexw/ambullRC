package com.example.ambullrc.data

import java.util.UUID

/**
 * Fixed configuration identifying the single target ESP32. Change [DEVICE_NAME] to match your
 * ESP32's advertised Bluetooth name; the device must already be paired (bonded) with the phone.
 */
object Esp32Config {
    /** The bonded ESP32's advertised Bluetooth name to match against the phone's bonded devices. */
    const val DEVICE_NAME: String = "ambullrc-esp32"

    /** Serial Port Profile (SPP) UUID — the standard RFCOMM service used by ESP32 SerialBT. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
}
