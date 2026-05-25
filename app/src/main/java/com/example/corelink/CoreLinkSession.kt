package com.example.corelink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Base64
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

object CoreLinkSession {
    private const val PREFIX_TEXT = "TXT:"
    private const val PREFIX_ALERT = "ALERT:"
    private const val PREFIX_LOCATION = "LOC:"
    private const val PREFIX_TRANSCRIPT = "STT:"
    private const val PREFIX_VOICE = "VOICE:"

    private val bluetooth = BluetoothService()
    private val messageListeners = CopyOnWriteArraySet<(IncomingPayload) -> Unit>()
    private val stateListeners = CopyOnWriteArraySet<(SessionState) -> Unit>()

    @Volatile
    private var listening = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var connectedDeviceName: String? = null
        private set

    @Volatile
    var isHost: Boolean = false
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isConnected(): Boolean = bluetooth.isConnected()

    val connectedDevice: BluetoothDevice?
        get() = if (isConnected()) bluetooth.getConnectedDevice() else null

    fun addMessageListener(listener: (IncomingPayload) -> Unit) {
        messageListeners += listener
    }

    fun removeMessageListener(listener: (IncomingPayload) -> Unit) {
        messageListeners -= listener
    }

    fun addStateListener(listener: (SessionState) -> Unit) {
        stateListeners += listener
        listener(SessionState(isConnected(), connectedDeviceName))
    }

    fun removeStateListener(listener: (SessionState) -> Unit) {
        stateListeners -= listener
    }

    private fun updateConnectedDeviceNames() {
        val devices = bluetooth.getConnectedDevices()
        connectedDeviceName = if (isHost) {
            if (devices.isEmpty()) {
                "Waiting for peers..."
            } else {
                devices.joinToString(", ") { device ->
                    try {
                        device.name ?: "Nearby peer"
                    } catch (_: SecurityException) {
                        "Nearby peer"
                    }
                }
            }
        } else {
            val dev = bluetooth.getConnectedDevice()
            try {
                dev?.name ?: "Nearby peer"
            } catch (_: SecurityException) {
                "Nearby peer"
            }
        }
    }

    fun startServer(
        adapter: BluetoothAdapter,
        onConnected: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        isHost = true
        bluetooth.startServer(
            adapter = adapter,
            onConnected = {
                updateConnectedDeviceNames()
                startListening()
                notifyState()
                appContext?.let { CoreLinkForegroundService.updateServiceState(it) }
                onConnected(connectedDeviceName ?: "Nearby peer")
            },
            onError = onError
        )
    }

    fun connectToDevice(
        device: BluetoothDevice,
        onConnected: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        isHost = false
        val deviceName = try {
            device.name ?: "Unknown device"
        } catch (_: SecurityException) {
            "Unknown device"
        }

        bluetooth.connectToDevice(
            device = device,
            onConnected = {
                updateConnectedDeviceNames()
                startListening()
                notifyState()
                appContext?.let { CoreLinkForegroundService.updateServiceState(it) }
                onConnected(deviceName)
            },
            onError = onError
        )
    }

    fun sendText(text: String): Boolean = sendOutgoing(PREFIX_TEXT + text, ChatLogEntry(text, true, "text", now(), "Me"))

    fun sendTranscript(text: String): Boolean =
        sendOutgoing(PREFIX_TRANSCRIPT + text, ChatLogEntry("Voice text: $text", true, "transcript", now(), "Me"))

    fun sendEmergency(text: String): Boolean =
        sendOutgoing(PREFIX_ALERT + text, ChatLogEntry("Alert: $text", true, "alert", now(), "Me"))

    fun sendLocation(label: String): Boolean =
        sendOutgoing(PREFIX_LOCATION + label, ChatLogEntry("Location: $label", true, "location", now(), "Me"))

    fun sendVoiceNote(file: File): String? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val payload = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val context = appContext ?: return null
        val persistentPath = saveSentVoiceNoteFile(context, file) ?: return null
        
        val entry = ChatLogEntry(persistentPath, true, "voice", now(), "Me")
        val sent = sendOutgoing(PREFIX_VOICE + payload, entry)
        return if (sent) persistentPath else null
    }

    private fun saveVoiceNoteFile(context: Context, base64Data: String): String? {
        return try {
            val dir = File(context.filesDir, "voice_notes")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "voice_received_${System.currentTimeMillis()}.3gp")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveSentVoiceNoteFile(context: Context, tempFile: File): String? {
        return try {
            val dir = File(context.filesDir, "voice_notes")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "voice_sent_${System.currentTimeMillis()}.3gp")
            tempFile.copyTo(file, overwrite = true)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun disconnect() {
        bluetooth.disconnect()
        connectedDeviceName = null
        isHost = false
        listening = false
        notifyState()
        appContext?.let { CoreLinkForegroundService.updateServiceState(it) }
    }

    private fun getSenderPrefixedRaw(raw: String): String {
        val localName = try {
            BluetoothAdapter.getDefaultAdapter()?.name ?: "Me"
        } catch (_: SecurityException) {
            "Me"
        }

        val prefix = when {
            raw.startsWith(PREFIX_ALERT) -> PREFIX_ALERT
            raw.startsWith(PREFIX_LOCATION) -> PREFIX_LOCATION
            raw.startsWith(PREFIX_TRANSCRIPT) -> PREFIX_TRANSCRIPT
            raw.startsWith(PREFIX_VOICE) -> PREFIX_VOICE
            raw.startsWith(PREFIX_TEXT) -> PREFIX_TEXT
            else -> ""
        }
        val body = raw.removePrefix(prefix)
        return prefix + "$localName|$body"
    }

    private fun sendOutgoing(raw: String, entry: ChatLogEntry): Boolean {
        val prefixedRaw = getSenderPrefixedRaw(raw)
        val sent = bluetooth.sendMessage(prefixedRaw)
        if (sent) {
            appContext?.let { ChatHistoryStore.append(it, entry) }
        }
        return sent
    }

    private fun startListening() {
        if (listening) return
        listening = true
        bluetooth.listen(
            onMessage = { raw, session -> dispatch(raw, session) },
            onDisconnected = {
                updateConnectedDeviceNames()
                if (bluetooth.getConnectedDevices().isEmpty()) {
                    listening = false
                    connectedDeviceName = null
                    isHost = false
                }
                notifyState()
                appContext?.let { CoreLinkForegroundService.updateServiceState(it) }
            }
        )
    }

    private fun dispatch(raw: String, session: BluetoothService.ConnectionSession) {
        if (isHost) {
            bluetooth.relayMessage(raw, session)
        }

        val prefix = when {
            raw.startsWith(PREFIX_ALERT) -> PREFIX_ALERT
            raw.startsWith(PREFIX_LOCATION) -> PREFIX_LOCATION
            raw.startsWith(PREFIX_TRANSCRIPT) -> PREFIX_TRANSCRIPT
            raw.startsWith(PREFIX_VOICE) -> PREFIX_VOICE
            raw.startsWith(PREFIX_TEXT) -> PREFIX_TEXT
            else -> ""
        }
        val body = raw.removePrefix(prefix)

        val idx = body.indexOf('|')
        val sender = if (idx != -1) body.substring(0, idx) else {
            try {
                session.socket.remoteDevice.name ?: "Peer"
            } catch (_: SecurityException) {
                "Peer"
            }
        }
        val content = if (idx != -1) body.substring(idx + 1) else body

        val payload = when (prefix) {
            PREFIX_ALERT -> IncomingPayload.Alert(content, sender)
            PREFIX_LOCATION -> IncomingPayload.Location(content, sender)
            PREFIX_TRANSCRIPT -> IncomingPayload.Transcript(content, sender)
            PREFIX_VOICE -> {
                val context = appContext ?: error("Context missing")
                val filePath = saveVoiceNoteFile(context, content) ?: "voice_error"
                IncomingPayload.VoiceNote(filePath, sender)
            }
            else -> IncomingPayload.Text(content, sender)
        }

        val entry = when (payload) {
            is IncomingPayload.Text -> ChatLogEntry(payload.text, false, "text", now(), sender)
            is IncomingPayload.Transcript -> ChatLogEntry("Voice text: ${payload.text}", false, "transcript", now(), sender)
            is IncomingPayload.Alert -> ChatLogEntry("Alert: ${payload.text}", false, "alert", now(), sender)
            is IncomingPayload.Location -> ChatLogEntry("Location: ${payload.text}", false, "location", now(), sender)
            is IncomingPayload.VoiceNote -> ChatLogEntry(payload.filePath, false, "voice", now(), sender)
        }

        appContext?.let { context ->
            ChatHistoryStore.append(context, entry)
            if (payload is IncomingPayload.Alert) {
                AppNotifications.showEmergency(context, "CRITICAL ALERT: $sender", content)
                EmergencyOverlayManager.show(context, sender, content)
            } else {
                AppNotifications.showIncoming(context, sender, entry.content)
            }
        }

        messageListeners.forEach { it(payload) }
    }

    private fun notifyState() {
        val state = SessionState(isConnected(), connectedDeviceName)
        stateListeners.forEach { it(state) }
    }

    private fun now(): Long = System.currentTimeMillis()
}

data class SessionState(val connected: Boolean, val deviceName: String?)

sealed class IncomingPayload(open val sender: String?) {
    data class Text(val text: String, override val sender: String?) : IncomingPayload(sender)
    data class Transcript(val text: String, override val sender: String?) : IncomingPayload(sender)
    data class Alert(val text: String, override val sender: String?) : IncomingPayload(sender)
    data class Location(val text: String, override val sender: String?) : IncomingPayload(sender)
    data class VoiceNote(val filePath: String, override val sender: String?) : IncomingPayload(sender)
}
