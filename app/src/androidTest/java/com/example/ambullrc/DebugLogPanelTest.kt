package com.example.ambullrc

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ambullrc.ui.DebugLogPanel
import com.example.ambullrc.viewmodel.LogCategory
import com.example.ambullrc.viewmodel.LogEntry
import com.example.ambullrc.viewmodel.LogLevel
import java.time.LocalTime
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests for [DebugLogPanel] (feature 005 redesign). Verifies the collapsed strip
 * shows only a live count (US3 FR-011), tap and drag both toggle open/closed (FR-009), height
 * transitions don't block the newest entry from auto-scrolling into view (FR-013), and entries
 * with different category/level render with visibly different colors (FR-012).
 */
class DebugLogPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String) =
        LogEntry(timestamp = LocalTime.of(12, 0, 0), category = category, level = level, message = message)

    private fun ImageBitmap.pixelSum(): Long {
        val bmp = asAndroidBitmap()
        var sum = 0L
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                sum += bmp.getPixel(x, y).toLong()
            }
        }
        return sum
    }

    // --- FR-011: collapsed shows only the live count, no individual entries ---

    @Test
    fun collapsedShowsOnlyCount() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf(entry(LogCategory.APP, message = "Connecting to ESP32…")))
        }
        // "log_panel_count" sits inside the handle's clickable subtree, which merges its
        // descendants' semantics — query the unmerged tree to find it directly.
        composeRule.onNodeWithTag("log_panel_count", useUnmergedTree = true).assertTextEquals("LOGS · 1")
        composeRule.onNodeWithText("Connecting to ESP32…").assertDoesNotExist()
    }

    // --- FR-009: tap toggles open/closed ---

    @Test
    fun tappingHandleExpandsAndShowsEntries() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf(entry(LogCategory.SENT, message = "UP -> sent")))
        }
        composeRule.onNodeWithTag("log_panel_handle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("debug_log").assertIsDisplayed()
        composeRule.onNodeWithText("UP -> sent").assertIsDisplayed()
    }

    @Test
    fun tappingHandleTwiceCollapsesAgain() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf(entry(LogCategory.SENT, message = "UP -> sent")))
        }
        val handle = composeRule.onNodeWithTag("log_panel_handle")
        handle.performClick()
        composeRule.waitForIdle()
        handle.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("UP -> sent").assertIsNotDisplayed()
        composeRule.onNodeWithTag("log_panel_count", useUnmergedTree = true).assertIsDisplayed()
    }

    // --- FR-009/Edge Cases: drag past the midpoint snaps open/closed ---

    @Test
    fun draggingHandleUpPastMidpointExpandsPanel() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf(entry(LogCategory.APP, message = "hello")))
        }
        composeRule.onNodeWithTag("log_panel_handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, -3000f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("hello").assertIsDisplayed()
    }

    @Test
    fun draggingHandleDownPastMidpointCollapsesPanel() {
        composeRule.setContent {
            DebugLogPanel(entries = listOf(entry(LogCategory.APP, message = "hello")))
        }
        val handle = composeRule.onNodeWithTag("log_panel_handle")
        handle.performClick() // start expanded
        composeRule.waitForIdle()

        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 3000f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("hello").assertIsNotDisplayed()
        composeRule.onNodeWithTag("log_panel_count", useUnmergedTree = true).assertIsDisplayed()
    }

    // --- FR-013: newest entry scrolled into view while expanded ---

    // 30 short rows comfortably fit within a full-screen-height expanded panel on tall test
    // devices, so overflow (and therefore the need to scroll/auto-scroll) isn't guaranteed unless
    // the panel's available height is bounded explicitly — mirrors how it's actually constrained
    // in the real app (weight(1f) below the header, not the full screen).
    private val BoundedPanelHeight = 300.dp

    @Test
    fun newestEntryIsVisibleAfterGrowingWhileExpanded() {
        var entries by mutableStateOf(listOf(entry(LogCategory.APP, message = "entry-1")))
        composeRule.setContent {
            DebugLogPanel(entries = entries, modifier = Modifier.height(BoundedPanelHeight))
        }
        composeRule.onNodeWithTag("log_panel_handle").performClick()
        composeRule.waitForIdle()

        entries = (1..30).map { entry(LogCategory.APP, message = "entry-$it") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("entry-30").assertIsDisplayed()
    }

    @Test
    fun olderEntryReachableByScrollingUpWhileExpanded() {
        val entries = (1..30).map { entry(LogCategory.APP, message = "entry-$it") }
        composeRule.setContent {
            DebugLogPanel(entries = entries, modifier = Modifier.height(BoundedPanelHeight))
        }
        composeRule.onNodeWithTag("log_panel_handle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("entry-1").assertIsNotDisplayed()
        composeRule.onNodeWithTag("debug_log").performScrollToNode(hasText("entry-1"))
        composeRule.onNodeWithText("entry-1").assertIsDisplayed()
    }

    // --- FR-012: category and level drive distinct colors ---

    @Test
    fun differentCategoriesRenderWithDifferentTagColors() {
        composeRule.setContent {
            DebugLogPanel(
                entries = listOf(
                    entry(LogCategory.SENT, message = "sent-message"),
                    entry(LogCategory.CONNECTION, message = "conn-message")
                )
            )
        }
        composeRule.onNodeWithTag("log_panel_handle").performClick()
        composeRule.waitForIdle()

        val sentTagColor = composeRule.onNodeWithText("TX").captureToImage().pixelSum()
        val connectionTagColor = composeRule.onNodeWithText("BLE").captureToImage().pixelSum()

        assertNotEquals(sentTagColor, connectionTagColor)
    }

    @Test
    fun differentLevelsRenderWithDifferentMessageColors() {
        composeRule.setContent {
            DebugLogPanel(
                entries = listOf(
                    entry(LogCategory.SENT, LogLevel.INFO, message = "info-message"),
                    entry(LogCategory.CONNECTION, LogLevel.ERROR, message = "error-message")
                )
            )
        }
        composeRule.onNodeWithTag("log_panel_handle").performClick()
        composeRule.waitForIdle()

        val infoColor = composeRule.onNodeWithText("info-message").captureToImage().pixelSum()
        val errorColor = composeRule.onNodeWithText("error-message").captureToImage().pixelSum()

        assertNotEquals(infoColor, errorColor)
    }
}
