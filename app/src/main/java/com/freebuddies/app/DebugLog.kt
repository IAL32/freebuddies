package com.freebuddies.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogTag { APP, BUDS, RX, TX }

data class LogEntry(
    val id: Long,
    val tag: LogTag,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DebugLog {
    private const val MAX_ENTRIES = 500
    private const val TAG = "FreeBuds"
    private var nextId = 0L

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    @Synchronized
    private fun append(tag: LogTag, message: String) {
        _entries.value = (_entries.value + LogEntry(nextId++, tag, message)).takeLast(MAX_ENTRIES)
    }

    fun d(tag: LogTag, message: String) {
        append(tag, message)
        Log.d(TAG, "(${tag.name.lowercase()}) $message")
    }

    fun w(tag: LogTag, message: String) {
        append(tag, message)
        Log.w(TAG, "(${tag.name.lowercase()}) $message")
    }

    fun e(tag: LogTag, message: String, throwable: Throwable? = null) {
        append(tag, message)
        if (throwable != null) {
            Log.e(TAG, "(${tag.name.lowercase()}) $message", throwable)
        } else {
            Log.e(TAG, "(${tag.name.lowercase()}) $message")
        }
    }
}
