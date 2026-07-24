package com.example.ambullrc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ambullrc.ui.theme.ConnectedDot
import com.example.ambullrc.ui.theme.ConnectingDot
import com.example.ambullrc.ui.theme.ConnectionTagColor
import com.example.ambullrc.ui.theme.DisconnectedDot
import com.example.ambullrc.ui.theme.OnSurface
import com.example.ambullrc.ui.theme.OnSurfaceVariant
import com.example.ambullrc.ui.theme.Outline
import com.example.ambullrc.ui.theme.SurfaceSheet
import com.example.ambullrc.viewmodel.LogCategory
import com.example.ambullrc.viewmodel.LogEntry
import com.example.ambullrc.viewmodel.LogLevel
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

private val CollapsedHeight = 40.dp
private const val ExpandedHeightFraction = 0.42f
private val DragTapThreshold = 10.dp
private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/**
 * Tap-or-drag collapsible sheet showing [entries] (oldest first). Collapsed by default to a thin
 * strip with a live count; expanded it grows to [ExpandedHeightFraction] of the screen height and
 * auto-scrolls to the newest entry. It occupies real layout space (a normal Column child, not an
 * overlay) so the header and control widget above it stay visible rather than being covered.
 * Open/closed and drag-height state are local to this Composable — purely presentational, no
 * business meaning (see specs/005-home-screen-ux-redesign/research.md Decision 6).
 */
@Composable
fun DebugLogPanel(
    entries: List<LogEntry>,
    truncated: Boolean = false,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val expandedHeightPx = with(density) { (screenHeightDp * ExpandedHeightFraction).toPx() }
    val collapsedHeightPx = with(density) { CollapsedHeight.toPx() }
    val thresholdPx = with(density) { DragTapThreshold.toPx() }

    // Single source of truth for the sheet's current height in px. Drag updates it directly
    // (snapTo, tracking the finger with no lag); tap and drag-release both settle it toward
    // the target with the same 220ms animation, so there's no jump at the drag/animation
    // handoff (spec.md FR-010).
    val heightPx = remember { Animatable(collapsedHeightPx) }
    var dragStartHeightPx by remember { mutableFloatStateOf(collapsedHeightPx) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, expandedHeightPx) {
        if (!isDragging) {
            heightPx.animateTo(
                targetValue = if (expanded) expandedHeightPx else collapsedHeightPx,
                animationSpec = tween(durationMillis = 220)
            )
        }
    }

    val sheetHeight = with(density) { heightPx.value.toDp() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(SurfaceSheet)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val updated = (heightPx.value - delta).coerceIn(collapsedHeightPx, expandedHeightPx)
                        scope.launch { heightPx.snapTo(updated) }
                    },
                    onDragStarted = {
                        isDragging = true
                        dragStartHeightPx = heightPx.value
                    },
                    onDragStopped = {
                        val reached = heightPx.value
                        val moved = abs(reached - dragStartHeightPx)
                        val newExpanded = if (moved < thresholdPx) {
                            expanded
                        } else {
                            reached > (collapsedHeightPx + expandedHeightPx) / 2f
                        }
                        isDragging = false
                        if (newExpanded == expanded) {
                            // No state change to re-trigger the LaunchedEffect above — settle
                            // explicitly so a small under-threshold drag still snaps cleanly.
                            scope.launch {
                                heightPx.animateTo(
                                    targetValue = if (newExpanded) expandedHeightPx else collapsedHeightPx,
                                    animationSpec = tween(durationMillis = 220)
                                )
                            }
                        } else {
                            expanded = newExpanded
                        }
                    }
                )
                .padding(top = 8.dp, bottom = 6.dp)
                .testTag("log_panel_handle"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Outline)
            )
            if (!expanded) {
                Text(
                    text = "LOGS · ${entries.size.withTruncationSuffix(truncated)}",
                    color = Outline,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("log_panel_count")
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Device Logs", color = OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${entries.size.withTruncationSuffix(truncated)} lines",
                        color = Outline,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("log_panel_line_count")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.testTag("log_panel_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Clear logs",
                            tint = Outline
                        )
                    }
                }
            }

            val listState = rememberLazyListState()
            LaunchedEffect(entries.size) {
                if (entries.isNotEmpty()) {
                    listState.animateScrollToItem(entries.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("debug_log")
            ) {
                items(entries) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = entry.timestamp.format(TimestampFormatter),
            color = Outline,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = entry.category.tag,
            color = entry.category.tagColor(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(46.dp)
        )
        Text(
            text = entry.message,
            color = entry.level.messageColor(),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun Int.withTruncationSuffix(truncated: Boolean): String = if (truncated) "$this+" else "$this"

private fun LogCategory.tagColor(): Color = when (this) {
    LogCategory.SENT -> ConnectingDot
    LogCategory.RECEIVED -> ConnectedDot
    LogCategory.CONNECTION -> ConnectionTagColor
    LogCategory.APP -> OnSurfaceVariant
}

private fun LogLevel.messageColor(): Color = when (this) {
    LogLevel.ERROR -> DisconnectedDot
    LogLevel.WARN -> ConnectingDot
    LogLevel.INFO -> OnSurface
}
