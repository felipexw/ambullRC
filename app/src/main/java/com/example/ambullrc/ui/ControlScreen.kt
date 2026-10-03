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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.sqrt

private val GridGap = 10.dp

// The D-pad is a 2x2 group of square arrows turned 45 degrees into a diamond, so all four arrows
// meet around a tiny center (just DiamondGap wide) instead of a full empty button-sized cell.
private const val DiamondRotation = 45f
private val DiamondGap = 6.dp

/**
 * The D-pad control area: four square directional buttons arranged as a diamond that meet around
 * a tiny center, dimmed and unresponsive while [connected] is false. The D-pad scales to fill
 * whatever region [modifier] grants it (feature 006 — see
 * specs/006-home-ui-branding-refresh/research.md Decision 4), instead of a fixed cell size.
 * Stateless — the View forwards every press/release to [viewModel]. See
 * specs/005-home-screen-ux-redesign/contracts/ui-contract.md.
 */
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    // Forwards connection state to the ViewModel so the lights control (feature 007) enables and
    // disables at the same moment this screen's own D-pad does.
    LaunchedEffect(connected) { viewModel.setConnected(connected) }
    Column(
        modifier = modifier.fillMaxSize().testTag("control_screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Buttons are perfect squares, sized as one cell of the largest 3x3 grid that fits the
            // available region, so they never distort into rectangles.
            val gridSize = minOf(maxWidth, maxHeight)
            val cellSize = (gridSize - GridGap * 2) / 3
            val diamondSide = cellSize * 2 + DiamondGap
            // Half the diamond's tip-to-tip span; the corner accessories are sized to stay
            // GridGap clear of its diagonal edges.
            val diamondRadius = diamondSide / sqrt(2f)
            val accessorySize = (gridSize - diamondRadius - GridGap * sqrt(2f)) / 2
            Box(Modifier.size(gridSize)) {
                LightsButton(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(accessorySize)
                )
                HornButton(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(accessorySize)
                )
                // Turned clockwise, the 2x2 grid's top-left cell points up, top-right points
                // right, bottom-right points down and bottom-left points left.
                Column(
                    verticalArrangement = Arrangement.spacedBy(DiamondGap),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = DiamondRotation }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DiamondGap)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(DiamondGap)) {
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
                    }
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
                // Undoes the D-pad's diamond rotation so the arrow glyph stays upright.
                modifier = Modifier.size(iconSize).rotate(-DiamondRotation)
            )
        }
    }
}

/**
 * Toggles the RC's lights (feature 007). Unlike [DirectionButton], this is a plain tap, not a
 * press/release pair — lights don't need hold-to-repeat. Its icon shows [ControlViewModel.lightsOn]
 * — the ESP32's last-confirmed state, never what a tap would produce — and it is only enabled
 * while [ControlViewModel.lightsEnabled] is true.
 */
@Composable
private fun LightsButton(
    viewModel: ControlViewModel,
    modifier: Modifier = Modifier
) {
    val lightsOn by viewModel.lightsOn.collectAsState()
    val lightsEnabled by viewModel.lightsEnabled.collectAsState()

    IconButton(
        onClick = viewModel::onLightsTapped,
        enabled = lightsEnabled,
        modifier = modifier
            .alpha(if (lightsEnabled) 1f else 0.35f)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceHigh)
            .testTag("btn_lights")
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val iconSize = minOf(maxWidth, maxHeight) * 0.42f
            Icon(
                imageVector = if (lightsOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = "Lights",
                tint = if (lightsOn) Accent else OnSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Triggers the ESP32's built-in horn sound (feature 008). Like [LightsButton], this is a plain
 * tap, not a press/release pair — the horn's duration is the ESP32's decision, not how long the
 * button is held. Always shows the same [Icons.Filled.Campaign] glyph; only its tint and enabled
 * state change, driven purely by [ControlViewModel.hornPlaying]/[ControlViewModel.hornAvailable] —
 * a local timer, never anything reported back by the ESP32.
 */
@Composable
private fun HornButton(
    viewModel: ControlViewModel,
    modifier: Modifier = Modifier
) {
    val hornPlaying by viewModel.hornPlaying.collectAsState()
    val hornAvailable by viewModel.hornAvailable.collectAsState()

    IconButton(
        onClick = viewModel::onHornTapped,
        enabled = hornAvailable,
        modifier = modifier
            .alpha(if (hornAvailable || hornPlaying) 1f else 0.35f)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceHigh)
            .testTag("btn_horn")
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val iconSize = minOf(maxWidth, maxHeight) * 0.42f
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = "Horn",
                tint = if (hornPlaying) Accent else OnSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
