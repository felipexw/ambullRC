package com.example.ambullrc.viewmodel

import java.time.LocalTime

/** Which part of the app produced a [LogEntry] — drives the entry's colored tag in the UI. */
enum class LogCategory(val tag: String) {
    /** A direction command was written to the ESP32 link ("TX" in the design handoff). */
    SENT("TX"),

    /** Reserved for a reply read back from the ESP32 ("RX"). No current call site produces one —
     * this app's link is one-way (see specs/005-home-screen-ux-redesign/research.md Decision 5). */
    RECEIVED("RX"),

    /** Connection lifecycle events: connecting, connected, retry, drop ("BLE"). */
    CONNECTION("BLE"),

    /** General app-level events not tied to a specific command or the link itself ("APP"). */
    APP("APP"),
}

/** Severity of a [LogEntry] — drives the entry's message text color in the UI. */
enum class LogLevel { INFO, WARN, ERROR }

/** A single captured diagnostic event, shown in the on-screen log panel. */
data class LogEntry(
    val timestamp: LocalTime,
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
)
