package com.example.ambullrc

import com.example.ambullrc.model.Direction
import com.example.ambullrc.viewmodel.ControlViewModel
import com.example.ambullrc.viewmodel.DirectionLogger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [ControlViewModel]. Uses a fake [DirectionLogger] to assert that each tap is
 * recorded as exactly one matching direction (FR-004), that repeated taps each record in order
 * (FR-005), and that a single tap records exactly one direction with no cross-firing (FR-006).
 */
class ControlViewModelTest {

    /** Fake logger that records the directions it receives, in order. */
    private class RecordingLogger : DirectionLogger {
        val logged = mutableListOf<Direction>()
        override fun log(direction: Direction) {
            logged.add(direction)
        }
    }

    @Test
    fun eachDirectionTapLogsThatSameDirection() {
        for (direction in Direction.entries) {
            val logger = RecordingLogger()
            val viewModel = ControlViewModel(logger)

            viewModel.onDirectionTapped(direction)

            assertEquals(listOf(direction), logger.logged)
        }
    }

    @Test
    fun singleTapLogsExactlyOneRecord() {
        val logger = RecordingLogger()
        val viewModel = ControlViewModel(logger)

        viewModel.onDirectionTapped(Direction.LEFT)

        assertEquals(1, logger.logged.size)
        assertEquals(Direction.LEFT, logger.logged.single())
    }

    @Test
    fun repeatedTapsEachProduceARecordInOrder() {
        val logger = RecordingLogger()
        val viewModel = ControlViewModel(logger)

        viewModel.onDirectionTapped(Direction.UP)
        viewModel.onDirectionTapped(Direction.UP)
        viewModel.onDirectionTapped(Direction.DOWN)
        viewModel.onDirectionTapped(Direction.UP)

        assertEquals(
            listOf(Direction.UP, Direction.UP, Direction.DOWN, Direction.UP),
            logger.logged
        )
    }

    @Test
    fun tappingOneDirectionNeverLogsAnother() {
        val logger = RecordingLogger()
        val viewModel = ControlViewModel(logger)

        viewModel.onDirectionTapped(Direction.RIGHT)

        assertEquals(listOf(Direction.RIGHT), logger.logged)
        assertEquals(false, logger.logged.contains(Direction.LEFT))
    }
}
