package com.example.corelink

import android.Manifest
import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DeviceDiscoveryActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scanPulse: View
    private lateinit var scanButton: MaterialButton
    private lateinit var themeToggleButton: ImageButton
    private lateinit var recyclerView: RecyclerView

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = linkedMapOf<String, DeviceItem>()
    private lateinit var adapter: DeviceScanAdapter
    private val pulseAnimators = mutableListOf<ObjectAnimator>()
    private var receiverRegistered = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasBluetoothPermissions()) {
                startDiscoveryFlow()
            } else {
                showToast(R.string.permissions_required)
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                startDiscoveryFlow()
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    statusText.text = getString(R.string.scan_status_running)
                    startPulse()
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    statusText.text = getString(R.string.scan_status_finished, devices.size)
                    stopPulse()
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let { addDevice(it, getString(R.string.device_state_discovered)) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_discovery)

        statusText = findViewById(R.id.deviceScanStatus)
        scanPulse = findViewById(R.id.scanPulse)
        scanButton = findViewById(R.id.scanButton)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        recyclerView = findViewById(R.id.deviceRecycler)

        adapter = DeviceScanAdapter(mutableListOf()) { device ->
            val bluetooth = bluetoothAdapter ?: return@DeviceScanAdapter
            val target = try {
                bluetooth.bondedDevices.firstOrNull { it.address == device.address }
            } catch (_: SecurityException) {
                null
            }

            if (target == null) {
                Toast.makeText(this, R.string.device_connect_pair_first, Toast.LENGTH_SHORT).show()
                return@DeviceScanAdapter
            }
            showConnectOptions(target)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        themeToggleButton.setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        scanButton.setOnClickListener {
            if (hasBluetoothPermissions()) {
                startDiscoveryFlow()
            } else {
                permissionLauncher.launch(requiredPermissions())
            }
        }

        loadPairedDevices()
    }

    override fun onStart() {
        super.onStart()
        registerDiscoveryReceiver()
    }

    override fun onStop() {
        super.onStop()
        stopDiscovery()
        unregisterDiscoveryReceiver()
    }

    private fun startDiscoveryFlow() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showToast(R.string.bluetooth_not_supported)
            return
        }

        if (!adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        loadPairedDevices()
        stopDiscovery()
        adapter.startDiscovery()
    }

    private fun loadPairedDevices() {
        val bluetooth = bluetoothAdapter ?: return
        devices.clear()
        val paired = try {
            bluetooth.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        paired.forEach { addDevice(it, getString(R.string.device_state_paired), refresh = false) }
        adapter.replace(devices.values.toList())
        statusText.text = if (paired.isEmpty()) {
            getString(R.string.scan_status_idle)
        } else {
            getString(R.string.scan_status_paired, paired.size)
        }
    }

    private fun addDevice(device: BluetoothDevice, state: String, refresh: Boolean = true) {
        val name = try {
            device.name ?: getString(R.string.unknown_device)
        } catch (_: SecurityException) {
            getString(R.string.unknown_device)
        }

        devices[device.address] = DeviceItem(
            name = name,
            address = device.address,
            state = state
        )

        if (refresh) {
            adapter.replace(devices.values.toList())
        }
    }

    private fun registerDiscoveryReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        registerReceiver(discoveryReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterDiscoveryReceiver() {
        if (!receiverRegistered) return
        unregisterReceiver(discoveryReceiver)
        receiverRegistered = false
    }

    private fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        stopPulse()
    }

    private fun startPulse() {
        if (pulseAnimators.any { it.isRunning }) return
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse, "scaleX", 1f, 1.16f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse, "scaleY", 1f, 1.16f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse, "rotation", 0f, 180f, 360f).apply {
            duration = 2200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        scanPulse.scaleX = 1f
        scanPulse.scaleY = 1f
        scanPulse.rotation = 0f
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothEnable() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showToast(R.string.bluetooth_not_supported)
            return
        }
        if (adapter.isEnabled) {
            startDiscoveryFlow()
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun showConnectOptions(device: BluetoothDevice) {
        val name = try {
            device.name ?: getString(R.string.unknown_device)
        } catch (_: SecurityException) {
            getString(R.string.unknown_device)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(
                arrayOf(
                    getString(R.string.join_selected_device),
                    getString(R.string.host_from_this_phone),
                    getString(R.string.open_session_manager)
                )
            ) { _, which ->
                when (which) {
                    0 -> joinSelectedDevice(device)
                    1 -> hostFromRadar()
                    2 -> startActivity(Intent(this, SessionManagerActivity::class.java))
                }
            }
            .show()
    }

    private fun joinSelectedDevice(device: BluetoothDevice) {
        statusText.text = getString(R.string.chat_status_connecting)
        CoreLinkSession.connectToDevice(
            device = device,
            onConnected = { name ->
                runOnUiThread {
                    statusText.text = getString(R.string.scan_connected_status, name)
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.connection_failed, error)
                }
            }
        )
    }

    private fun hostFromRadar() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showToast(R.string.bluetooth_not_supported)
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        statusText.text = getString(R.string.chat_status_hosting)
        CoreLinkSession.startServer(
            adapter = adapter,
            onConnected = { name ->
                runOnUiThread {
                    statusText.text = getString(R.string.scan_connected_status, name)
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.connection_failed, error)
                }
            }
        )
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions.toTypedArray()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
