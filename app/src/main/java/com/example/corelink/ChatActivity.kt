package com.example.corelink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendBtn: ImageButton
    private lateinit var micBtn: ImageButton
    private lateinit var connectBtn: ImageButton
    private lateinit var emergencyBtn: ImageButton
    private lateinit var callBtn: ImageButton
    private lateinit var themeToggleBtn: ImageButton
    private lateinit var statusText: TextView

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Pair<String, Boolean>>()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val sessionListener: (IncomingPayload) -> Unit = { incoming ->
        runOnUiThread {
            when (incoming) {
                is IncomingPayload.Text -> addLiveMessage(incoming.text, false)
                is IncomingPayload.Transcript -> addLiveMessage("Voice text: ${incoming.text}", false)
                is IncomingPayload.Alert -> addLiveMessage("Alert: ${incoming.text}", false)
                is IncomingPayload.Location -> addLiveMessage("Location: ${incoming.text}", false)
                is IncomingPayload.VoiceNote -> addLiveMessage(getString(R.string.voice_note_received), false)
            }
        }
    }

    private val stateListener: (SessionState) -> Unit = { state ->
        runOnUiThread {
            statusText.text = if (state.connected) {
                getString(R.string.chat_status_connected_device, state.deviceName ?: getString(R.string.unknown_device))
            } else {
                getString(R.string.chat_status_not_connected)
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasRequiredPermissions()) {
                promptConnectionMode()
            } else {
                showToast(R.string.permissions_required)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.chatRecycler)
        input = findViewById(R.id.chatInput)
        sendBtn = findViewById(R.id.sendBtn)
        micBtn = findViewById(R.id.micBtn)
        connectBtn = findViewById(R.id.connectBtn)
        emergencyBtn = findViewById(R.id.emergencyBtn)
        callBtn = findViewById(R.id.callBtn)
        themeToggleBtn = findViewById(R.id.themeToggleButton)
        statusText = findViewById(R.id.statusText)

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        loadPersistedMessages()

        sendBtn.setOnClickListener { sendTypedMessage() }
        micBtn.setOnClickListener { startActivity(Intent(this, VoiceToolsActivity::class.java)) }
        connectBtn.setOnClickListener { ensurePermissionsAndConnect() }
        emergencyBtn.setOnClickListener { startActivity(Intent(this, EmergencyActivity::class.java)) }
        callBtn.setOnClickListener { startActivity(Intent(this, CallActivity::class.java)) }
        themeToggleBtn.setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        CoreLinkSession.addMessageListener(sessionListener)
        CoreLinkSession.addStateListener(stateListener)
    }

    override fun onStop() {
        super.onStop()
        CoreLinkSession.removeMessageListener(sessionListener)
        CoreLinkSession.removeStateListener(stateListener)
    }

    private fun loadPersistedMessages() {
        messages.clear()
        val saved = ChatHistoryStore.load(this)
        if (saved.isEmpty()) {
            messages += getString(R.string.chat_welcome_message) to false
        } else {
            messages += saved.map { it.content to it.isSent }
        }
        adapter.notifyDataSetChanged()
        recyclerView.post { recyclerView.scrollToPosition(messages.lastIndex.coerceAtLeast(0)) }
    }

    private fun addLiveMessage(msg: String, isSent: Boolean) {
        messages += msg to isSent
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun ensurePermissionsAndConnect() {
        if (bluetoothAdapter == null) {
            showToast(R.string.bluetooth_not_supported)
            return
        }

        if (!hasRequiredPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            showToast(R.string.enable_bluetooth_first)
            return
        }

        promptConnectionMode()
    }

    private fun sendTypedMessage() {
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return

        if (CoreLinkSession.isConnected()) {
            CoreLinkSession.sendText(msg)
            addLiveMessage(msg, true)
            statusText.text = getString(
                R.string.chat_status_connected_device,
                CoreLinkSession.connectedDeviceName ?: getString(R.string.unknown_device)
            )
        } else {
            statusText.text = getString(R.string.chat_status_offline)
            showToast(R.string.chat_offline_hint)
        }

        input.text.clear()
    }

    private fun promptConnectionMode() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connect_mode_title)
            .setItems(arrayOf(getString(R.string.host_mode), getString(R.string.join_mode))) { _, which ->
                if (which == 0) hostConnection() else showPairedDevices()
            }
            .show()
    }

    private fun hostConnection() {
        val adapter = bluetoothAdapter ?: return
        statusText.text = getString(R.string.chat_status_hosting)
        addLiveMessage(getString(R.string.hosting_message), false)
        CoreLinkSession.startServer(
            adapter = adapter,
            onConnected = { name ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_connected_device, name)
                    addLiveMessage(getString(R.string.connected_as_host), false)
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_error)
                    addLiveMessage(getString(R.string.connection_failed, error), false)
                }
            }
        )
    }

    private fun showPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        val devices = try {
            adapter.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        if (devices.isEmpty()) {
            showToast(R.string.no_paired_devices)
            return
        }

        val names = devices.map { deviceName(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_device_title)
            .setItems(names) { _, index -> connectToDevice(devices[index]) }
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        statusText.text = getString(R.string.chat_status_connecting)
        addLiveMessage(getString(R.string.connecting_to_device, deviceName(device)), false)
        CoreLinkSession.connectToDevice(
            device = device,
            onConnected = { name ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_connected_device, name)
                    addLiveMessage(getString(R.string.connected_to_device, name), false)
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_error)
                    addLiveMessage(getString(R.string.connection_failed, error), false)
                }
            }
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    private fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: getString(R.string.unknown_device)
        } catch (_: SecurityException) {
            getString(R.string.unknown_device)
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
