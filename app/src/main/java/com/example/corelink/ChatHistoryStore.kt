package com.example.corelink

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ChatLogEntry(
    val content: String,
    val isSent: Boolean,
    val kind: String,
    val timestamp: Long,
    val sender: String? = null
)

object ChatHistoryStore {
    private const val DATABASE_NAME = "corelink_chat.db"
    private const val DATABASE_VERSION = 2
    private const val TABLE_NAME = "chats"

    private const val COL_ID = "id"
    private const val COL_CONTENT = "content"
    private const val COL_IS_SENT = "is_sent"
    private const val COL_KIND = "kind"
    private const val COL_TIMESTAMP = "timestamp"
    private const val COL_SENDER = "sender"

    private const val MAX_MESSAGES = 1000

    private class ChatDbHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_NAME (" +
                        "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "$COL_CONTENT TEXT NOT NULL, " +
                        "$COL_IS_SENT INTEGER NOT NULL, " +
                        "$COL_KIND TEXT NOT NULL, " +
                        "$COL_TIMESTAMP INTEGER NOT NULL, " +
                        "$COL_SENDER TEXT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_SENDER TEXT")
            }
        }
    }

    @Volatile
    private var helper: ChatDbHelper? = null

    @Synchronized
    private fun getHelper(context: Context): ChatDbHelper {
        if (helper == null) {
            helper = ChatDbHelper(context.applicationContext)
        }
        return helper!!
    }

    fun load(context: Context): MutableList<ChatLogEntry> {
        val list = mutableListOf<ChatLogEntry>()
        try {
            val db = getHelper(context).readableDatabase
            val cursor = db.query(
                TABLE_NAME,
                arrayOf(COL_CONTENT, COL_IS_SENT, COL_KIND, COL_TIMESTAMP, COL_SENDER),
                null, null, null, null,
                "$COL_ID ASC"
            )
            cursor.use {
                val contentIdx = it.getColumnIndexOrThrow(COL_CONTENT)
                val isSentIdx = it.getColumnIndexOrThrow(COL_IS_SENT)
                val kindIdx = it.getColumnIndexOrThrow(COL_KIND)
                val timestampIdx = it.getColumnIndexOrThrow(COL_TIMESTAMP)
                val senderIdx = it.getColumnIndex(COL_SENDER)

                while (it.moveToNext()) {
                    val senderVal = if (senderIdx != -1) it.getString(senderIdx) else null
                    list.add(
                        ChatLogEntry(
                            content = it.getString(contentIdx),
                            isSent = it.getInt(isSentIdx) == 1,
                            kind = it.getString(kindIdx),
                            timestamp = it.getLong(timestampIdx),
                            sender = senderVal
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun append(context: Context, entry: ChatLogEntry) {
        try {
            val db = getHelper(context).writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put(COL_CONTENT, entry.content)
                    put(COL_IS_SENT, if (entry.isSent) 1 else 0)
                    put(COL_KIND, entry.kind)
                    put(COL_TIMESTAMP, entry.timestamp)
                    put(COL_SENDER, entry.sender)
                }
                db.insert(TABLE_NAME, null, values)

                val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
                var count = 0
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
                cursor.close()

                if (count > MAX_MESSAGES) {
                    val deleteLimit = count - MAX_MESSAGES
                    db.execSQL("DELETE FROM $TABLE_NAME WHERE $COL_ID IN (SELECT $COL_ID FROM $TABLE_NAME ORDER BY $COL_ID ASC LIMIT $deleteLimit)")
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            val db = getHelper(context).writableDatabase
            db.delete(TABLE_NAME, null, null)
        } catch (_: Exception) {
        }
    }
}
