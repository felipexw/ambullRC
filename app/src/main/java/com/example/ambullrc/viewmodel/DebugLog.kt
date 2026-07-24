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

    // True once an add has dropped the oldest entry to stay within MAX_ENTRIES, so the UI can
    // tell "exactly 50 logged" apart from "more than 50 logged, oldest discarded".
    private val _truncated = MutableStateFlow(false)
    val truncated: StateFlow<Boolean> = _truncated.asStateFlow()

    fun add(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String) {
        val entry = LogEntry(timestamp = LocalTime.now(), category = category, level = level, message = message)
        _entries.update {
            val updated = it + entry
            if (updated.size > MAX_ENTRIES) _truncated.value = true
            updated.takeLast(MAX_ENTRIES)
        }
    }

    fun clear() {
        _entries.value = emptyList()
        _truncated.value = false
    }
}
