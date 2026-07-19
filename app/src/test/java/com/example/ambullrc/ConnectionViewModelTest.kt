package com.example.ambullrc

import com.example.ambullrc.model.BluetoothDisabledException
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.model.DeviceUnavailableException
import com.example.ambullrc.model.FailureReason
import com.example.ambullrc.model.LinkException
import com.example.ambullrc.viewmodel.ConnectionViewModel
import com.example.ambullrc.viewmodel.DebugLog
import com.example.ambullrc.viewmodel.LogCategory
import com.example.ambullrc.viewmodel.LogEntry
import com.example.ambullrc.viewmodel.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [ConnectionViewModel], driven against [FakeEsp32Connection] on virtual time.
 * Covers the connect success path (US1), each failure reason, timeout, retry, and mid-session drop
 * (US3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        fake: FakeEsp32Connection,
        timeoutMillis: Long = 12_000L,
        debugLog: DebugLog = DebugLog()
    ) = ConnectionViewModel(fake, debugLog, connectTimeoutMillis = timeoutMillis, ioDispatcher = dispatcher)

    // --- US1: auto-connect success ---

    @Test
    fun startsIdle() {
        val vm = newViewModel(FakeEsp32Connection())
        assertEquals(ConnectionState.Idle, vm.state.value)
    }

    @Test
    fun connectTransitionsThroughConnectingToConnected() = runTest(dispatcher) {
        val fake = FakeEsp32Connection(connectDelayMillis = 1_000L)
        val vm = newViewModel(fake)

        vm.connect()
        runCurrent()
        assertEquals(ConnectionState.Connecting, vm.state.value)

        advanceUntilIdle()
        assertEquals(ConnectionState.Connected, vm.state.value)
        assertEquals(1, fake.connectCount)
    }

    @Test
    fun neverConnectedWhenConnectFails() = runTest(dispatcher) {
        val vm = newViewModel(FakeEsp32Connection(connectError = DeviceUnavailableException()))
        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.state.value is ConnectionState.Failed)
    }

    // --- US3: failure reason mapping ---

    @Test
    fun bluetoothDisabledMapsToBluetoothDisabledReason() = runTest(dispatcher) {
        val vm = newViewModel(FakeEsp32Connection(connectError = BluetoothDisabledException()))
        vm.connect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Failed(FailureReason.BLUETOOTH_DISABLED), vm.state.value)
    }

    @Test
    fun deviceUnavailableMapsToDeviceUnavailableReason() = runTest(dispatcher) {
        val vm = newViewModel(FakeEsp32Connection(connectError = DeviceUnavailableException()))
        vm.connect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Failed(FailureReason.DEVICE_UNAVAILABLE), vm.state.value)
    }

    @Test
    fun otherLinkErrorMapsToErrorReason() = runTest(dispatcher) {
        val vm = newViewModel(FakeEsp32Connection(connectError = LinkException("boom")))
        vm.connect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Failed(FailureReason.ERROR), vm.state.value)
    }

    @Test
    fun connectTimesOutToDeviceUnavailable() = runTest(dispatcher) {
        // Connect never completes within the timeout window.
        val fake = FakeEsp32Connection(connectDelayMillis = 60_000L)
        val vm = newViewModel(fake, timeoutMillis = 12_000L)

        vm.connect()
        advanceUntilIdle()

        assertEquals(ConnectionState.Failed(FailureReason.DEVICE_UNAVAILABLE), vm.state.value)
    }

    // --- US3: permission denied ---

    @Test
    fun onPermissionDeniedSetsPermissionDeniedAndDoesNotConnect() = runTest(dispatcher) {
        val fake = FakeEsp32Connection()
        val vm = newViewModel(fake)

        vm.onPermissionDenied()
        advanceUntilIdle()

        assertEquals(ConnectionState.Failed(FailureReason.PERMISSION_DENIED), vm.state.value)
        assertEquals(0, fake.connectCount)
    }

    // --- US3: retry ---

    @Test
    fun retryAfterFailureReachesConnected() = runTest(dispatcher) {
        val fake = FakeEsp32Connection(connectError = DeviceUnavailableException())
        val vm = newViewModel(fake)

        vm.connect()
        advanceUntilIdle()
        assertTrue(vm.state.value is ConnectionState.Failed)

        // Cause resolved: subsequent attempt succeeds.
        fake.connectError = null
        vm.retry()
        advanceUntilIdle()
        assertEquals(ConnectionState.Connected, vm.state.value)
    }

    // --- US3: mid-session drop ---

    @Test
    fun dropAfterConnectedSetsConnectionLost() = runTest(dispatcher) {
        val fake = FakeEsp32Connection()
        val vm = newViewModel(fake)

        vm.connect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Connected, vm.state.value)

        fake.simulateDrop()
        advanceUntilIdle()
        assertEquals(ConnectionState.Failed(FailureReason.CONNECTION_LOST), vm.state.value)
    }

    // --- Feature 004/005: state transitions are recorded in the DebugLog widget, with a
    //     category/level attached to each entry (feature 005) ---

    private fun List<LogEntry>.summaries() =
        map { Triple(it.category, it.level, it.message) }

    @Test
    fun connectSuccessAppendsConnectingThenConnectedToDebugLog() = runTest(dispatcher) {
        val debugLog = DebugLog()
        val vm = newViewModel(FakeEsp32Connection(), debugLog = debugLog)

        vm.connect()
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(LogCategory.CONNECTION, LogLevel.INFO, "Connecting to ESP32…"),
                Triple(LogCategory.CONNECTION, LogLevel.INFO, "Connected")
            ),
            debugLog.entries.value.summaries()
        )
    }

    @Test
    fun connectFailureAppendsConnectFailedEntryToDebugLog() = runTest(dispatcher) {
        val debugLog = DebugLog()
        val vm = newViewModel(FakeEsp32Connection(connectError = DeviceUnavailableException()), debugLog = debugLog)

        vm.connect()
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(LogCategory.CONNECTION, LogLevel.INFO, "Connecting to ESP32…"),
                Triple(LogCategory.CONNECTION, LogLevel.ERROR, "Connect failed: DEVICE_UNAVAILABLE")
            ),
            debugLog.entries.value.summaries()
        )
    }

    @Test
    fun midSessionDropAppendsConnectionLostToDebugLog() = runTest(dispatcher) {
        val fake = FakeEsp32Connection()
        val debugLog = DebugLog()
        val vm = newViewModel(fake, debugLog = debugLog)

        vm.connect()
        advanceUntilIdle()

        fake.simulateDrop()
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(LogCategory.CONNECTION, LogLevel.INFO, "Connecting to ESP32…"),
                Triple(LogCategory.CONNECTION, LogLevel.INFO, "Connected"),
                Triple(LogCategory.CONNECTION, LogLevel.ERROR, "Connection lost")
            ),
            debugLog.entries.value.summaries()
        )
    }

    @Test
    fun permissionDeniedAppendsAppCategoryErrorEntryToDebugLog() {
        val debugLog = DebugLog()
        val vm = newViewModel(FakeEsp32Connection(), debugLog = debugLog)

        vm.onPermissionDenied()

        assertEquals(
            listOf(Triple(LogCategory.APP, LogLevel.ERROR, "Connect failed: PERMISSION_DENIED")),
            debugLog.entries.value.summaries()
        )
    }
}
