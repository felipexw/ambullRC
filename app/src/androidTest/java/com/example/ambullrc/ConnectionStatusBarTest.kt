package com.example.ambullrc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.model.FailureReason
import com.example.ambullrc.ui.ConnectionStatusBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose test for [ConnectionStatusBar] (feature 005 redesign). Verifies the status
 * pill's label/colors per state (US1), that the dot only pulses while Connecting, that Retry
 * shows only for the disconnected bucket (Idle/Failed), and that Retry still invokes onRetry.
 */
class ConnectionStatusBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(state: ConnectionState, onRetry: () -> Unit = {}) {
        composeRule.setContent { ConnectionStatusBar(state = state, onRetry = onRetry) }
    }

    // --- US1: status label per state ---

    @Test
    fun idleShowsDisconnected() {
        setState(ConnectionState.Idle)
        composeRule.onNodeWithTag("status_text").assertTextEquals("Disconnected")
    }

    @Test
    fun connectingShowsConnecting() {
        setState(ConnectionState.Connecting)
        composeRule.onNodeWithTag("status_text").assertTextEquals("Connecting…")
    }

    @Test
    fun connectedShowsConnected() {
        setState(ConnectionState.Connected)
        composeRule.onNodeWithTag("status_text").assertTextEquals("Connected")
    }

    @Test
    fun failedShowsDisconnected() {
        setState(ConnectionState.Failed(FailureReason.DEVICE_UNAVAILABLE))
        composeRule.onNodeWithTag("status_text").assertTextEquals("Disconnected")
    }

    // --- US1: device name ---

    @Test
    fun connectedShowsDeviceName() {
        setState(ConnectionState.Connected)
        composeRule.onNodeWithTag("device_name").assertTextEquals("ESP32-RCCAR")
    }

    @Test
    fun notConnectedShowsNoDevicePlaceholder() {
        setState(ConnectionState.Idle)
        composeRule.onNodeWithTag("device_name").assertTextEquals("No device")
    }

    // --- Retry button visibility (Idle/Failed = disconnected bucket) ---

    @Test
    fun retryButtonShownWhenIdle() {
        setState(ConnectionState.Idle)
        composeRule.onNodeWithTag("btn_retry").assertIsDisplayed()
    }

    @Test
    fun retryButtonShownWhenFailed() {
        setState(ConnectionState.Failed(FailureReason.BLUETOOTH_DISABLED))
        composeRule.onNodeWithTag("btn_retry").assertIsDisplayed()
    }

    @Test
    fun retryButtonAbsentWhenConnecting() {
        setState(ConnectionState.Connecting)
        composeRule.onNodeWithTag("btn_retry").assertDoesNotExist()
    }

    @Test
    fun retryButtonAbsentWhenConnected() {
        setState(ConnectionState.Connected)
        composeRule.onNodeWithTag("btn_retry").assertDoesNotExist()
    }

    // --- Retry invokes callback ---

    @Test
    fun tappingRetryInvokesOnRetry() {
        var retries = 0
        setState(ConnectionState.Failed(FailureReason.CONNECTION_LOST)) { retries++ }
        composeRule.onNodeWithTag("btn_retry").performClick()
        assertEquals(1, retries)
    }

    // --- Dot only pulses while Connecting ---

    @Test
    fun dotPulsesOnlyWhileConnecting() {
        composeRule.mainClock.autoAdvance = false
        setState(ConnectionState.Connecting)

        composeRule.mainClock.advanceTimeBy(50)
        val frame0 = composeRule.onNodeWithTag("status_dot").captureToImage()

        // Advance partway through the pulse period and confirm the rendered dot actually
        // changed — i.e. the animation is really running, not just declared.
        composeRule.mainClock.advanceTimeBy(500)
        val frame1 = composeRule.onNodeWithTag("status_dot").captureToImage()

        assertNotEquals(pixelSum(frame0), pixelSum(frame1))
    }

    @Test
    fun dotDoesNotAnimateWhenConnected() {
        composeRule.mainClock.autoAdvance = false
        setState(ConnectionState.Connected)

        composeRule.mainClock.advanceTimeBy(50)
        val frame0 = composeRule.onNodeWithTag("status_dot").captureToImage()
        composeRule.mainClock.advanceTimeBy(500)
        val frame1 = composeRule.onNodeWithTag("status_dot").captureToImage()

        assertEquals(pixelSum(frame0), pixelSum(frame1))
    }

    private fun pixelSum(bitmap: ImageBitmap): Long {
        val bmp = bitmap.asAndroidBitmap()
        var sum = 0L
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                sum += bmp.getPixel(x, y).toLong()
            }
        }
        return sum
    }
}
