package com.example.corelink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

object CoreLinkCallSession {

    private val listeners = CopyOnWriteArraySet<(CallSessionState) -> Unit>()

    @Volatile
    private var audio: AudioCallService? = null

    @Volatile
    var state = CallSessionState()
        private set

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (audio == null) {
            audio = AudioCallService(context.applicationContext)
        }
    }

    fun addStateListener(listener: (CallSessionState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeStateListener(listener: (CallSessionState) -> Unit) {
        listeners -= listener
    }

    fun host(adapter: BluetoothAdapter, onError: (String) -> Unit) {
        val service = audio ?: return
        updateState(CallPhase.HOSTING, null, null)
        service.startServer(
            adapter = adapter,
            onConnected = {
                updateState(CallPhase.ACTIVE, "Nearby peer", System.currentTimeMillis())
            },
            onDisconnected = {
                updateState(CallPhase.IDLE, null, null)
            },
            onError = { error ->
                updateState(CallPhase.IDLE, null, null)
                onError(error)
            }
        )
    }

    fun join(device: BluetoothDevice, onError: (String) -> Unit) {
        val service = audio ?: return
        val deviceName = try {
            device.name ?: "Unknown device"
        } catch (_: SecurityException) {
            "Unknown device"
        }

        updateState(CallPhase.JOINING, deviceName, null)
        service.connectToDevice(
            device = device,
            onConnected = {
                updateState(CallPhase.ACTIVE, deviceName, System.currentTimeMillis())
            },
            onDisconnected = {
                updateState(CallPhase.IDLE, null, null)
            },
            onError = { error ->
                updateState(CallPhase.IDLE, null, null)
                onError(error)
            }
        )
    }

    fun end() {
        audio?.disconnect()
        updateState(CallPhase.IDLE, null, null)
    }

    private fun updateState(phase: CallPhase, peerName: String?, startedAt: Long?) {
        state = CallSessionState(phase, peerName, startedAt)
        listeners.forEach { it(state) }
        appContext?.let { CoreLinkForegroundService.updateServiceState(it) }
    }
}

data class CallSessionState(
    val phase: CallPhase = CallPhase.IDLE,
    val peerName: String? = null,
    val startedAt: Long? = null
)

enum class CallPhase {
    IDLE,
    HOSTING,
    JOINING,
    ACTIVE
}
