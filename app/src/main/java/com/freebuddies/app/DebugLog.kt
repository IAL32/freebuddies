package com.freebuddies.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class LogTag { APP, BUDS, RX, TX }

data class LogEntry(
    val id: Long,
    val tag: LogTag,
    val message: String,
    val time: String,
)

object DebugLog {
    private const val MAX_ENTRIES = 200
    private const val TAG = "FreeBuds"
    private var nextId = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    @Synchronized
    private fun append(tag: LogTag, message: String) {
        val entry = LogEntry(nextId++, tag, message, timeFormat.format(Date()))
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
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
