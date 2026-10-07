package app.mystery0.ims.tensor

import org.junit.Assert.*
import org.junit.Test

class LogReadCursorTest {
    private val first = "10-07 12:00:00.123456 123 123 I Test: first"
    private val next = "10-07 12:00:00.123457 123 123 I Test: next"

    @Test fun initialReadIncludesBufferedEvents() {
        val resume = LogReadCursor().resume()
        assertNull(resume.timestamp)
        assertTrue(resume.accept(first))
        assertFalse(resume.accept("--------- beginning of main"))
    }

    @Test fun restartSkipsOnlyAlreadyReadOccurrencesAtBoundary() {
        val cursor = LogReadCursor()
        cursor.record(first)
        cursor.record(first)
        val resume = cursor.resume()
        assertEquals("10-07 12:00:00.123456", resume.timestamp)
        assertFalse(resume.accept(first))
        assertFalse(resume.accept(first))
        assertTrue(resume.accept(first))
        assertTrue(resume.accept(next))
    }

    @Test fun successiveRestartsAdvanceWithoutPermanentlyDeduplicatingText() {
        val cursor = LogReadCursor()
        cursor.record(first)
        cursor.record(next)
        assertFalse(cursor.resume().accept(next))
        assertFalse(cursor.resume().accept(next))
        assertTrue(cursor.resume().accept(first))
    }
}
