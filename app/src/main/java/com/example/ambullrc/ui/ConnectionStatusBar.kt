package com.example.ambullrc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ambullrc.model.ConnectionState
import com.example.ambullrc.model.FailureReason

/**
 * Minimal, stateless connection status display: shows the current [state] as text and, only when
 * the state is [ConnectionState.Failed], a Retry button. Holds no logic beyond mapping state → text
 * (function over form); the host supplies [onRetry].
 */
@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = state.statusText(),
            modifier = Modifier.testTag("status_text")
        )
        if (state is ConnectionState.Failed) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .testTag("btn_retry")
                    .semantics { contentDescription = "Retry" }
            ) {
                Text("Retry")
            }
        }
    }
}

private fun ConnectionState.statusText(): String = when (this) {
    ConnectionState.Idle -> "Not connected"
    ConnectionState.Connecting -> "Connecting…"
    ConnectionState.Connected -> "Connected"
    is ConnectionState.Failed -> "Not connected — ${reason.label()}"
}

private fun FailureReason.label(): String = when (this) {
    FailureReason.PERMISSION_DENIED -> "permission denied"
    FailureReason.BLUETOOTH_DISABLED -> "Bluetooth disabled"
    FailureReason.DEVICE_UNAVAILABLE -> "device unavailable"
    FailureReason.CONNECTION_LOST -> "connection lost"
    FailureReason.ERROR -> "error"
}
