package com.example.corelink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.speech.RecognizerIntent
import android.media.MediaRecorder
import java.io.File
import java.util.Locale
import android.location.Location
import android.location.LocationManager
import android.content.Context

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendBtn: MaterialButton
    private lateinit var micBtn: MaterialButton
    private lateinit var connectBtn: MaterialButton
    private lateinit var emergencyBtn: MaterialButton
    private lateinit var callBtn: MaterialButton
    private lateinit var themeToggleBtn: MaterialButton
    private lateinit var statusText: TextView

    private lateinit var callingBar: android.view.View
    private lateinit var callingBarText: TextView
    private lateinit var callingBarEndBtn: com.google.android.material.button.MaterialButton

    private lateinit var normalInputLayout: android.view.View
    private lateinit var recordingLayout: android.view.View
    private lateinit var recordingDot: android.widget.ImageView
    private lateinit var recordingTimerText: android.widget.TextView
    private lateinit var recordCancelBtn: MaterialButton
    private lateinit var recordSendBtn: MaterialButton

    private lateinit var telemetryPanel: android.view.View
    private lateinit var telemetryMacAddress: android.widget.TextView
    private lateinit var telemetryStats: android.widget.TextView

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatLogEntry>()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var isRecording = false

    private var callStartedAt = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = ((System.currentTimeMillis() - callStartedAt) / 1000).toInt()
            val minutes = elapsed / 60
            val seconds = elapsed % 60
            callingBarText.text = String.format("Live voice call: %02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spokenText.isNullOrEmpty()) {
                val currentText = input.text.toString()
                input.setText(if (currentText.isEmpty()) spokenText else "$currentText $spokenText")
                input.setSelection(input.text.length)
            }
        }

    private val callStateListener: (CallSessionState) -> Unit = { state ->
        runOnUiThread { renderCallState(state) }
    }

    private var microphoneAction: (() -> Unit)? = null
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                microphoneAction?.invoke()
            } else {
                showToast(R.string.permissions_required)
            }
            microphoneAction = null
        }

    private var callAction: (() -> Unit)? = null
    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                callAction?.invoke()
            } else {
                Toast.makeText(this, R.string.call_permissions_required, Toast.LENGTH_SHORT).show()
            }
            callAction = null
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                shareCurrentLocation()
            } else {
                showToast(R.string.location_permission_needed)
            }
        }

    private val sessionListener: (IncomingPayload) -> Unit = { incoming ->
        runOnUiThread {
            when (incoming) {
                is IncomingPayload.Text -> addLiveMessage(incoming.text, false, "text", incoming.sender)
                is IncomingPayload.Transcript -> addLiveMessage("Voice text: ${incoming.text}", false, "transcript", incoming.sender)
                is IncomingPayload.Alert -> addLiveMessage("Alert: ${incoming.text}", false, "alert", incoming.sender)
                is IncomingPayload.Location -> addLiveMessage("Location: ${incoming.text}", false, "location", incoming.sender)
                is IncomingPayload.VoiceNote -> addLiveMessage(incoming.filePath, false, "voice", incoming.sender)
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
            updateTelemetryData()
            updateClearChatVisibility()
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

        callingBar = findViewById(R.id.callingBar)
        callingBarText = findViewById(R.id.callingBarText)
        callingBarEndBtn = findViewById(R.id.callingBarEndBtn)

        normalInputLayout = findViewById(R.id.normalInputLayout)
        recordingLayout = findViewById(R.id.recordingLayout)
        recordingDot = findViewById(R.id.recordingDot)
        recordingTimerText = findViewById(R.id.recordingTimerText)
        recordCancelBtn = findViewById(R.id.recordCancelBtn)
        recordSendBtn = findViewById(R.id.recordSendBtn)

        telemetryPanel = findViewById(R.id.telemetryPanel)
        telemetryMacAddress = findViewById(R.id.telemetryMacAddress)
        telemetryStats = findViewById(R.id.telemetryStats)

        callingBarEndBtn.setOnClickListener {
            CoreLinkCallSession.end()
        }

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        loadPersistedMessages()

        sendBtn.setOnClickListener { handleSendBtnClick() }
        recordCancelBtn.setOnClickListener { cancelVoiceRecording() }
        recordSendBtn.setOnClickListener { stopAndSendVoiceRecording() }
        statusText.setOnClickListener { toggleTelemetryPanel() }

        findViewById<android.view.View>(R.id.clearChatBtn).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear all chat history?")
                .setPositiveButton("Clear") { _, _ ->
                    ChatHistoryStore.clear(this)
                    messages.clear()
                    messages += ChatLogEntry(getString(R.string.chat_welcome_message), false, "text", System.currentTimeMillis())
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateClearChatVisibility()

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrEmpty()
                if (isEmpty) {
                    sendBtn.setIconResource(android.R.drawable.ic_btn_speak_now)
                } else {
                    sendBtn.setIconResource(android.R.drawable.ic_menu_send)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        micBtn.setOnClickListener { showVoiceToolsDialog() }
        connectBtn.setOnClickListener { ensurePermissionsAndConnect() }
        emergencyBtn.setOnClickListener { showEmergencyDialog() }
        callBtn.setOnClickListener { handleCallAction() }
        themeToggleBtn.setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        CoreLinkSession.addMessageListener(sessionListener)
        CoreLinkSession.addStateListener(stateListener)
        CoreLinkCallSession.addStateListener(callStateListener)
    }

    override fun onStop() {
        super.onStop()
        CoreLinkSession.removeMessageListener(sessionListener)
        CoreLinkSession.removeStateListener(stateListener)
        CoreLinkCallSession.removeStateListener(callStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        recorder?.release()
        adapter.cleanup()
    }

    private fun loadPersistedMessages() {
        messages.clear()
        val saved = ChatHistoryStore.load(this)
        if (saved.isEmpty()) {
            messages += ChatLogEntry(getString(R.string.chat_welcome_message), false, "text", System.currentTimeMillis())
        } else {
            messages += saved
        }
        adapter.notifyDataSetChanged()
        recyclerView.post { recyclerView.scrollToPosition(messages.lastIndex.coerceAtLeast(0)) }
    }

    private fun addLiveMessage(entry: ChatLogEntry) {
        messages += entry
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun addLiveMessage(content: String, isSent: Boolean, kind: String = "text", senderName: String? = null) {
        val finalSender = senderName ?: if (isSent) "Me" else (CoreLinkSession.connectedDeviceName ?: "Peer")
        addLiveMessage(ChatLogEntry(content, isSent, kind, System.currentTimeMillis(), finalSender))
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
                    updateClearChatVisibility()
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_error)
                    addLiveMessage(getString(R.string.connection_failed, error), false)
                    updateClearChatVisibility()
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
                    updateClearChatVisibility()
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = getString(R.string.chat_status_error)
                    addLiveMessage(getString(R.string.connection_failed, error), false)
                    updateClearChatVisibility()
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

    // --- UNIFIED METHODS ---

    private fun showVoiceToolsDialog() {
        val options = arrayOf("Dictate message (Speech to Text)", if (isRecording) "Stop & Send Voice Note" else "Start Voice Note Recording")
        MaterialAlertDialogBuilder(this)
            .setTitle("Voice Tools")
            .setItems(options) { _, which ->
                if (which == 0) {
                    ensureMicrophonePermission { startSpeechToText() }
                } else {
                    ensureMicrophonePermission {
                        if (isRecording) stopAndSendVoiceRecording() else startVoiceRecording()
                    }
                }
            }
            .show()
    }

    private fun ensureMicrophonePermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            microphoneAction = onGranted
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            showToast(R.string.voice_input_unavailable)
        }
    }

    private fun handleSendBtnClick() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            ensureMicrophonePermission { startVoiceRecording() }
        } else {
            sendTypedMessage()
        }
    }

    private var recordingBlinker: android.animation.ObjectAnimator? = null
    private var recordSeconds = 0
    private var recordTimerRunnable: Runnable? = null

    private fun startVoiceRecording() {
        if (isRecording) return
        normalInputLayout.visibility = android.view.View.GONE
        recordingLayout.visibility = android.view.View.VISIBLE

        voiceFile = File(cacheDir, "voice_note_temp.3gp")
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(voiceFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            startRecordingBlinker()
            startRecordingTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(R.string.voice_note_send_failed)
            cancelVoiceRecording()
        }
    }

    private fun cancelVoiceRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false

        stopRecordingBlinker()
        stopRecordingTimer()

        val file = voiceFile
        if (file != null && file.exists()) {
            file.delete()
        }
        voiceFile = null

        normalInputLayout.visibility = android.view.View.VISIBLE
        recordingLayout.visibility = android.view.View.GONE
    }

    private fun stopAndSendVoiceRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false

        stopRecordingBlinker()
        stopRecordingTimer()

        val file = voiceFile
        if (file != null && file.exists()) {
            if (CoreLinkSession.isConnected()) {
                val path = CoreLinkSession.sendVoiceNote(file)
                if (path != null) {
                    addLiveMessage(path, true, "voice")
                } else {
                    showToast(R.string.voice_note_send_failed)
                }
            } else {
                showToast(R.string.chat_offline_hint)
            }
        }
        voiceFile = null

        normalInputLayout.visibility = android.view.View.VISIBLE
        recordingLayout.visibility = android.view.View.GONE
    }

    private fun startRecordingBlinker() {
        recordingBlinker?.cancel()
        recordingBlinker = android.animation.ObjectAnimator.ofFloat(recordingDot, "alpha", 1f, 0.2f).apply {
            duration = 600
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopRecordingBlinker() {
        recordingBlinker?.cancel()
        recordingBlinker = null
        recordingDot.alpha = 1.0f
    }

    private fun startRecordingTimer() {
        recordSeconds = 0
        recordingTimerText.text = "00:00"
        recordTimerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordSeconds++
                    val m = recordSeconds / 60
                    val s = recordSeconds % 60
                    recordingTimerText.text = String.format("%02d:%02d", m, s)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(recordTimerRunnable!!, 1000)
    }

    private fun stopRecordingTimer() {
        recordTimerRunnable?.let { handler.removeCallbacks(it) }
        recordTimerRunnable = null
    }

    private fun toggleTelemetryPanel() {
        if (telemetryPanel.visibility == android.view.View.VISIBLE) {
            telemetryPanel.visibility = android.view.View.GONE
        } else {
            updateTelemetryData()
            telemetryPanel.visibility = android.view.View.VISIBLE
        }
    }

    private fun updateTelemetryData() {
        val session = CoreLinkSession
        val device = session.connectedDevice
        if (device != null) {
            telemetryMacAddress.text = "MAC: ${device.address}"
            telemetryStats.text = "TX: Enabled | RX: Enabled | Signal: Connected"
        } else {
            telemetryMacAddress.text = "MAC: N/A"
            telemetryStats.text = "TX: 0 Pkts | RX: 0 Pkts | Mode: Offline"
        }
    }

    private fun handleCallAction() {
        if (!CoreLinkSession.isConnected()) {
            showToast(R.string.chat_offline_hint)
            return
        }

        val phase = CoreLinkCallSession.state.phase
        if (phase != CallPhase.IDLE) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Active Call")
                .setMessage("Do you want to end the active voice call session?")
                .setPositiveButton("End Call") { _, _ -> CoreLinkCallSession.end() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        ensureCallPermissions {
            val device = CoreLinkSession.connectedDevice
            if (device == null) {
                showToast(R.string.chat_offline_hint)
                return@ensureCallPermissions
            }

            val options = arrayOf("Host Call Link", "Join Call Link")
            MaterialAlertDialogBuilder(this)
                .setTitle("Voice Call")
                .setItems(options) { _, which ->
                    val adapter = bluetoothAdapter ?: return@setItems
                    if (which == 0) {
                        CoreLinkCallSession.host(adapter) { error ->
                            runOnUiThread { Toast.makeText(this, getString(R.string.connection_failed, error), Toast.LENGTH_SHORT).show() }
                        }
                    } else {
                        CoreLinkCallSession.join(device) { error ->
                            runOnUiThread { Toast.makeText(this, getString(R.string.connection_failed, error), Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
                .show()
        }
    }

    private fun ensureCallPermissions(onGranted: () -> Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isEmpty()) {
            onGranted()
        } else {
            callAction = onGranted
            callPermissionLauncher.launch(denied.toTypedArray())
        }
    }

    private fun renderCallState(state: CallSessionState) {
        when (state.phase) {
            CallPhase.IDLE -> {
                callingBar.visibility = android.view.View.GONE
                handler.removeCallbacks(timerRunnable)
            }
            CallPhase.HOSTING -> {
                callingBar.visibility = android.view.View.VISIBLE
                callingBarText.text = getString(R.string.call_hosting_status)
                handler.removeCallbacks(timerRunnable)
            }
            CallPhase.JOINING -> {
                callingBar.visibility = android.view.View.VISIBLE
                callingBarText.text = getString(R.string.call_joining_status)
                handler.removeCallbacks(timerRunnable)
            }
            CallPhase.ACTIVE -> {
                callingBar.visibility = android.view.View.VISIBLE
                callStartedAt = state.startedAt ?: System.currentTimeMillis()
                handler.removeCallbacks(timerRunnable)
                handler.post(timerRunnable)
            }
        }
    }

    private fun showEmergencyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emergency_sender, null)
        val progressIndicator = dialogView.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.sosProgress)
        val sosButton = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.sosButton)
        val timerText = dialogView.findViewById<TextView>(R.id.sosTimerText)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.categoryChipGroup)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        var currentProgress = 0
        val vibrationHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator

        val runnable = object : Runnable {
            override fun run() {
                currentProgress += 50
                progressIndicator.progress = currentProgress
                val remainingSeconds = ((3000 - currentProgress) / 1000f)
                timerText.text = "HOLDING: ${String.format("%.1fs", remainingSeconds)}"

                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(android.os.VibrationEffect.createOneShot(30, 80))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(30)
                    }
                }

                if (currentProgress >= 3000) {
                    val payload = when (chipGroup.checkedChipId) {
                        R.id.chipMedical -> getString(R.string.emergency_medical_payload)
                        R.id.chipShelter -> getString(R.string.emergency_shelter_payload)
                        R.id.chipRelay -> getString(R.string.emergency_relay_payload)
                        else -> "General emergency assistance needed"
                    }
                    sendAlert(payload, R.string.emergency_medical_sent)

                    vibrator?.let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            it.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(500)
                        }
                    }

                    dialog.dismiss()
                } else {
                    vibrationHandler.postDelayed(this, 50)
                }
            }
        }

        sosButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    currentProgress = 0
                    progressIndicator.progress = 0
                    timerText.text = "HOLDING: 3.0s"
                    vibrationHandler.post(runnable)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    vibrationHandler.removeCallbacks(runnable)
                    currentProgress = 0
                    progressIndicator.progress = 0
                    timerText.text = "Released (Safeguard Reset)"
                    true
                }
                else -> false
            }
        }

        dialog.show()
    }

    private fun sendAlert(payload: String, successRes: Int) {
        if (CoreLinkSession.isConnected()) {
            CoreLinkSession.sendEmergency(payload)
            addLiveMessage("Emergency Alert: $payload", true)
        } else {
            showToast(R.string.chat_offline_hint)
        }
    }

    private fun handleLocationShare() {
        if (hasLocationPermission()) {
            shareCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun shareCurrentLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = latestKnownLocation(locationManager)
        if (location == null) {
            showToast(R.string.location_unavailable)
            return
        }

        val label = getString(
            R.string.location_label,
            "%.4f".format(location.latitude),
            "%.4f".format(location.longitude)
        )

        if (CoreLinkSession.isConnected()) {
            CoreLinkSession.sendLocation(label)
            addLiveMessage("Location Shared: $label", true)
        } else {
            showToast(R.string.chat_offline_hint)
        }
    }

    private fun latestKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun updateClearChatVisibility() {
        val clearChatBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.clearChatBtn) ?: return
        clearChatBtn.visibility = if (CoreLinkSession.isHost) android.view.View.VISIBLE else android.view.View.GONE
    }
}
