package com.example.ambullrc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.ambullrc.ui.DebugLogPanel
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests for [DebugLogPanel] (feature 004). Verifies the widget renders its
 * entries (US1) and stays readable/scrollable as history grows (US2).
 */
class DebugLogPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- US1: widget displays entries, newest visible without manual action ---

    @Test
    fun panelIsDisplayedWithProvidedEntries() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf("Connecting to ESP32…", "Connected", "UP -> sent"))
        }
        composeRule.onNodeWithTag("debug_log").assertIsDisplayed()
        composeRule.onNodeWithText("Connecting to ESP32…").assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("UP -> sent").assertIsDisplayed()
    }

    @Test
    fun newestEntryIsVisibleWithoutManualScrollWhenHistoryOverflows() {
        val entries = (1..30).map { "entry-$it" }
        composeRule.setContent {
            DebugLogPanel(entries = entries)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("entry-30").assertIsDisplayed()
    }

    // --- US2: chronological order, older entries reachable by scrolling ---

    @Test
    fun olderEntryIsHiddenInitiallyButReachableByScrollingUp() {
        val entries = (1..30).map { "entry-$it" }
        composeRule.setContent {
            DebugLogPanel(entries = entries)
        }
        composeRule.waitForIdle()

        // Auto-scrolled to the newest entry, so the oldest is off-screen until scrolled to.
        composeRule.onNodeWithText("entry-1").assertIsNotDisplayed()

        composeRule.onNodeWithTag("debug_log").performScrollToNode(hasText("entry-1"))
        composeRule.onNodeWithText("entry-1").assertIsDisplayed()
    }

    @Test
    fun entriesRemainIndividuallyReadableInChronologicalOrder() {
        val entries = listOf("Connecting to ESP32…", "Connected", "UP -> sent", "DOWN -> sent")
        composeRule.setContent {
            DebugLogPanel(entries = entries)
        }
        composeRule.waitForIdle()

        for (entry in entries) {
            composeRule.onNodeWithTag("debug_log").performScrollToNode(hasText(entry))
            composeRule.onNodeWithText(entry).assertIsDisplayed()
        }
    }
}
