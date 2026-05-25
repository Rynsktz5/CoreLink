package com.example.corelink

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SessionManagerActivity : AppCompatActivity() {

    private lateinit var sessionStatus: TextView
    private lateinit var sessionPeer: TextView
    private lateinit var historyCount: TextView

    private val stateListener: (SessionState) -> Unit = { state ->
        runOnUiThread { renderState(state) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_manager)

        sessionStatus = findViewById(R.id.sessionStatus)
        sessionPeer = findViewById(R.id.sessionPeer)
        historyCount = findViewById(R.id.historyCount)

        findViewById<MaterialButton>(R.id.openRadarButton).setOnClickListener {
            startActivity(Intent(this, DeviceDiscoveryActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.openChatButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener {
            CoreLinkSession.disconnect()
        }
        findViewById<MaterialButton>(R.id.clearHistoryButton).setOnClickListener {
            ChatHistoryStore.clear(this)
            updateHistoryCount()
        }
        findViewById<MaterialButton>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        updateHistoryCount()
    }

    override fun onStart() {
        super.onStart()
        CoreLinkSession.addStateListener(stateListener)
    }

    override fun onStop() {
        super.onStop()
        CoreLinkSession.removeStateListener(stateListener)
    }

    private fun renderState(state: SessionState) {
        sessionStatus.text = if (state.connected) {
            getString(R.string.session_connected)
        } else {
            getString(R.string.session_idle)
        }
        sessionPeer.text = getString(
            R.string.session_peer_value,
            state.deviceName ?: getString(R.string.session_peer_none)
        )
    }

    private fun updateHistoryCount() {
        val count = ChatHistoryStore.load(this).size
        historyCount.text = getString(R.string.session_history_value, count)
    }
}
