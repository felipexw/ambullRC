package com.example.ambullrc.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.ui.theme.ConnectedContainer
import com.example.ambullrc.ui.theme.ConnectedDot
import com.example.ambullrc.ui.theme.ConnectingContainer
import com.example.ambullrc.ui.theme.ConnectingDot
import com.example.ambullrc.ui.theme.DisconnectedContainer
import com.example.ambullrc.ui.theme.DisconnectedDot
import com.example.ambullrc.ui.theme.OnAccent
import com.example.ambullrc.ui.theme.OnConnectedContainer
import com.example.ambullrc.ui.theme.OnConnectingContainer
import com.example.ambullrc.ui.theme.OnDisconnectedContainer
import com.example.ambullrc.ui.theme.OnSurface
import com.example.ambullrc.ui.theme.SurfaceAppBar

/**
 * The home screen header: device name + a color-coded connection status pill, with a Retry
 * action shown only while disconnected. Stateless — [state] and [onRetry] are supplied by the
 * host. See specs/005-home-screen-ux-redesign/data-model.md for the state → presentation mapping.
 */
@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = state.toPresentation()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceAppBar)
            .padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = presentation.deviceName,
            color = OnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("device_name")
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(presentation)
            if (presentation.showRetry) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.example.ambullrc.ui.theme.Accent,
                        contentColor = OnAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .testTag("btn_retry")
                        .semantics { contentDescription = "Retry" }
                ) {
                    Text(text = "Retry", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(presentation: StatusPresentation) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(presentation.backgroundColor)
            .padding(start = 8.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val dotAlpha = if (presentation.animated) {
            val transition = rememberInfiniteTransition(label = "status_dot_pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "status_dot_alpha"
            )
            alpha
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .testTag("status_dot")
                .size(8.dp)
                .graphicsLayer { this.alpha = dotAlpha }
                .clip(CircleShape)
                .background(presentation.dotColor)
        )
        Text(
            text = presentation.label,
            color = presentation.foregroundColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("status_text")
        )
    }
}

/** Derived visual attributes for a [ConnectionState] — see data-model.md's mapping table. */
private data class StatusPresentation(
    val deviceName: String,
    val label: String,
    val backgroundColor: Color,
    val foregroundColor: Color,
    val dotColor: Color,
    val animated: Boolean,
    val showRetry: Boolean
)

private fun ConnectionState.toPresentation(): StatusPresentation = when (this) {
    ConnectionState.Idle -> disconnectedPresentation()
    ConnectionState.Connecting -> StatusPresentation(
        deviceName = "No device",
        label = "Connecting…",
        backgroundColor = ConnectingContainer,
        foregroundColor = OnConnectingContainer,
        dotColor = ConnectingDot,
        animated = true,
        showRetry = false
    )
    ConnectionState.Connected -> StatusPresentation(
        deviceName = "ESP32-RCCAR",
        label = "Connected",
        backgroundColor = ConnectedContainer,
        foregroundColor = OnConnectedContainer,
        dotColor = ConnectedDot,
        animated = false,
        showRetry = false
    )
    is ConnectionState.Failed -> disconnectedPresentation()
}

private fun disconnectedPresentation() = StatusPresentation(
    deviceName = "No device",
    label = "Disconnected",
    backgroundColor = DisconnectedContainer,
    foregroundColor = OnDisconnectedContainer,
    dotColor = DisconnectedDot,
    animated = false,
    showRetry = true
)
