package com.example.ambullrc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ambullrc.model.Direction
import com.example.ambullrc.ui.theme.Accent
import com.example.ambullrc.ui.theme.OnAccent
import com.example.ambullrc.ui.theme.OnSurfaceVariant
import com.example.ambullrc.ui.theme.Outline
import com.example.ambullrc.ui.theme.SurfaceHigh
import com.example.ambullrc.viewmodel.ControlViewModel

private val GridGap = 10.dp

/**
 * The D-pad control area: four directional buttons in a cross layout around a decorative center
 * hub, dimmed and unresponsive while [connected] is false. The cross scales to fill whatever
 * region [modifier] grants it (feature 006 — see specs/006-home-ui-branding-refresh/research.md
 * Decision 4), instead of a fixed cell size. Stateless — the View forwards every press/release to
 * [viewModel]. See specs/005-home-screen-ux-redesign/contracts/ui-contract.md.
 */
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().testTag("control_screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Buttons are perfect squares: the grid is the largest 3x3 square (cellSize x gaps)
            // that fits the available region, so it never distorts into rectangles.
            val cellSize = (minOf(maxWidth, maxHeight) - GridGap * 2) / 3
            Column(verticalArrangement = Arrangement.spacedBy(GridGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    Box(Modifier.size(cellSize))
                    DirectionButton(
                        direction = Direction.UP,
                        icon = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Up",
                        testTag = "btn_up",
                        connected = connected,
                        onPressed = viewModel::onDirectionPressed,
                        onReleased = viewModel::onDirectionReleased,
                        modifier = Modifier.size(cellSize)
                    )
                    Box(Modifier.size(cellSize))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    DirectionButton(
                        direction = Direction.LEFT,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Left",
                        testTag = "btn_left",
                        connected = connected,
                        onPressed = viewModel::onDirectionPressed,
                        onReleased = viewModel::onDirectionReleased,
                        modifier = Modifier.size(cellSize)
                    )
                    Box(Modifier.size(cellSize))
                    DirectionButton(
                        direction = Direction.RIGHT,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Right",
                        testTag = "btn_right",
                        connected = connected,
                        onPressed = viewModel::onDirectionPressed,
                        onReleased = viewModel::onDirectionReleased,
                        modifier = Modifier.size(cellSize)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    Box(Modifier.size(cellSize))
                    DirectionButton(
                        direction = Direction.DOWN,
                        icon = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Down",
                        testTag = "btn_down",
                        connected = connected,
                        onPressed = viewModel::onDirectionPressed,
                        onReleased = viewModel::onDirectionReleased,
                        modifier = Modifier.size(cellSize)
                    )
                    Box(Modifier.size(cellSize))
                }
            }
        }
        if (!connected) {
            Text(
                text = "Waiting for connection to enable controls",
                color = Outline,
                fontSize = 13.sp,
                modifier = Modifier.testTag("dpad_hint")
            )
        }
    }
}

@Composable
private fun DirectionButton(
    direction: Direction,
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    connected: Boolean,
    onPressed: (Direction) -> Unit,
    onReleased: (Direction) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // The button commands the vehicle for as long as it is held down, not just on a completed
    // click: the ESP32 stops the motor once this stream of presses stops arriving. A disabled
    // (connected == false) IconButton never dispatches press interactions, so this only fires
    // while connected.
    LaunchedEffect(isPressed) {
        if (isPressed) onPressed(direction) else onReleased(direction)
    }

    val background by animateColorAsState(
        targetValue = if (isPressed) Accent else SurfaceHigh,
        label = "direction_background"
    )
    val tint by animateColorAsState(
        targetValue = if (isPressed) OnAccent else OnSurfaceVariant,
        label = "direction_tint"
    )
    // Soft accent-colored glow while held, approximating the design's `0 0 0 4px accent33` ring.
    val glow by animateColorAsState(
        targetValue = if (isPressed) Accent.copy(alpha = 0.2f) else Accent.copy(alpha = 0f),
        label = "direction_glow"
    )

    IconButton(
        onClick = {},
        enabled = connected,
        interactionSource = interactionSource,
        modifier = modifier
            .alpha(if (connected) 1f else 0.35f)
            .border(width = 4.dp, color = glow, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .testTag(testTag)
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val iconSize = minOf(maxWidth, maxHeight) * 0.42f
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
