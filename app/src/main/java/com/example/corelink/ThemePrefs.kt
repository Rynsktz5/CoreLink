package com.example.corelink

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemePrefs {
    private const val PREFS_NAME = "corelink_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    fun apply(context: Context) {
        val isDark = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun isDarkMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
    }

    fun toggle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(KEY_DARK_MODE, false)
        prefs.edit().putBoolean(KEY_DARK_MODE, next).apply()
        apply(context)
        return next
    }
}
