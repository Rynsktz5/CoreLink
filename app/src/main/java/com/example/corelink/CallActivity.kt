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
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CallActivity : AppCompatActivity() {

    private lateinit var setupContainer: View
    private lateinit var callContainer: View
    private lateinit var callStatus: TextView
    private lateinit var callTimer: TextView
    private lateinit var callPeerName: TextView
    private lateinit var callPulse1: View
    private lateinit var callPulse2: View

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var callStartedAt = 0L
    private val pulseAnimators = mutableListOf<ObjectAnimator>()

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

        setupContainer = findViewById(R.id.setupContainer)
        callContainer = findViewById(R.id.callContainer)
        callStatus = findViewById(R.id.callStatus)
        callTimer = findViewById(R.id.callTimer)
        callPeerName = findViewById(R.id.callPeerName)
        callPulse1 = findViewById(R.id.callPulse)
        callPulse2 = findViewById(R.id.callPulse2)

        setupContainer.visibility = View.VISIBLE
        setupContainer.alpha = 1f
        callContainer.visibility = View.GONE
        callContainer.alpha = 0f

        findViewById<View>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        findViewById<View>(R.id.hostCallButton).setOnClickListener {
            startHostingCall()
        }

        findViewById<View>(R.id.joinCallButton).setOnClickListener {
            showJoinDevicePicker()
        }

        findViewById<View>(R.id.endCallButton).setOnClickListener {
            CoreLinkCallSession.end()
        }

        CoreLinkCallSession.addStateListener(callStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        stopPulse()
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
                crossfade(setupContainer, callContainer)
                callStatus.text = getString(R.string.call_idle_status)
                resetTimer()
                stopPulse()
            }

            CallPhase.HOSTING -> {
                crossfade(callContainer, setupContainer)
                callPeerName.text = "Discoverable Mode"
                callStatus.text = getString(R.string.call_hosting_status)
                resetTimer()
                startPulse()
            }

            CallPhase.JOINING -> {
                crossfade(callContainer, setupContainer)
                val peer = state.peerName ?: getString(R.string.unknown_device)
                callPeerName.text = peer
                callStatus.text = getString(R.string.call_joining_device_status, peer)
                resetTimer()
                startPulse()
            }

            CallPhase.ACTIVE -> {
                crossfade(callContainer, setupContainer)
                val peer = state.peerName ?: getString(R.string.call_connected_peer)
                callPeerName.text = peer
                callStatus.text = "CALL IN PROGRESS"
                callStartedAt = state.startedAt ?: System.currentTimeMillis()
                handler.removeCallbacks(timerRunnable)
                handler.post(timerRunnable)
                startPulse()
            }
        }
    }

    private fun crossfade(showView: View, hideView: View) {
        if (showView.visibility == View.VISIBLE && showView.alpha == 1f) {
            hideView.visibility = View.GONE
            hideView.alpha = 0f
            return
        }

        // Cancel running animations to prevent glitches
        showView.animate().cancel()
        hideView.animate().cancel()

        showView.alpha = 0f
        showView.visibility = View.VISIBLE

        showView.animate()
            .alpha(1f)
            .setDuration(400)
            .setListener(null)

        hideView.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                hideView.visibility = View.GONE
            }
    }

    private fun resetTimer() {
        callTimer.text = "00:00"
        handler.removeCallbacks(timerRunnable)
    }

    private fun startPulse() {
        if (pulseAnimators.any { it.isRunning }) return
        
        // Pulse 1
        pulseAnimators += ObjectAnimator.ofFloat(callPulse1, "scaleX", 1f, 2.5f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(callPulse1, "scaleY", 1f, 2.5f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(callPulse1, "alpha", 1f, 0f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Pulse 2 (delayed by 1000ms)
        pulseAnimators += ObjectAnimator.ofFloat(callPulse2, "scaleX", 1f, 2.5f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(callPulse2, "scaleY", 1f, 2.5f).apply {
            duration = 2000
            startDelay = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        pulseAnimators += ObjectAnimator.ofFloat(callPulse2, "alpha", 1f, 0f).apply {
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
        callPulse1.scaleX = 1f
        callPulse1.scaleY = 1f
        callPulse1.alpha = 0f
        callPulse2.scaleX = 1f
        callPulse2.scaleY = 1f
        callPulse2.alpha = 0f
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
