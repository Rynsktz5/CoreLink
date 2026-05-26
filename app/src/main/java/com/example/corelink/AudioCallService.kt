package com.example.corelink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioCallService(private val context: Context) {

    private val uuid = UUID.fromString("9f4e9d18-8f62-4ac1-8c65-e6e437f69b50")

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    private val active = AtomicBoolean(false)

    fun startServer(
        adapter: BluetoothAdapter,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()
        Thread {
            try {
                serverSocket = openServerSocket(adapter)
                socket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null
                setupStreams()
                startAudioLoops(onDisconnected)
                onConnected()
            } catch (e: Exception) {
                disconnect()
                onError(e.message ?: "Unable to host call link")
            }
        }.start()
    }

    fun connectToDevice(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
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
                    startAudioLoops(onDisconnected)
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

            disconnect()
            onError(lastError?.message ?: "Unable to join call link")
        }.start()
    }

    private fun openServerSocket(adapter: BluetoothAdapter): BluetoothServerSocket {
        return try {
            adapter.listenUsingInsecureRfcommWithServiceRecord("CoreLinkAudio", uuid)
        } catch (_: Exception) {
            adapter.listenUsingRfcommWithServiceRecord("CoreLinkAudio", uuid)
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

    private fun startAudioLoops(onDisconnected: () -> Unit) {
        val source = input ?: error("Input stream missing")
        val sink = output ?: error("Output stream missing")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        active.set(true)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val disconnectOnce = AtomicBoolean(false)
        val handleDisconnect = {
            if (disconnectOnce.compareAndSet(false, true)) {
                disconnect()
                onDisconnected()
            }
        }

        val packetSize = 640 // 20ms of 16-bit mono voice audio at 16kHz

        // Recording Thread
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufferSize, 2048)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                handleDisconnect()
                return@Thread
            }

            val aec = if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                android.media.audiofx.AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            } else null

            val ns = if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                android.media.audiofx.NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            } else null

            val buffer = ByteArray(packetSize)
            try {
                recorder.startRecording()
                while (active.get() && socket?.isConnected == true) {
                    var bytesRead = 0
                    while (bytesRead < packetSize && active.get() && socket?.isConnected == true) {
                        val count = recorder.read(buffer, bytesRead, packetSize - bytesRead)
                        if (count < 0) {
                            throw java.io.IOException("AudioRecord read error: $count")
                        }
                        bytesRead += count
                    }
                    if (bytesRead == packetSize) {
                        sink.write(buffer, 0, packetSize)
                        sink.flush()
                    }
                }
            } catch (_: Exception) {
                if (active.get()) handleDisconnect()
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                aec?.release()
                ns?.release()
            }
        }.start()

        // Playback Thread
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufferSize, 2048)

            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                handleDisconnect()
                return@Thread
            }

            val buffer = ByteArray(packetSize)
            try {
                track.play()
                while (active.get() && socket?.isConnected == true) {
                    readFully(source, buffer, packetSize)
                    track.write(buffer, 0, packetSize)
                }
            } catch (_: Exception) {
                if (active.get()) handleDisconnect()
            } finally {
                runCatching { track.stop() }
                track.release()
                if (active.get()) handleDisconnect()
            }
        }.start()
    }

    private fun readFully(inputStream: java.io.InputStream, buffer: ByteArray, length: Int) {
        var bytesRead = 0
        while (bytesRead < length) {
            val result = inputStream.read(buffer, bytesRead, length - bytesRead)
            if (result == -1) {
                throw java.io.IOException("EOF reached")
            }
            bytesRead += result
        }
    }

    fun disconnect() {
        active.set(false)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false

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

    companion object {
        private const val SAMPLE_RATE = 16_000
    }
}
