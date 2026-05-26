package com.example.corelink

import android.content.Context
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton

object EmergencyOverlayManager {

    @Volatile
    private var overlayView: View? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun show(context: Context, senderName: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (overlayView != null) {
                    dismiss()
                }

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val themeWrappedContext = android.view.ContextThemeWrapper(context, R.style.Theme_Corelink)
                val inflater = LayoutInflater.from(themeWrappedContext)
                val view = inflater.inflate(R.layout.layout_emergency_overlay, null)
                overlayView = view

                view.findViewById<TextView>(R.id.overlaySenderName).text = "Sender: $senderName"
                view.findViewById<TextView>(R.id.overlayMessage).text = message

                view.findViewById<MaterialButton>(R.id.overlayDismissBtn).setOnClickListener {
                    dismiss()
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm.addView(view, params)

                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, alertUri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }

                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(pattern, 0)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Overlay failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun dismiss() {
        Handler(Looper.getMainLooper()).post {
            try {
                ringtone?.stop()
                ringtone = null

                vibrator?.cancel()
                vibrator = null

                val view = overlayView
                if (view != null) {
                    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(view)
                    overlayView = null
                }
            } catch (_: Exception) {
            }
        }
    }
}
