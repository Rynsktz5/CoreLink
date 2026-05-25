package com.example.corelink

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale

class VoiceToolsActivity : AppCompatActivity() {

    private lateinit var transcriptText: TextView
    private lateinit var recordStatus: TextView
    private lateinit var themeToggleButton: ImageButton
    private lateinit var pulseView: android.view.View

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var voiceFile: File? = null
    private var isRecording = false
    private var pulseAnimator: ObjectAnimator? = null
    private var transcriptValue = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) showToast(R.string.permissions_required)
        }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            transcriptValue = spokenText.orEmpty()
            transcriptText.text = if (transcriptValue.isEmpty()) {
                getString(R.string.voice_empty_transcript)
            } else {
                transcriptValue
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_tools)

        transcriptText = findViewById(R.id.transcriptText)
        recordStatus = findViewById(R.id.recordStatus)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        pulseView = findViewById(R.id.voicePulse)

        findViewById<MaterialButton>(R.id.transcribeButton).setOnClickListener { startSpeechToText() }
        findViewById<MaterialButton>(R.id.sendTranscriptButton).setOnClickListener { sendTranscript() }
        findViewById<MaterialButton>(R.id.recordButton).setOnClickListener { toggleRecording() }
        findViewById<MaterialButton>(R.id.playButton).setOnClickListener { playRecording() }
        findViewById<MaterialButton>(R.id.sendVoiceButton).setOnClickListener { sendVoiceNote() }
        themeToggleButton.setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
        player?.release()
    }

    private fun startSpeechToText() {
        if (!hasMicPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

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

    private fun sendTranscript() {
        if (transcriptValue.isBlank()) {
            showToast(R.string.voice_empty_transcript_send)
            return
        }
        if (!CoreLinkSession.isConnected()) {
            showToast(R.string.chat_offline_hint)
            return
        }

        CoreLinkSession.sendTranscript(transcriptValue)
        recordStatus.text = getString(R.string.voice_transcript_sent)
    }

    private fun toggleRecording() {
        if (!hasMicPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        voiceFile = File(cacheDir, "voice_note.3gp")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(voiceFile?.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        recordStatus.text = getString(R.string.voice_recording_status)
        startPulse()
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        isRecording = false
        recordStatus.text = getString(R.string.voice_saved_status)
        stopPulse()
    }

    private fun playRecording() {
        val file = voiceFile
        if (file == null || !file.exists()) {
            showToast(R.string.voice_note_missing)
            return
        }

        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
        recordStatus.text = getString(R.string.voice_playback_status)
    }

    private fun sendVoiceNote() {
        val file = voiceFile
        if (file == null || !file.exists()) {
            showToast(R.string.voice_note_missing)
            return
        }
        if (!CoreLinkSession.isConnected()) {
            showToast(R.string.chat_offline_hint)
            return
        }

        if (CoreLinkSession.sendVoiceNote(file)) {
            recordStatus.text = getString(R.string.voice_note_sent)
        } else {
            showToast(R.string.voice_note_send_failed)
        }
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(pulseView, "alpha", 0.45f, 1f).apply {
            duration = 650
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseView.alpha = 1f
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
