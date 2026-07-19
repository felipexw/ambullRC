package com.example.ambullrc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ambullrc.model.Direction
import com.example.ambullrc.viewmodel.ControlViewModel

/**
 * The control screen: four directional buttons (up / down / left / right), each showing the
 * matching arrow icon. This View holds no logic — every press/release is forwarded to
 * [viewModel]. Test tags and content descriptions match the feature's UI contract.
 */
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DirectionButton(
            direction = Direction.UP,
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Up",
            testTag = "btn_up",
            onPressed = viewModel::onDirectionPressed,
            onReleased = viewModel::onDirectionReleased
        )
        Row(horizontalArrangement = Arrangement.Center) {
            DirectionButton(
                direction = Direction.LEFT,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Left",
                testTag = "btn_left",
                onPressed = viewModel::onDirectionPressed,
                onReleased = viewModel::onDirectionReleased
            )
            DirectionButton(
                direction = Direction.RIGHT,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Right",
                testTag = "btn_right",
                onPressed = viewModel::onDirectionPressed,
                onReleased = viewModel::onDirectionReleased
            )
        }
        DirectionButton(
            direction = Direction.DOWN,
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Down",
            testTag = "btn_down",
            onPressed = viewModel::onDirectionPressed,
            onReleased = viewModel::onDirectionReleased
        )
    }
}

@Composable
private fun DirectionButton(
    direction: Direction,
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onPressed: (Direction) -> Unit,
    onReleased: (Direction) -> Unit
) {
    // Track the button's interaction states so we can react to pointer hover and to press/release.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val active = isPressed || isHovered

    // The button commands the vehicle for as long as it is held down, not just on a completed
    // click: the ESP32 stops the motor once this stream of presses stops arriving.
    LaunchedEffect(isPressed) {
        if (isPressed) onPressed(direction) else onReleased(direction)
    }

    // Animate the feedback so hover, press, and release transition smoothly.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.25f else if (isHovered) 1.1f else 1f,
        label = "scale"
    )
    val background by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        label = "background"
    )
    val tint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "tint"
    )

    IconButton(
        onClick = {},
        interactionSource = interactionSource,
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(96.dp)
        )
    }
}
