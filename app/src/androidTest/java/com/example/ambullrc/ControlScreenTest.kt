package com.example.ambullrc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ambullrc.model.Direction
import com.example.ambullrc.ui.ControlScreen
import com.example.ambullrc.viewmodel.ControlViewModel
import com.example.ambullrc.viewmodel.DirectionLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI test for the control screen (feature 005 redesign). Verifies the four
 * directional buttons are present and tappable (US1/legacy), that pressing each button drives the
 * ViewModel's logger with the correct direction and no cross-firing (US2), and the new
 * connection-aware behavior: dimmed/disabled controls and hint text when `connected == false`
 * (spec.md FR-007/FR-008).
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

    private fun setContentWith(logger: DirectionLogger, connected: Boolean = true) {
        composeRule.setContent {
            ControlScreen(
                viewModel = ControlViewModel(FakeEsp32Connection(), logger),
                connected = connected
            )
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

    // --- buttons present and tappable when connected ---

    @Test
    fun allFourButtonsAreDisplayedAndClickable() {
        setContentWith(RecordingLogger(), connected = true)
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

        // The highlight background and icon tint must alter the rendered pixels.
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

    // --- US2/FR-007: disabled while not connected ---

    @Test
    fun buttonsAreEnabledWhenConnected() {
        setContentWith(RecordingLogger(), connected = true)
        for (tag in tags) {
            composeRule.onNodeWithTag(tag).assertIsEnabled()
        }
    }

    @Test
    fun buttonsAreDisabledWhenNotConnected() {
        setContentWith(RecordingLogger(), connected = false)
        for (tag in tags) {
            composeRule.onNodeWithTag(tag).assertIsNotEnabled()
        }
    }

    @Test
    fun pressingButtonWhenNotConnectedDoesNotLog() {
        val logger = RecordingLogger()
        setContentWith(logger, connected = false)
        tap("btn_up")
        assertTrue(logger.logged.isEmpty())
    }

    // --- FR-004: connected-state hint text is removed; disconnected hint is unchanged ---

    @Test
    fun hintTextPromptsToWaitWhenNotConnected() {
        setContentWith(RecordingLogger(), connected = false)
        composeRule.onNodeWithTag("dpad_hint").assertTextEquals("Waiting for connection to enable controls")
    }

    @Test
    fun noHintTextWhenConnected() {
        setContentWith(RecordingLogger(), connected = true)
        composeRule.onNodeWithTag("dpad_hint").assertDoesNotExist()
        composeRule.onNodeWithText("Hold a direction to drive").assertDoesNotExist()
    }

    // --- FR-001/FR-002/SC-001: buttons fill the available space, staying within ControlScreen ---

    @Test
    fun directionButtonsAreLargerThanOldFixedCellSize() {
        setContentWith(RecordingLogger(), connected = true)
        // The old fixed cell was 76dp; on a full-screen host these should comfortably exceed it.
        for (tag in tags) {
            composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(90.dp)
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(90.dp)
        }
    }

    @Test
    fun directionButtonsStayWithinControlScreenBounds() {
        setContentWith(RecordingLogger(), connected = true)
        val screenBounds = composeRule.onNodeWithTag("control_screen").getUnclippedBoundsInRoot()
        for (tag in tags) {
            val buttonBounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(buttonBounds.left >= screenBounds.left)
            assertTrue(buttonBounds.top >= screenBounds.top)
            assertTrue(buttonBounds.right <= screenBounds.right)
            assertTrue(buttonBounds.bottom <= screenBounds.bottom)
        }
    }
}
