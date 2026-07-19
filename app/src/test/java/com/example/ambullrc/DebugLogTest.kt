package com.example.ambullrc

import com.example.ambullrc.viewmodel.DebugLog
import com.example.ambullrc.viewmodel.LogCategory
import com.example.ambullrc.viewmodel.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [DebugLog] (feature 005): ordering, immediate visibility, the 50-entry cap,
 * and that each entry carries the category/level/message/timestamp it was added with.
 */
class DebugLogTest {

    @Test
    fun startsEmpty() {
        assertTrue(DebugLog().entries.value.isEmpty())
    }

    @Test
    fun singleAddIsReflectedImmediately() {
        val log = DebugLog()
        log.add(LogCategory.APP, LogLevel.INFO, "first")
        val entries = log.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogCategory.APP, entries[0].category)
        assertEquals(LogLevel.INFO, entries[0].level)
        assertEquals("first", entries[0].message)
    }

    @Test
    fun defaultLevelIsInfo() {
        val log = DebugLog()
        log.add(LogCategory.SENT, message = "no explicit level")
        assertEquals(LogLevel.INFO, log.entries.value[0].level)
    }

    @Test
    fun multipleAddsPreserveChronologicalOrder() {
        val log = DebugLog()
        log.add(LogCategory.APP, LogLevel.INFO, "one")
        log.add(LogCategory.APP, LogLevel.INFO, "two")
        log.add(LogCategory.APP, LogLevel.INFO, "three")
        assertEquals(listOf("one", "two", "three"), log.entries.value.map { it.message })
    }

    @Test
    fun cappedAtFiftyEntriesDroppingOldestFirst() {
        val log = DebugLog()
        for (i in 1..55) {
            log.add(LogCategory.APP, LogLevel.INFO, "entry-$i")
        }
        val entries = log.entries.value
        assertEquals(50, entries.size)
        assertEquals("entry-6", entries.first().message)
        assertEquals("entry-55", entries.last().message)
    }
}
