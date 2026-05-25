package com.example.corelink

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class CallActivity : AppCompatActivity() {

    private lateinit var callStatus: TextView
    private lateinit var callTimer: TextView
    private lateinit var callPulse: android.view.View

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var callStartedAt = 0L
    private var pulseAnimator: ObjectAnimator? = null

    private val callStateListener: (CallSessionState) -> Unit = { state ->
        runOnUiThread { renderState(state) }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (!granted.values.all { it }) {
                Toast.makeText(this, R.string.call_permissions_required, Toast.LENGTH_SHORT).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                Toast.makeText(this, R.string.bluetooth_enabled_status, Toast.LENGTH_SHORT).show()
            }
        }

    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = ((System.currentTimeMillis() - callStartedAt) / 1000).toInt()
            val minutes = elapsed / 60
            val seconds = elapsed % 60
            callTimer.text = String.format("%02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        callStatus = findViewById(R.id.callStatus)
        callTimer = findViewById(R.id.callTimer)
        callPulse = findViewById(R.id.callPulse)

        findViewById<ImageButton>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        findViewById<MaterialButton>(R.id.hostCallButton).setOnClickListener {
            startHostingCall()
        }

        findViewById<MaterialButton>(R.id.joinCallButton).setOnClickListener {
            showJoinDevicePicker()
        }

        findViewById<MaterialButton>(R.id.endCallButton).setOnClickListener {
            CoreLinkCallSession.end()
        }

        CoreLinkCallSession.addStateListener(callStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        pulseAnimator?.cancel()
        CoreLinkCallSession.removeStateListener(callStateListener)
    }

    private fun startHostingCall() {
        val adapter = bluetoothAdapter
        if (!ensureCallPermissions()) return
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        CoreLinkCallSession.host(adapter) { error ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.connection_failed, error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showJoinDevicePicker() {
        val adapter = bluetoothAdapter
        if (!ensureCallPermissions()) return
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            requestBluetoothEnable()
            return
        }

        val devices = try {
            adapter.bondedDevices.toList().sortedBy { it.name ?: it.address }
        } catch (_: SecurityException) {
            emptyList()
        }

        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.no_paired_devices, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = devices.map { deviceLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.call_select_device)
            .setItems(labels) { _, which -> joinSelectedDevice(devices[which]) }
            .show()
    }

    private fun joinSelectedDevice(device: BluetoothDevice) {
        CoreLinkCallSession.join(device) { error ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.connection_failed, error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderState(state: CallSessionState) {
        when (state.phase) {
            CallPhase.IDLE -> {
                callStatus.text = getString(R.string.call_idle_status)
                resetTimer()
                stopPulse()
            }

            CallPhase.HOSTING -> {
                callStatus.text = getString(R.string.call_hosting_status)
                resetTimer()
                startPulse()
            }

            CallPhase.JOINING -> {
                val peer = state.peerName ?: getString(R.string.unknown_device)
                callStatus.text = getString(R.string.call_joining_device_status, peer)
                resetTimer()
                startPulse()
            }

            CallPhase.ACTIVE -> {
                val peer = state.peerName ?: getString(R.string.call_connected_peer)
                callStatus.text = getString(R.string.call_active_status, peer)
                callStartedAt = state.startedAt ?: System.currentTimeMillis()
                handler.removeCallbacks(timerRunnable)
                handler.post(timerRunnable)
                startPulse()
            }
        }
    }

    private fun resetTimer() {
        callTimer.text = "00:00"
        handler.removeCallbacks(timerRunnable)
    }

    private fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ObjectAnimator.ofFloat(callPulse, "alpha", 0.35f, 1f).apply {
            duration = 800
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        callPulse.alpha = 1f
        pulseAnimator = null
    }

    private fun ensureCallPermissions(): Boolean {
        val denied = requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            permissionLauncher.launch(denied.toTypedArray())
            return false
        }
        return true
    }

    private fun requestBluetoothEnable() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (adapter.isEnabled) return
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        permissions += Manifest.permission.BLUETOOTH
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions.toTypedArray()
    }

    private fun deviceLabel(device: BluetoothDevice): String {
        val name = try {
            device.name ?: getString(R.string.unknown_device)
        } catch (_: SecurityException) {
            getString(R.string.unknown_device)
        }
        return "$name\n${device.address}"
    }
}
