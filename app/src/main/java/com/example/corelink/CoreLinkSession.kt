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

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isConnected(): Boolean = bluetooth.isConnected()

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

    fun startServer(
        adapter: BluetoothAdapter,
        onConnected: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        bluetooth.startServer(
            adapter = adapter,
            onConnected = {
                connectedDeviceName = "Nearby peer"
                startListening()
                notifyState()
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
        val deviceName = try {
            device.name ?: "Unknown device"
        } catch (_: SecurityException) {
            "Unknown device"
        }

        bluetooth.connectToDevice(
            device = device,
            onConnected = {
                connectedDeviceName = deviceName
                startListening()
                notifyState()
                onConnected(deviceName)
            },
            onError = onError
        )
    }

    fun sendText(text: String): Boolean = sendOutgoing(PREFIX_TEXT + text, ChatLogEntry(text, true, "text", now()))

    fun sendTranscript(text: String): Boolean =
        sendOutgoing(PREFIX_TRANSCRIPT + text, ChatLogEntry("Voice text: $text", true, "transcript", now()))

    fun sendEmergency(text: String): Boolean =
        sendOutgoing(PREFIX_ALERT + text, ChatLogEntry("Alert: $text", true, "alert", now()))

    fun sendLocation(label: String): Boolean =
        sendOutgoing(PREFIX_LOCATION + label, ChatLogEntry("Location: $label", true, "location", now()))

    fun sendVoiceNote(file: File): Boolean {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val payload = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return sendOutgoing(PREFIX_VOICE + payload, ChatLogEntry("Voice note sent", true, "voice", now()))
    }

    fun disconnect() {
        bluetooth.disconnect()
        connectedDeviceName = null
        listening = false
        notifyState()
    }

    private fun sendOutgoing(raw: String, entry: ChatLogEntry): Boolean {
        val sent = bluetooth.sendMessage(raw)
        if (sent) {
            appContext?.let { ChatHistoryStore.append(it, entry) }
        }
        return sent
    }

    private fun startListening() {
        if (listening) return
        listening = true
        bluetooth.listen(
            onMessage = { raw -> dispatch(raw) },
            onDisconnected = {
                listening = false
                connectedDeviceName = null
                notifyState()
            }
        )
    }

    private fun dispatch(raw: String) {
        val payload = when {
            raw.startsWith(PREFIX_ALERT) -> IncomingPayload.Alert(raw.removePrefix(PREFIX_ALERT))
            raw.startsWith(PREFIX_LOCATION) -> IncomingPayload.Location(raw.removePrefix(PREFIX_LOCATION))
            raw.startsWith(PREFIX_TRANSCRIPT) -> IncomingPayload.Transcript(raw.removePrefix(PREFIX_TRANSCRIPT))
            raw.startsWith(PREFIX_VOICE) -> IncomingPayload.VoiceNote(raw.removePrefix(PREFIX_VOICE))
            raw.startsWith(PREFIX_TEXT) -> IncomingPayload.Text(raw.removePrefix(PREFIX_TEXT))
            else -> IncomingPayload.Text(raw)
        }

        val entry = when (payload) {
            is IncomingPayload.Text -> ChatLogEntry(payload.text, false, "text", now())
            is IncomingPayload.Transcript -> ChatLogEntry("Voice text: ${payload.text}", false, "transcript", now())
            is IncomingPayload.Alert -> ChatLogEntry("Alert: ${payload.text}", false, "alert", now())
            is IncomingPayload.Location -> ChatLogEntry("Location: ${payload.text}", false, "location", now())
            is IncomingPayload.VoiceNote -> ChatLogEntry("Voice note received", false, "voice", now())
        }

        appContext?.let {
            ChatHistoryStore.append(it, entry)
            AppNotifications.showIncoming(it, "CoreLink", entry.content)
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

sealed class IncomingPayload {
    data class Text(val text: String) : IncomingPayload()
    data class Transcript(val text: String) : IncomingPayload()
    data class Alert(val text: String) : IncomingPayload()
    data class Location(val text: String) : IncomingPayload()
    data class VoiceNote(val payload: String) : IncomingPayload()
}
