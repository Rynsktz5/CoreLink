package com.example.corelink

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class EmergencyActivity : AppCompatActivity() {

    private lateinit var alertStatus: TextView
    private lateinit var locationText: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                shareCurrentLocation()
            } else {
                showToast(R.string.location_permission_needed)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        alertStatus = findViewById(R.id.alertStatus)
        locationText = findViewById(R.id.locationText)

        findViewById<ImageButton>(R.id.themeToggleButton).setOnClickListener {
            ThemePrefs.toggle(this)
            recreate()
        }

        findViewById<MaterialButton>(R.id.medicalAlertButton).setOnClickListener {
            sendAlert(getString(R.string.emergency_medical_payload), R.string.emergency_medical_sent)
        }

        findViewById<MaterialButton>(R.id.shelterAlertButton).setOnClickListener {
            sendAlert(getString(R.string.emergency_shelter_payload), R.string.emergency_shelter_sent)
        }

        findViewById<MaterialButton>(R.id.relayAlertButton).setOnClickListener {
            sendAlert(getString(R.string.emergency_relay_payload), R.string.emergency_relay_sent)
        }

        findViewById<MaterialButton>(R.id.locationAlertButton).setOnClickListener {
            if (hasLocationPermission()) {
                shareCurrentLocation()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun sendAlert(payload: String, successRes: Int) {
        animateAlert()
        if (CoreLinkSession.isConnected()) {
            CoreLinkSession.sendEmergency(payload)
        }
        alertStatus.text = getString(successRes)
    }

    private fun shareCurrentLocation() {
        val locationManager = getSystemService(LocationManager::class.java) ?: run {
            showToast(R.string.location_unavailable)
            return
        }

        val location = latestKnownLocation(locationManager)
        if (location == null) {
            locationText.text = getString(R.string.location_unavailable)
            return
        }

        val label = getString(
            R.string.location_label,
            "%.4f".format(location.latitude),
            "%.4f".format(location.longitude)
        )
        locationText.text = label
        if (CoreLinkSession.isConnected()) {
            CoreLinkSession.sendLocation(label)
            alertStatus.text = getString(R.string.location_sent_status)
        }
        animateAlert()
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun animateAlert() {
        ObjectAnimator.ofFloat(findViewById(R.id.alertHero), "scaleX", 1f, 1.05f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(findViewById(R.id.alertHero), "scaleY", 1f, 1.05f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator()
            start()
        }
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
