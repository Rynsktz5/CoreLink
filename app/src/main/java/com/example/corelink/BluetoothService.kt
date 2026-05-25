package com.example.corelink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothService {

    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var listening = false

    fun isConnected(): Boolean = socket?.isConnected == true

    fun startServer(
        adapter: BluetoothAdapter,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()
        Thread {
            try {
                serverSocket = openServerSocket(adapter)
                socket = serverSocket?.accept()
                serverSocket?.close()
                setupStreams()
                onConnected()
            } catch (e: Exception) {
                onError(e.message ?: "Unable to host Bluetooth connection")
            }
        }.start()
    }

    fun connectToDevice(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()
        Thread {
            var lastError: Exception? = null
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: Exception) {
            }

            for (candidate in socketFactories(device)) {
                try {
                    socket = candidate()
                    socket?.connect()
                    setupStreams()
                    onConnected()
                    return@Thread
                } catch (e: Exception) {
                    lastError = e
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    socket = null
                }
            }

            onError(lastError?.message ?: "Unable to join Bluetooth connection")
        }.start()
    }

    private fun openServerSocket(adapter: BluetoothAdapter): BluetoothServerSocket {
        return try {
            adapter.listenUsingInsecureRfcommWithServiceRecord("CoreLink", uuid)
        } catch (_: Exception) {
            adapter.listenUsingRfcommWithServiceRecord("CoreLink", uuid)
        }
    }

    private fun socketFactories(device: BluetoothDevice): List<() -> BluetoothSocket> {
        return listOf(
            { device.createInsecureRfcommSocketToServiceRecord(uuid) },
            { device.createRfcommSocketToServiceRecord(uuid) },
            {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                @Suppress("UNCHECKED_CAST")
                method.invoke(device, 1) as BluetoothSocket
            }
        )
    }

    private fun setupStreams() {
        val activeSocket = socket ?: error("Socket missing")
        input = activeSocket.inputStream
        output = activeSocket.outputStream
    }

    fun sendMessage(message: String): Boolean {
        val stream = output ?: return false
        return try {
            Thread {
                try {
                    stream.write((message + "\n").toByteArray())
                    stream.flush()
                } catch (_: Exception) {
                }
            }.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun listen(onMessage: (String) -> Unit, onDisconnected: (() -> Unit)? = null) {
        if (listening || input == null) return
        listening = true
        Thread {
            try {
                val buffer = ByteArray(1024)
                while (isConnected()) {
                    val bytes = input?.read(buffer) ?: -1
                    if (bytes <= 0) break
                    val msg = String(buffer, 0, bytes).trim()
                    if (msg.isNotEmpty()) {
                        onMessage(msg)
                    }
                }
            } catch (_: Exception) {
            } finally {
                listening = false
                onDisconnected?.invoke()
            }
        }.start()
    }

    fun disconnect() {
        listening = false
        try {
            input?.close()
        } catch (_: Exception) {
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        input = null
        output = null
        socket = null
        serverSocket = null
    }
}
