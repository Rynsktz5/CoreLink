package com.example.corelink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hostingDialog: androidx.appcompat.app.AlertDialog? = null

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            auditServicesAndPermissions()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                auditServicesAndPermissions()
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_SHORT).show()
            }
        }

    private val sessionListener: (SessionState) -> Unit = { state ->
        runOnUiThread { updateStatusCard(state) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.enableBluetoothButton).setOnClickListener {
            if (hasBluetoothPermissions()) {
                requestBluetoothEnable()
            } else {
                permissionLauncher.launch(requiredPermissions())
            }
        }

        findViewById<android.view.View>(R.id.hostRoomCard).setOnClickListener {
            startHostingFlow()
        }

        findViewById<android.view.View>(R.id.joinRoomCard).setOnClickListener {
            startActivity(Intent(this, DeviceDiscoveryActivity::class.java))
        }

        findViewById<android.view.View>(R.id.openChatConsoleBtn).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<android.view.View>(R.id.disconnectLinkBtn).setOnClickListener {
            CoreLinkSession.disconnect()
        }

        findViewById<android.view.View>(R.id.advancedSettingsCard).setOnClickListener {
            startActivity(Intent(this, SessionManagerActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        updateBluetoothButton()
    }

    override fun onStart() {
        super.onStart()
        CoreLinkSession.addStateListener(sessionListener)
    }

    override fun onStop() {
        super.onStop()
        CoreLinkSession.removeStateListener(sessionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        hostingDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        updateBluetoothButton()
        auditServicesAndPermissions()
    }

    private fun startHostingFlow() {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        val myDeviceName = try {
            adapter.name ?: "this phone"
        } catch (_: SecurityException) {
            "this phone"
        }

        hostingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Hosting Bluetooth Room")
            .setMessage("Waiting for a peer to connect...\n\nAsk your friend to scan and join:\n\"$myDeviceName\"")
            .setNegativeButton("Cancel") { _, _ ->
                CoreLinkSession.disconnect()
            }
            .setCancelable(false)
            .show()

        CoreLinkSession.startServer(
            adapter = adapter,
            onConnected = { name ->
                runOnUiThread {
                    hostingDialog?.dismiss()
                    hostingDialog = null
                    // Open the chat console automatically!
                    startActivity(Intent(this@MainActivity, ChatActivity::class.java))
                }
            },
            onError = { error ->
                runOnUiThread {
                    hostingDialog?.dismiss()
                    hostingDialog = null
                    Toast.makeText(this@MainActivity, "Hosting failed: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateStatusCard(state: SessionState) {
        val disconnectedConsole = findViewById<android.view.View>(R.id.disconnectedLayout)
        val connectedConsole = findViewById<android.view.View>(R.id.connectedLayout)
        val peerNameText = findViewById<TextView>(R.id.connectedPeerName)

        if (state.connected) {
            disconnectedConsole.visibility = android.view.View.GONE
            connectedConsole.visibility = android.view.View.VISIBLE
            peerNameText.text = "LINKED: ${state.deviceName ?: "Nearby peer"}"

            // Auto dismiss hosting dialog if connected
            hostingDialog?.dismiss()
            hostingDialog = null
        } else {
            disconnectedConsole.visibility = android.view.View.VISIBLE
            connectedConsole.visibility = android.view.View.GONE
        }
    }

    private fun requestBluetoothEnable() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (adapter.isEnabled) {
            updateBluetoothButton()
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun updateBluetoothButton() {
        val button = findViewById<MaterialButton>(R.id.enableBluetoothButton)
        val enabled = bluetoothAdapter?.isEnabled == true
        button.visibility = if (enabled) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    private fun auditServicesAndPermissions() {
        val missingPermissions = mutableListOf<String>()
        val requiredPerms = requiredPermissions()

        for (perm in requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(perm)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val gpsEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        val networkEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        if (!gpsEnabled && !networkEnabled) {
            showLocationServicesPrompt()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                showOverlayPermissionPrompt()
            }
        }
    }

    private fun showLocationServicesPrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Services Required")
            .setMessage("Location services must be enabled to scan for nearby Bluetooth devices.")
            .setPositiveButton("Enable Settings") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayPermissionPrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Display Over Other Apps Required")
            .setMessage("CoreLink requires permission to display emergency alerts on top of other apps so you never miss critical SOS broadcasts. Please enable 'Display over other apps' in settings.")
            .setPositiveButton("Settings") { _, _ ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
