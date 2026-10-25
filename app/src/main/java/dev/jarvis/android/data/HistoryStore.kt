package dev.jarvis.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ChatMessage(val role: String, val text: String, val createdAt: Long)

class HistoryStore(context: Context) : SQLiteOpenHelper(context, "jarvis_history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun append(role: String, text: String) {
        require(role in setOf("user", "assistant"))
        if (text.isBlank()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("messages", null, ContentValues().apply {
                put("role", role); put("text", text.take(16000)); put("created_at", System.currentTimeMillis())
            })
            db.execSQL("DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY id DESC LIMIT 500)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun recent(limit: Int = 80): List<ChatMessage> {
        require(limit in 1..500)
        val result = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery("SELECT role, text, created_at FROM messages ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) result.add(ChatMessage(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
        }
        return result.reversed()
    }
    fun clear() { writableDatabase.delete("messages", null, null) }
}
