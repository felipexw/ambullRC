package com.example.ambullrc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.model.FailureReason
import com.example.ambullrc.ui.ConnectionStatusBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose test for [ConnectionStatusBar]. Verifies each state renders the right status
 * text (US2), the Retry button appears only for Failed states, and tapping it invokes onRetry (US3).
 */
class ConnectionStatusBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(state: ConnectionState, onRetry: () -> Unit = {}) {
        composeRule.setContent { ConnectionStatusBar(state = state, onRetry = onRetry) }
    }

    // --- US2: status text per state ---

    @Test
    fun idleShowsNotConnected() {
        setState(ConnectionState.Idle)
        composeRule.onNodeWithTag("status_text").assertTextEquals("Not connected")
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
    fun failedShowsReason() {
        setState(ConnectionState.Failed(FailureReason.DEVICE_UNAVAILABLE))
        composeRule.onNodeWithTag("status_text").assertTextEquals("Not connected — device unavailable")
    }

    // --- Retry button visibility ---

    @Test
    fun retryButtonShownOnlyWhenFailed() {
        setState(ConnectionState.Failed(FailureReason.BLUETOOTH_DISABLED))
        composeRule.onNodeWithTag("btn_retry").assertIsDisplayed()
    }

    @Test
    fun retryButtonAbsentWhenNotFailed() {
        setState(ConnectionState.Connecting)
        composeRule.onNodeWithTag("btn_retry").assertDoesNotExist()
    }

    @Test
    fun retryButtonAbsentWhenConnected() {
        setState(ConnectionState.Connected)
        composeRule.onNodeWithTag("btn_retry").assertDoesNotExist()
    }

    // --- US3: Retry invokes callback ---

    @Test
    fun tappingRetryInvokesOnRetry() {
        var retries = 0
        setState(ConnectionState.Failed(FailureReason.CONNECTION_LOST)) { retries++ }
        composeRule.onNodeWithTag("btn_retry").performClick()
        assertEquals(1, retries)
    }
}
