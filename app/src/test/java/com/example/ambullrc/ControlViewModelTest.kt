package com.example.ambullrc

import com.example.ambullrc.model.Direction
import com.example.ambullrc.viewmodel.ControlViewModel
import com.example.ambullrc.viewmodel.DebugLog
import com.example.ambullrc.viewmodel.DirectionLogger
import com.example.ambullrc.viewmodel.LogCategory
import com.example.ambullrc.viewmodel.LogEntry
import com.example.ambullrc.viewmodel.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * JVM unit tests for [ControlViewModel]. Uses a fake [DirectionLogger] and [FakeEsp32Connection]
 * to assert that pressing a direction logs it once (feature 001 regression), that it keeps
 * resending the command every 100ms while held (so the ESP32's own watchdog never sees the stream
 * go quiet), and that it stops the moment the button is released — there is no separate "stop"
 * command; not sending is what stops the motor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Fake logger that records the directions it receives, in order. */
    private class RecordingLogger : DirectionLogger {
        val logged = mutableListOf<Direction>()
        override fun log(direction: Direction) {
            logged.add(direction)
        }
    }

    private fun newViewModel(
        connection: FakeEsp32Connection,
        logger: DirectionLogger,
        debugLog: DebugLog = DebugLog()
    ) = ControlViewModel(connection, logger, debugLog, ioDispatcher = dispatcher)

    // --- US1: pressing while connected sends the matching command, repeatedly, until released ---

    @Test
    fun eachDirectionPressSendsThatSameDirectionsCommand() = runTest(dispatcher) {
        for (direction in Direction.entries) {
            val connection = FakeEsp32Connection().apply { connect() }
            val logger = RecordingLogger()
            val viewModel = newViewModel(connection, logger)

            viewModel.onDirectionPressed(direction)
            runCurrent()
            viewModel.onDirectionReleased(direction)
            advanceUntilIdle()

            assertEquals(listOf(direction), logger.logged)
            assertEquals(listOf("${direction.name}\n"), connection.sentCommands)
        }
    }

    @Test
    fun pressThenImmediateReleaseSendsExactlyOneMessage() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionPressed(Direction.LEFT)
        runCurrent()
        viewModel.onDirectionReleased(Direction.LEFT)
        advanceUntilIdle()

        assertEquals(1, connection.sentCommands.size)
        assertEquals("LEFT\n", connection.sentCommands.single())
    }

    @Test
    fun holdingAButtonResendsTheCommandEveryHundredMillis() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val viewModel = newViewModel(connection, RecordingLogger())

        viewModel.onDirectionPressed(Direction.UP)
        advanceTimeBy(350)
        runCurrent()
        viewModel.onDirectionReleased(Direction.UP)
        advanceUntilIdle()

        // Sent at t=0, 100, 200, 300 while held for 350ms.
        assertEquals(List(4) { "UP\n" }, connection.sentCommands)
    }

    @Test
    fun releasingStopsFurtherSends() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val viewModel = newViewModel(connection, RecordingLogger())

        viewModel.onDirectionPressed(Direction.UP)
        advanceTimeBy(250)
        runCurrent()
        viewModel.onDirectionReleased(Direction.UP)
        val sentAtRelease = connection.sentCommands.size

        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(sentAtRelease, connection.sentCommands.size)
    }

    @Test
    fun pressingAnotherDirectionStopsTheFirstDirectionsSends() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val viewModel = newViewModel(connection, RecordingLogger())

        viewModel.onDirectionPressed(Direction.UP)
        advanceTimeBy(50)
        runCurrent()
        viewModel.onDirectionPressed(Direction.DOWN)
        advanceTimeBy(50)
        runCurrent()
        viewModel.onDirectionReleased(Direction.DOWN)
        advanceUntilIdle()

        // UP was sent once before DOWN took over; DOWN was sent once before release. UP's repeat
        // was cancelled the moment DOWN was pressed, so it never sends a second time.
        assertEquals(listOf("UP\n", "DOWN\n"), connection.sentCommands)
    }

    // --- US2: pressing while not connected is a safe no-op ---

    @Test
    fun pressWhileNotConnectedSendsNothingAndDoesNotThrow() = runTest(dispatcher) {
        val connection = FakeEsp32Connection() // connect() never called
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionPressed(Direction.UP)
        runCurrent()
        viewModel.onDirectionReleased(Direction.UP)
        advanceUntilIdle()

        assertTrue(connection.sentCommands.isEmpty())
        // Logging still happens; only the send is dropped.
        assertEquals(listOf(Direction.UP), logger.logged)
    }

    @Test
    fun pressWhileDisconnectedIsNotReplayedAfterReconnect() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionPressed(Direction.UP)
        runCurrent()
        viewModel.onDirectionReleased(Direction.UP)
        advanceUntilIdle()

        connection.disconnect()
        viewModel.onDirectionPressed(Direction.DOWN)
        runCurrent()
        viewModel.onDirectionReleased(Direction.DOWN)
        advanceUntilIdle()

        connection.connect()
        viewModel.onDirectionPressed(Direction.LEFT)
        runCurrent()
        viewModel.onDirectionReleased(Direction.LEFT)
        advanceUntilIdle()

        // The DOWN press made while disconnected is never sent, before or after reconnecting.
        assertEquals(listOf("UP\n", "LEFT\n"), connection.sentCommands)
    }

    // --- Feature 004/005: send outcomes are recorded in the DebugLog widget, with a
    //     category/level attached to each entry (feature 005) ---

    private fun List<LogEntry>.summaries() = map { Triple(it.category, it.level, it.message) }

    @Test
    fun pressWhileConnectedAppendsSentEntryToDebugLog() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val debugLog = DebugLog()
        val viewModel = newViewModel(connection, RecordingLogger(), debugLog)

        viewModel.onDirectionPressed(Direction.UP)
        runCurrent()
        viewModel.onDirectionReleased(Direction.UP)
        advanceUntilIdle()

        assertEquals(
            listOf(Triple(LogCategory.SENT, LogLevel.INFO, "UP -> sent")),
            debugLog.entries.value.summaries()
        )
    }

    @Test
    fun pressWhileDisconnectedAppendsDroppedEntryToDebugLog() = runTest(dispatcher) {
        val connection = FakeEsp32Connection() // never connected
        val debugLog = DebugLog()
        val viewModel = newViewModel(connection, RecordingLogger(), debugLog)

        viewModel.onDirectionPressed(Direction.DOWN)
        runCurrent()
        viewModel.onDirectionReleased(Direction.DOWN)
        advanceUntilIdle()

        assertEquals(
            listOf(Triple(LogCategory.SENT, LogLevel.WARN, "DOWN -> dropped (not connected)")),
            debugLog.entries.value.summaries()
        )
    }
}
