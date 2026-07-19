package com.example.ambullrc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ambullrc.ui.theme.OutlineVariant
import com.example.ambullrc.ui.theme.SurfaceHigh
import com.example.ambullrc.ui.theme.SurfaceSheet
import com.example.ambullrc.viewmodel.ControlViewModel

private val CellSize = 76.dp
private val GridGap = 10.dp

/**
 * The D-pad control area: four directional buttons in a cross layout around a decorative center
 * hub, dimmed and unresponsive while [connected] is false. Stateless — the View forwards every
 * press/release to [viewModel]. See specs/005-home-screen-ux-redesign/contracts/ui-contract.md.
 */
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GridGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                Box(Modifier.size(CellSize))
                DirectionButton(
                    direction = Direction.UP,
                    icon = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Up",
                    testTag = "btn_up",
                    connected = connected,
                    onPressed = viewModel::onDirectionPressed,
                    onReleased = viewModel::onDirectionReleased
                )
                Box(Modifier.size(CellSize))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                DirectionButton(
                    direction = Direction.LEFT,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Left",
                    testTag = "btn_left",
                    connected = connected,
                    onPressed = viewModel::onDirectionPressed,
                    onReleased = viewModel::onDirectionReleased
                )
                CenterHub()
                DirectionButton(
                    direction = Direction.RIGHT,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Right",
                    testTag = "btn_right",
                    connected = connected,
                    onPressed = viewModel::onDirectionPressed,
                    onReleased = viewModel::onDirectionReleased
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                Box(Modifier.size(CellSize))
                DirectionButton(
                    direction = Direction.DOWN,
                    icon = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Down",
                    testTag = "btn_down",
                    connected = connected,
                    onPressed = viewModel::onDirectionPressed,
                    onReleased = viewModel::onDirectionReleased
                )
                Box(Modifier.size(CellSize))
            }
        }
        Text(
            text = if (connected) "Hold a direction to drive" else "Waiting for connection to enable controls",
            color = Outline,
            fontSize = 13.sp,
            modifier = Modifier.testTag("dpad_hint")
        )
    }
}

/** Decorative, non-interactive center cell of the D-pad cross. */
@Composable
private fun CenterHub() {
    Box(
        modifier = Modifier
            .size(CellSize)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceSheet),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(width = 2.dp, color = OutlineVariant, shape = CircleShape)
        )
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
    onReleased: (Direction) -> Unit
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
        modifier = Modifier
            .size(CellSize)
            .alpha(if (connected) 1f else 0.35f)
            .border(width = 4.dp, color = glow, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
    }
}
