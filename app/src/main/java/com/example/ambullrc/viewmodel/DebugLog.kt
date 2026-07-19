package com.example.ambullrc.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_ENTRIES = 50

/**
 * In-memory ring buffer of human-readable event lines, rendered on-screen so behavior (e.g. which
 * commands actually got sent) can be checked directly on a device without an adb/Logcat connection.
 */
class DebugLog {
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(message: String) {
        _entries.update { (it + message).takeLast(MAX_ENTRIES) }
    }
}
