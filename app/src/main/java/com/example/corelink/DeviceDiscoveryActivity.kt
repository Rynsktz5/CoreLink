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
    private lateinit var scanPulse1: View
    private lateinit var scanPulse2: View
    private lateinit var scanButton: MaterialButton
    private lateinit var themeToggleButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var systemPairButton: MaterialButton

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
                    statusText.text = "Scan finished. Found ${devices.size} hosted rooms."
                    stopPulse()
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        try {
                            it.fetchUuidsWithSdp()
                        } catch (_: SecurityException) {
                        }
                    }
                }

                BluetoothDevice.ACTION_UUID -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    if (device != null && uuids != null) {
                        val hasCoreLink = uuids.any { uuid ->
                            uuid.toString().equals("00001101-0000-1000-8000-00805F9B34FB", ignoreCase = true)
                        }
                        if (hasCoreLink) {
                            val isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                            val stateLabel = if (isPaired) "Paired" else "Discovered"
                            addDevice(device, stateLabel, isPaired)
                            statusText.text = "Found ${devices.size} hosted room(s)"
                        }
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (device != null) {
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            Toast.makeText(context, "${device.name ?: "Device"} paired successfully!", Toast.LENGTH_SHORT).show()
                            addDevice(device, "Paired", true)
                            showConnectOptions(device)
                        } else if (bondState == BluetoothDevice.BOND_NONE) {
                            addDevice(device, "Discovered", false)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_discovery)

        statusText = findViewById(R.id.deviceScanStatus)
        scanPulse1 = findViewById(R.id.scanPulse1)
        scanPulse2 = findViewById(R.id.scanPulse2)
        scanButton = findViewById(R.id.scanButton)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        recyclerView = findViewById(R.id.deviceRecycler)
        systemPairButton = findViewById(R.id.systemPairButton)

        adapter = DeviceScanAdapter(
            devices = mutableListOf(),
            onPairClick = { item ->
                val bluetooth = bluetoothAdapter ?: return@DeviceScanAdapter
                try {
                    val remoteDevice = bluetooth.getRemoteDevice(item.address)
                    Toast.makeText(this, "Pairing with ${item.name}...", Toast.LENGTH_SHORT).show()
                    remoteDevice.createBond()
                } catch (e: Exception) {
                    Toast.makeText(this, "Pairing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onTap = { item ->
                val bluetooth = bluetoothAdapter ?: return@DeviceScanAdapter
                try {
                    val remoteDevice = bluetooth.getRemoteDevice(item.address)
                    if (remoteDevice.bondState == BluetoothDevice.BOND_BONDED) {
                        showConnectOptions(remoteDevice)
                    } else {
                        Toast.makeText(this, "Please pair with this device first.", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {}
            }
        )

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

        systemPairButton.setOnClickListener {
            try {
                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
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
        try {
            adapter.startDiscovery()
        } catch (_: SecurityException) {
        }
    }

    private fun loadPairedDevices() {
        val bluetooth = bluetoothAdapter ?: return
        devices.clear()
        adapter.replace(emptyList())
        
        val paired = try {
            bluetooth.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        paired.forEach { device ->
            try {
                device.fetchUuidsWithSdp()
            } catch (_: SecurityException) {
            }
        }
        
        statusText.text = "Checking paired devices for hosted rooms..."
    }

    private fun addDevice(device: BluetoothDevice, state: String, isPaired: Boolean, refresh: Boolean = true) {
        val name = try {
            device.name ?: getString(R.string.unknown_device)
        } catch (_: SecurityException) {
            getString(R.string.unknown_device)
        }

        devices[device.address] = DeviceItem(
            name = name,
            address = device.address,
            state = state,
            isPaired = isPaired
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
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
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
        
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse1, "scaleX", 1f, 3.5f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse1, "scaleY", 1f, 3.5f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse1, "alpha", 1f, 0f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        pulseAnimators += ObjectAnimator.ofFloat(scanPulse2, "scaleX", 1f, 3.5f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse2, "scaleY", 1f, 3.5f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(scanPulse2, "alpha", 1f, 0f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        scanPulse1.scaleX = 1f
        scanPulse1.scaleY = 1f
        scanPulse1.alpha = 0f
        scanPulse2.scaleX = 1f
        scanPulse2.scaleY = 1f
        scanPulse2.alpha = 0f
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
                    startActivity(Intent(this@DeviceDiscoveryActivity, ChatActivity::class.java))
                    finish()
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
                    startActivity(Intent(this@DeviceDiscoveryActivity, ChatActivity::class.java))
                    finish()
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
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return permissions.toTypedArray()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
