package app.mayak

import android.Manifest
import android.app.Activity
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
import app.mayak.ui.MayakApp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.prepareAccess(it) }
    }
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(application) as T
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestVpnPermissionThenConnect()
    }

    private val exportLogsDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.exportLogsTo(uri, contentResolver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MayakApp(
                viewModel = viewModel,
                onConnectRequested = ::requestPermissionsThenConnect,
                onExportLogsRequested = { exportLogsDocument.launch("mayak-logs.txt") },
                onScanQrRequested = {
                    scanQr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Наведите камеру на QR-код VPN").setBeepEnabled(false).setOrientationLocked(false))
                }
            )
        }
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun requestPermissionsThenConnect() {
        if (!viewModel.prepareConnection()) return
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

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let(viewModel::prepareAccess)
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::prepareAccess)
            return
        }
        if (intent?.getBooleanExtra(EXTRA_CONNECT_FROM_TILE, false) != true) return
        intent.removeExtra(EXTRA_CONNECT_FROM_TILE)
        requestPermissionsThenConnect()
    }

    companion object {
        private const val EXTRA_CONNECT_FROM_TILE = "connect_from_tile"

        fun connectFromTileIntent(context: android.content.Context): Intent {
            return Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONNECT_FROM_TILE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
