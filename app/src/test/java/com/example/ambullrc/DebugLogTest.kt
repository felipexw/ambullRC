package com.example.ambullrc

import com.example.ambullrc.viewmodel.DebugLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [DebugLog]: ordering, immediate visibility, and the 50-entry cap (FR-007).
 */
class DebugLogTest {

    @Test
    fun startsEmpty() {
        assertTrue(DebugLog().entries.value.isEmpty())
    }

    @Test
    fun singleAddIsReflectedImmediately() {
        val log = DebugLog()
        log.add("first")
        assertEquals(listOf("first"), log.entries.value)
    }

    @Test
    fun multipleAddsPreserveChronologicalOrder() {
        val log = DebugLog()
        log.add("one")
        log.add("two")
        log.add("three")
        assertEquals(listOf("one", "two", "three"), log.entries.value)
    }

    @Test
    fun cappedAtFiftyEntriesDroppingOldestFirst() {
        val log = DebugLog()
        for (i in 1..55) {
            log.add("entry-$i")
        }
        val entries = log.entries.value
        assertEquals(50, entries.size)
        assertEquals("entry-6", entries.first())
        assertEquals("entry-55", entries.last())
    }
}
