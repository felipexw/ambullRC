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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var outputStream: OutputStream? = null

    private val adapter: BluetoothAdapter?
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _lightState = MutableStateFlow<Boolean?>(null)
    override val lightState: StateFlow<Boolean?> = _lightState.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        _lightState.value = null
        val adapter = adapter ?: throw BluetoothDisabledException()
        if (!adapter.isEnabled) throw BluetoothDisabledException()

        val device = findBondedEsp32(adapter) ?: throw DeviceUnavailableException()

        try {
            val newSocket = device.createRfcommSocketToServiceRecord(Esp32Config.SPP_UUID)
            // Discovery is not running (we never scan), so no cancelDiscovery is needed.
            newSocket.connect()
            socket = newSocket
            inputStream = newSocket.inputStream
            outputStream = newSocket.outputStream
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
        try {
            // Block until the link drops. This is the connection's only stream reader, so lights
            // state confirmations (feature 007) ride along on the same loop that already detects
            // disconnects — a recognized line updates _lightState; anything else is discarded, same
            // as when this loop only drained raw bytes.
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                when (line) {
                    "LIGHT_ON" -> _lightState.value = true
                    "LIGHT_OFF" -> _lightState.value = false
                }
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
            outputStream?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        inputStream = null
        outputStream = null
        socket = null
    }

    override suspend fun send(message: String): Boolean {
        val stream = outputStream ?: return false
        return try {
            stream.write(message.toByteArray(Charsets.UTF_8))
            stream.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun findBondedEsp32(adapter: BluetoothAdapter): BluetoothDevice? =
        adapter.bondedDevices?.firstOrNull { it.name == Esp32Config.DEVICE_NAME }
}
