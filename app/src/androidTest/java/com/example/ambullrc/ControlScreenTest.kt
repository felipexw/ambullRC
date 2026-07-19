package com.example.ambullrc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.example.ambullrc.model.Direction
import com.example.ambullrc.ui.ControlScreen
import com.example.ambullrc.viewmodel.ControlViewModel
import com.example.ambullrc.viewmodel.DirectionLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI test for the control screen. Verifies the four directional buttons are present
 * and tappable (US1), and that pressing each button drives the ViewModel's logger with the
 * correct direction and no cross-firing (US2). Presses are driven via raw touch input (down/up)
 * rather than performClick, since the button now streams commands for as long as it is held
 * rather than firing once on a completed click.
 */
class ControlScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Fake logger that records the directions it receives, in order. */
    private class RecordingLogger : DirectionLogger {
        val logged = mutableListOf<Direction>()
        override fun log(direction: Direction) {
            logged.add(direction)
        }
    }

    private val tags = listOf("btn_up", "btn_down", "btn_left", "btn_right")

    private fun setContentWith(logger: DirectionLogger) {
        composeRule.setContent {
            ControlScreen(viewModel = ControlViewModel(FakeEsp32Connection(), logger))
        }
    }

    /** Presses and immediately releases the tagged button, as a single logical tap. */
    private fun tap(tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        node.performTouchInput { down(center) }
        composeRule.waitForIdle()
        node.performTouchInput { up() }
        composeRule.waitForIdle()
    }

    /** Coarse fingerprint of a rendered node: a sampled grid of pixel colors. */
    private fun ImageBitmap.pixelSignature(): List<Int> {
        val bmp = asAndroidBitmap()
        val step = maxOf(1, minOf(bmp.width, bmp.height) / 10)
        val pixels = mutableListOf<Int>()
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                pixels.add(bmp.getPixel(x, y))
                x += step
            }
            y += step
        }
        return pixels
    }

    // --- US1: buttons present and tappable ---

    @Test
    fun allFourButtonsAreDisplayedAndClickable() {
        setContentWith(RecordingLogger())
        for (tag in tags) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
        }
    }

    // --- US2: each press logs the matching direction, no cross-firing ---

    @Test
    fun tappingUpLogsOnlyUp() {
        val logger = RecordingLogger()
        setContentWith(logger)
        tap("btn_up")
        assertEquals(listOf(Direction.UP), logger.logged)
    }

    @Test
    fun tappingDownLogsOnlyDown() {
        val logger = RecordingLogger()
        setContentWith(logger)
        tap("btn_down")
        assertEquals(listOf(Direction.DOWN), logger.logged)
    }

    @Test
    fun tappingLeftLogsOnlyLeft() {
        val logger = RecordingLogger()
        setContentWith(logger)
        tap("btn_left")
        assertEquals(listOf(Direction.LEFT), logger.logged)
    }

    @Test
    fun tappingRightLogsOnlyRight() {
        val logger = RecordingLogger()
        setContentWith(logger)
        tap("btn_right")
        assertEquals(listOf(Direction.RIGHT), logger.logged)
    }

    // --- Press feedback: the button visibly changes appearance while held ---

    @Test
    fun pressingButtonChangesItsAppearance() {
        setContentWith(RecordingLogger())
        val node = composeRule.onNodeWithTag("btn_up")

        val idle = node.captureToImage().pixelSignature()

        node.performTouchInput { down(center) }
        composeRule.waitForIdle()
        val pressed = node.captureToImage().pixelSignature()
        node.performTouchInput { up() }

        // The highlight background, tint change, and scale must alter the rendered pixels.
        assertNotEquals(idle, pressed)
    }

    @Test
    fun tappingSequenceRecordsEveryTapInOrder() {
        val logger = RecordingLogger()
        setContentWith(logger)
        tap("btn_up")
        tap("btn_up")
        tap("btn_left")
        tap("btn_right")
        assertEquals(
            listOf(Direction.UP, Direction.UP, Direction.LEFT, Direction.RIGHT),
            logger.logged
        )
    }
}
