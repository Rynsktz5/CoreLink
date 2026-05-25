package com.example.corelink

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ChatLogEntry(
    val content: String,
    val isSent: Boolean,
    val kind: String,
    val timestamp: Long
)

object ChatHistoryStore {
    private const val PREFS_NAME = "corelink_chat_history"
    private const val KEY_MESSAGES = "messages"
    private const val MAX_MESSAGES = 200

    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<ChatLogEntry>>() {}.type

    fun load(context: Context): MutableList<ChatLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<ChatLogEntry>>(raw, listType) }.getOrDefault(mutableListOf())
    }

    fun append(context: Context, entry: ChatLogEntry) {
        val items = load(context)
        items += entry
        while (items.size > MAX_MESSAGES) {
            items.removeAt(0)
        }
        save(context, items)
    }

    fun clear(context: Context) {
        save(context, mutableListOf())
    }

    private fun save(context: Context, entries: MutableList<ChatLogEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MESSAGES, gson.toJson(entries)).apply()
    }
}
