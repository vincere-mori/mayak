package app.beacon

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.beacon.ui.BeaconApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(application) as T
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.connect()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestVpnPermissionThenConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BeaconApp(
                viewModel = viewModel,
                onConnectRequested = ::requestPermissionsThenConnect
            )
        }
    }

    private fun requestPermissionsThenConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermissionThenConnect()
        }
    }

    private fun requestVpnPermissionThenConnect() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null) {
            vpnPermission.launch(intent)
        } else {
            viewModel.connect()
        }
    }
}
