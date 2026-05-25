package com.example.corelink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateBluetoothButton()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                requestBluetoothEnable()
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.enableBluetoothButton).setOnClickListener {
            if (hasBluetoothPermissions()) {
                requestBluetoothEnable()
            } else {
                permissionLauncher.launch(requiredPermissions())
            }
        }

        findViewById<MaterialButton>(R.id.startChatButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.deviceRadarButton).setOnClickListener {
            startActivity(Intent(this, DeviceDiscoveryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.voiceLabButton).setOnClickListener {
            startActivity(Intent(this, VoiceToolsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.emergencyCenterButton).setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.callDeckButton).setOnClickListener {
            startActivity(Intent(this, CallActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.sessionManagerButton).setOnClickListener {
            startActivity(Intent(this, SessionManagerActivity::class.java))
        }

        findViewById<ImageButton>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        updateBluetoothButton()
    }

    override fun onResume() {
        super.onResume()
        updateBluetoothButton()
    }

    private fun requestBluetoothEnable() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (adapter.isEnabled) {
            updateBluetoothButton()
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun updateBluetoothButton() {
        val button = findViewById<MaterialButton>(R.id.enableBluetoothButton)
        val enabled = bluetoothAdapter?.isEnabled == true
        button.visibility = if (enabled) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions.toTypedArray()
    }
}
