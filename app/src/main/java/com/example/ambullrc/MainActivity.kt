package com.example.ambullrc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ambullrc.data.BluetoothEsp32Connection
import com.example.ambullrc.model.Esp32Connection
import com.example.ambullrc.ui.ConnectionStatusBar
import com.example.ambullrc.ui.ControlScreen
import com.example.ambullrc.ui.theme.AmbullRCTheme
import com.example.ambullrc.viewmodel.ConnectionViewModel
import com.example.ambullrc.viewmodel.ControlViewModel

class MainActivity : ComponentActivity() {

    // Shared by both ViewModels so sent commands travel over the same socket the connection
    // lifecycle manages.
    private val esp32Connection: Esp32Connection by lazy { BluetoothEsp32Connection(applicationContext) }

    private val connectionViewModel: ConnectionViewModel by viewModels {
        viewModelFactory {
            initializer {
                ConnectionViewModel(esp32Connection)
            }
        }
    }

    private val controlViewModel: ControlViewModel by viewModels {
        viewModelFactory {
            initializer {
                ControlViewModel(esp32Connection)
            }
        }
    }

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) connectionViewModel.connect() else connectionViewModel.onPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmbullRCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val state by connectionViewModel.state.collectAsState()
                    Column(modifier = Modifier.padding(innerPadding)) {
                        ConnectionStatusBar(
                            state = state,
                            onRetry = ::ensureBluetoothPermissionThenConnect
                        )
                        ControlScreen(viewModel = controlViewModel)
                    }
                }
            }
        }
        // Auto-connect on startup (US1). Retry (US3) reuses the same permission-then-connect path.
        ensureBluetoothPermissionThenConnect()
    }

    private fun ensureBluetoothPermissionThenConnect() {
        if (hasBluetoothConnectPermission()) {
            connectionViewModel.connect()
        } else {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
