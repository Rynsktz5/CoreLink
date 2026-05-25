package com.example.corelink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class BluetoothService {

    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    class ConnectionSession(
        val socket: BluetoothSocket,
        val input: InputStream,
        val output: OutputStream,
        @Volatile var listening: Boolean = false
    )

    private val activeSessions = CopyOnWriteArrayList<ConnectionSession>()

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var isServerRunning = false

    @Volatile
    private var onMessageCallback: ((String, ConnectionSession) -> Unit)? = null

    @Volatile
    private var onDisconnectedCallback: (() -> Unit)? = null

    fun isConnected(): Boolean = activeSessions.isNotEmpty()

    fun getConnectedDevice(): BluetoothDevice? =
        activeSessions.firstOrNull()?.socket?.remoteDevice

    fun getConnectedDevices(): List<BluetoothDevice> =
        activeSessions.map { it.socket.remoteDevice }

    fun startServer(
        adapter: BluetoothAdapter,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()
        isServerRunning = true
        Thread {
            try {
                serverSocket = openServerSocket(adapter)
                while (isServerRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    Thread {
                        try {
                            val input = clientSocket.inputStream
                            val output = clientSocket.outputStream
                            val session = ConnectionSession(clientSocket, input, output)
                            activeSessions.add(session)

                            onConnected()

                            // Start listening to this session
                            listenToSession(session)
                        } catch (e: Exception) {
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }.start()
                }
            } catch (e: Exception) {
                if (isServerRunning) {
                    onError(e.message ?: "Unable to host Bluetooth connection")
                }
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
                    val clientSocket = candidate()
                    clientSocket.connect()
                    
                    val input = clientSocket.inputStream
                    val output = clientSocket.outputStream
                    val session = ConnectionSession(clientSocket, input, output)
                    activeSessions.add(session)
                    
                    onConnected()
                    
                    // Start listening as a client
                    listenToSession(session)
                    return@Thread
                } catch (e: Exception) {
                    lastError = e
                }
            }

            onError(lastError?.message ?: "Unable to join Bluetooth connection")
        }.start()
    }

    private fun listenToSession(session: ConnectionSession) {
        session.listening = true
        try {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(session.input, Charsets.UTF_8))
            while (session.listening && session.socket.isConnected && !Thread.currentThread().isInterrupted) {
                val line = reader.readLine() ?: break
                if (line.isNotEmpty()) {
                    onMessageCallback?.invoke(line, session)
                }
            }
        } catch (_: Exception) {
        } finally {
            session.listening = false
            activeSessions.remove(session)
            try { session.socket.close() } catch (_: Exception) {}
            onDisconnectedCallback?.invoke()
        }
    }

    fun listen(onMessage: (String, ConnectionSession) -> Unit, onDisconnected: (() -> Unit)? = null) {
        onMessageCallback = onMessage
        onDisconnectedCallback = onDisconnected
    }

    fun sendMessage(message: String): Boolean {
        if (activeSessions.isEmpty()) return false
        var sentAny = false
        for (session in activeSessions) {
            try {
                Thread {
                    try {
                        session.output.write((message + "\n").toByteArray())
                        session.output.flush()
                    } catch (_: Exception) {
                    }
                }.start()
                sentAny = true
            } catch (_: Exception) {
            }
        }
        return sentAny
    }

    fun relayMessage(message: String, excludeSession: ConnectionSession) {
        for (session in activeSessions) {
            if (session == excludeSession) continue
            try {
                Thread {
                    try {
                        session.output.write((message + "\n").toByteArray())
                        session.output.flush()
                    } catch (_: Exception) {
                    }
                }.start()
            } catch (_: Exception) {
            }
        }
    }

    fun disconnect() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (session in activeSessions) {
            session.listening = false
            try { session.input.close() } catch (_: Exception) {}
            try { session.output.close() } catch (_: Exception) {}
            try { session.socket.close() } catch (_: Exception) {}
        }
        activeSessions.clear()
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
}
