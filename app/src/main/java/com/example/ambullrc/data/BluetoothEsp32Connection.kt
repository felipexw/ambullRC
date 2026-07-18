package com.example.ambullrc.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.ambullrc.model.BluetoothDisabledException
import com.example.ambullrc.model.DeviceUnavailableException
import com.example.ambullrc.model.Esp32Connection
import com.example.ambullrc.model.LinkException
import java.io.IOException
import java.io.InputStream

/**
 * Real [Esp32Connection] over Bluetooth Classic (RFCOMM / SPP). Connects to the single bonded
 * ESP32 named [Esp32Config.DEVICE_NAME]. Callers must hold BLUETOOTH_CONNECT before calling
 * [connect]; the Activity requests it at startup.
 *
 * All Bluetooth API calls are annotated MissingPermission because the permission gate lives in the
 * Activity/ViewModel flow, not here.
 */
class BluetoothEsp32Connection(context: Context) : Esp32Connection {

    private val appContext = context.applicationContext
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    private val adapter: BluetoothAdapter?
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        val adapter = adapter ?: throw BluetoothDisabledException()
        if (!adapter.isEnabled) throw BluetoothDisabledException()

        val device = findBondedEsp32(adapter) ?: throw DeviceUnavailableException()

        try {
            val newSocket = device.createRfcommSocketToServiceRecord(Esp32Config.SPP_UUID)
            // Discovery is not running (we never scan), so no cancelDiscovery is needed.
            newSocket.connect()
            socket = newSocket
            inputStream = newSocket.inputStream
        } catch (e: IOException) {
            disconnect()
            throw DeviceUnavailableException()
        } catch (e: SecurityException) {
            disconnect()
            throw LinkException(e.message ?: "Bluetooth permission missing")
        }
    }

    override suspend fun awaitDisconnect() {
        val stream = inputStream ?: return
        val buffer = ByteArray(64)
        try {
            // Block until the link drops. Bytes are discarded — this is liveness only, not telemetry.
            while (stream.read(buffer) != -1) {
                // discard
            }
        } catch (e: IOException) {
            // Link dropped or socket closed — treated as disconnect.
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        inputStream = null
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun findBondedEsp32(adapter: BluetoothAdapter): BluetoothDevice? =
        adapter.bondedDevices?.firstOrNull { it.name == Esp32Config.DEVICE_NAME }
}
