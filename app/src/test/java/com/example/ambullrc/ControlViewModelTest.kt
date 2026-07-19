package com.example.ambullrc

import com.example.ambullrc.model.Direction
import com.example.ambullrc.viewmodel.ControlViewModel
import com.example.ambullrc.viewmodel.DirectionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [ControlViewModel]. Uses a fake [DirectionLogger] and [FakeEsp32Connection]
 * to assert that each tap logs the matching direction (feature 001 regression) and sends the
 * matching command (FR-001..004), and that taps made while disconnected are silently dropped
 * (FR-005/FR-006).
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

    private fun newViewModel(connection: FakeEsp32Connection, logger: DirectionLogger) =
        ControlViewModel(connection, logger, ioDispatcher = dispatcher)

    // --- US1: tapping while connected sends the matching command ---

    @Test
    fun eachDirectionTapSendsThatSameDirectionsCommand() = runTest(dispatcher) {
        for (direction in Direction.entries) {
            val connection = FakeEsp32Connection().apply { connect() }
            val logger = RecordingLogger()
            val viewModel = newViewModel(connection, logger)

            viewModel.onDirectionTapped(direction)
            advanceUntilIdle()

            assertEquals(listOf(direction), logger.logged)
            assertEquals(listOf("${direction.name}\n"), connection.sentCommands)
        }
    }

    @Test
    fun singleTapSendsExactlyOneMessage() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionTapped(Direction.LEFT)
        advanceUntilIdle()

        assertEquals(1, connection.sentCommands.size)
        assertEquals("LEFT\n", connection.sentCommands.single())
    }

    @Test
    fun repeatedTapsEachSendAMessageInOrder() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionTapped(Direction.UP)
        viewModel.onDirectionTapped(Direction.UP)
        viewModel.onDirectionTapped(Direction.DOWN)
        viewModel.onDirectionTapped(Direction.UP)
        advanceUntilIdle()

        assertEquals(
            listOf(Direction.UP, Direction.UP, Direction.DOWN, Direction.UP),
            logger.logged
        )
        assertEquals(
            listOf("UP\n", "UP\n", "DOWN\n", "UP\n"),
            connection.sentCommands
        )
    }

    @Test
    fun tappingOneDirectionNeverSendsAnother() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionTapped(Direction.RIGHT)
        advanceUntilIdle()

        assertEquals(listOf("RIGHT\n"), connection.sentCommands)
        assertTrue(connection.sentCommands.none { it.startsWith("LEFT") })
    }

    // --- US2: tapping while not connected is a safe no-op ---

    @Test
    fun tapWhileNotConnectedSendsNothingAndDoesNotThrow() = runTest(dispatcher) {
        val connection = FakeEsp32Connection() // connect() never called
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionTapped(Direction.UP)
        advanceUntilIdle()

        assertTrue(connection.sentCommands.isEmpty())
        // Logging still happens; only the send is dropped.
        assertEquals(listOf(Direction.UP), logger.logged)
    }

    @Test
    fun tapWhileDisconnectedIsNotReplayedAfterReconnect() = runTest(dispatcher) {
        val connection = FakeEsp32Connection().apply { connect() }
        val logger = RecordingLogger()
        val viewModel = newViewModel(connection, logger)

        viewModel.onDirectionTapped(Direction.UP)
        advanceUntilIdle()

        connection.disconnect()
        viewModel.onDirectionTapped(Direction.DOWN)
        advanceUntilIdle()

        connection.connect()
        viewModel.onDirectionTapped(Direction.LEFT)
        advanceUntilIdle()

        // The DOWN tap made while disconnected is never sent, before or after reconnecting.
        assertEquals(listOf("UP\n", "LEFT\n"), connection.sentCommands)
    }
}
