package com.example.ambullrc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
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

    /** A node's on-screen bounding box in dp. Unlike `getUnclippedBoundsInRoot`, this accounts
     *  for the D-pad's 45-degree rotation, so it reflects where the button actually appears. */
    private fun visualBounds(tag: String): DpRect = with(composeRule.density) {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        DpRect(bounds.left.toDp(), bounds.top.toDp(), bounds.right.toDp(), bounds.bottom.toDp())
    }

    /** Same as [setContentWith] but hands back the connection/viewModel so a test can push a
     *  confirmed lights state onto the fake and assert against the resulting ViewModel state. */
    private fun setContentWithLights(
        connected: Boolean = true
    ): Pair<FakeEsp32Connection, ControlViewModel> {
        val connection = FakeEsp32Connection()
        val viewModel = ControlViewModel(connection, RecordingLogger())
        composeRule.setContent {
            ControlScreen(viewModel = viewModel, connected = connected)
        }
        return connection to viewModel
    }

    /** Same as [setContentWithLights] but for the horn button (feature 008): hands back the
     *  connection/viewModel so a test can assert on sent commands and horn state. */
    private fun setContentWithHorn(
        connected: Boolean = true
    ): Pair<FakeEsp32Connection, ControlViewModel> {
        val connection = FakeEsp32Connection()
        val viewModel = ControlViewModel(connection, RecordingLogger())
        composeRule.setContent {
            ControlScreen(viewModel = viewModel, connected = connected)
        }
        return connection to viewModel
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
        val screenBounds = visualBounds("control_screen")
        for (tag in tags) {
            val buttonBounds = visualBounds(tag)
            assertTrue(buttonBounds.left >= screenBounds.left)
            assertTrue(buttonBounds.top >= screenBounds.top)
            assertTrue(buttonBounds.right <= screenBounds.right)
            assertTrue(buttonBounds.bottom <= screenBounds.bottom)
        }
    }

    @Test
    fun directionButtonsMeetAroundATinyCenter() {
        setContentWith(RecordingLogger(), connected = true)
        val up = visualBounds("btn_up")
        val down = visualBounds("btn_down")
        val left = visualBounds("btn_left")
        val right = visualBounds("btn_right")

        // Opposite arrows' tips almost meet: the empty center between them is only a few dp,
        // not a whole button-sized cell.
        val verticalGap = down.top - up.bottom
        val horizontalGap = right.left - left.right
        assertTrue(verticalGap >= 0.dp && verticalGap < 16.dp)
        assertTrue(horizontalGap >= 0.dp && horizontalGap < 16.dp)
    }

    // --- Feature 007: lights button — position, confirmed-state icon, enablement ---
    // The app never queries the ESP32 for lights state; the button enables purely on
    // `connected`, same as the D-pad, and its icon only moves when the ESP32 reports state
    // unprompted (simulated here by pushing directly onto the fake's lightState).

    @Test
    fun lightsButtonIsDisplayedBetweenLeftAndDown() {
        setContentWith(RecordingLogger(), connected = true)
        composeRule.onNodeWithTag("btn_lights").assertIsDisplayed()

        val leftBounds = visualBounds("btn_left")
        val downBounds = visualBounds("btn_down")
        val lightsBounds = visualBounds("btn_lights")

        // Bottom-left corner cell: directly below Left, directly left of Down.
        assertTrue(lightsBounds.left <= leftBounds.left + 1.dp)
        assertTrue(lightsBounds.top >= leftBounds.bottom - 1.dp)
        assertTrue(lightsBounds.top >= downBounds.top - 1.dp)
        assertTrue(lightsBounds.right <= downBounds.left + 1.dp)
    }

    @Test
    fun lightsButtonIsDisabledWhileNotConnected() {
        setContentWithLights(connected = false)
        composeRule.onNodeWithTag("btn_lights").assertIsNotEnabled()
    }

    @Test
    fun lightsButtonIsEnabledAssoonAsConnectedWithNoQueryNeeded() {
        setContentWithLights(connected = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_lights").assertIsEnabled()
    }

    @Test
    fun lightsButtonIconSwapsWhenEsp32ReportsAState() {
        val (connection, _) = setContentWithLights(connected = true)
        composeRule.waitForIdle()

        val beforeReport = composeRule.onNodeWithTag("btn_lights")
            .captureToImage().pixelSignature()

        connection.lightState.value = true
        composeRule.waitForIdle()

        val afterReport = composeRule.onNodeWithTag("btn_lights")
            .captureToImage().pixelSignature()

        // The icon swap (outline -> filled bulb) alters the rendered pixels.
        assertNotEquals(beforeReport, afterReport)
    }

    @Test
    fun tappingLightsWhileDisabledSendsNothing() {
        val (connection, _) = setContentWithLights(connected = false)
        composeRule.onNodeWithTag("btn_lights").performTouchInput { down(center); up() }
        composeRule.waitForIdle()
        assertTrue(connection.sentCommands.none { it.startsWith("LIGHTS_") })
    }

    // --- Feature 008: horn button — position, tap-triggers-then-cools-down-on-a-timer, and
    // three visually/behaviorally distinct states (Unavailable/Ready/Sounding). No ESP32 response
    // is ever involved: the button re-enables itself purely on ControlViewModel's local timer.

    @Test
    fun hornButtonIsDisplayedBetweenRightAndDown() {
        setContentWith(RecordingLogger(), connected = true)
        composeRule.onNodeWithTag("btn_horn").assertIsDisplayed()

        val rightBounds = visualBounds("btn_right")
        val downBounds = visualBounds("btn_down")
        val hornBounds = visualBounds("btn_horn")

        // Bottom-right corner cell: directly below Right, directly right of Down.
        assertTrue(hornBounds.right >= rightBounds.right - 1.dp)
        assertTrue(hornBounds.top >= rightBounds.bottom - 1.dp)
        assertTrue(hornBounds.top >= downBounds.top - 1.dp)
        assertTrue(hornBounds.left >= downBounds.right - 1.dp)
    }

    @Test
    fun hornButtonIsDisabledWhileNotConnected() {
        setContentWithHorn(connected = false)
        composeRule.onNodeWithTag("btn_horn").assertIsNotEnabled()
    }

    @Test
    fun tappingHornWhileDisabledSendsNothing() {
        val (connection, _) = setContentWithHorn(connected = false)
        composeRule.onNodeWithTag("btn_horn").performTouchInput { down(center); up() }
        composeRule.waitForIdle()
        assertTrue(connection.sentCommands.none { it == "HORN\n" })
    }

    @Test
    fun tappingHornSendsTriggerAndReEnablesOnItsOwnAfterTheCooldown() {
        val (connection, _) = setContentWithHorn(connected = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_horn").assertIsEnabled()

        composeRule.onNodeWithTag("btn_horn").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        assertEquals(listOf("HORN\n"), connection.sentCommands)
        composeRule.onNodeWithTag("btn_horn").assertIsNotEnabled()

        // Re-enables purely on ControlViewModel's fixed local timer — no ESP32 response involved.
        composeRule.waitUntil(timeoutMillis = 3_000) {
            try {
                composeRule.onNodeWithTag("btn_horn").assertIsEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun hornButtonLooksDistinctInAllThreeStates() {
        val connection = FakeEsp32Connection()
        val viewModel = ControlViewModel(connection, RecordingLogger())
        var connected by mutableStateOf(false)
        composeRule.setContent {
            ControlScreen(viewModel = viewModel, connected = connected)
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("btn_horn")

        // Unavailable: disconnected, dimmed.
        node.assertIsNotEnabled()
        val unavailable = node.captureToImage().pixelSignature()

        connected = true
        composeRule.waitForIdle()

        // Ready: connected, idle.
        node.assertIsEnabled()
        val ready = node.captureToImage().pixelSignature()
        assertNotEquals(unavailable, ready)

        // Sounding: connected, just triggered.
        node.performTouchInput { down(center); up() }
        composeRule.waitForIdle()
        node.assertIsNotEnabled()
        val sounding = node.captureToImage().pixelSignature()
        assertNotEquals(ready, sounding)
        assertNotEquals(unavailable, sounding)
    }
}
