package com.example.ambullrc.viewmodel

import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_ENTRIES = 50

/**
 * In-memory ring buffer of [LogEntry] records, rendered on-screen so behavior (e.g. which
 * commands actually got sent) can be checked directly on a device without an adb/Logcat connection.
 */
class DebugLog {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String) {
        val entry = LogEntry(timestamp = LocalTime.now(), category = category, level = level, message = message)
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }
}
