package com.baroness.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class DateUtilsTest {
    @Test
    fun parsesPostgrestTimestampWithShortOffset() {
        assertEquals(EXPECTED_MILLIS, parseIsoToLong("2026-10-11 10:42:19.599+00"))
    }

    @Test
    fun parsesPostgrestTimestampWithMicroseconds() {
        assertEquals(EXPECTED_MILLIS, parseIsoToLong("2026-10-11 10:42:19.599000+00"))
    }

    @Test
    fun parsesRealtimeTimestamp() {
        assertEquals(EXPECTED_MILLIS, parseIsoToLong("2026-10-11T10:42:19.599Z"))
    }

    @Test
    fun parsesColonSeparatedOffset() {
        assertEquals(EXPECTED_MILLIS, parseIsoToLong("2026-10-11T10:42:19.599+00:00"))
    }

    @Test
    fun nullTimestampReturnsFallbackAndLogsWarning() {
        val logger = Logger.getLogger("DateUtils")
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }
        val previousLevel = logger.level
        logger.addHandler(handler)
        logger.level = Level.ALL

        try {
            val fallback = parseIsoToLong(null)

            assertTrue(fallback > 0L)
            assertTrue(records.any {
                it.level == Level.WARNING && it.message.contains("Unable to parse timestamp: null")
            })
        } finally {
            logger.removeHandler(handler)
            logger.level = previousLevel
        }
    }

    private companion object {
        const val EXPECTED_MILLIS = 1_791_715_339_599L
    }
}
