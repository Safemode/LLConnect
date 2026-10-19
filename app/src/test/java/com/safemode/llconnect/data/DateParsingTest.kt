package com.safemode.llconnect.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateParsingTest {

    @Test fun parsesIso() {
        assertEquals(LocalDate.of(2026, 10, 19), DateParsing.parse("2026-10-19"))
    }

    @Test fun parsesUsSlash() {
        assertEquals(LocalDate.of(2026, 10, 19), DateParsing.parse("10/19/2026"))
    }

    @Test fun parsesEuSlash() {
        assertEquals(LocalDate.of(2026, 10, 19), DateParsing.parse("19/10/2026"))
    }

    @Test fun stripsTimeComponent() {
        assertEquals(LocalDate.of(2026, 10, 19), DateParsing.parse("2026-10-19T08:30:00"))
        assertEquals(LocalDate.of(2026, 10, 19), DateParsing.parse("2026-10-19 08:30"))
    }

    @Test fun blankAndInvalidAreNull() {
        assertNull(DateParsing.parse(null))
        assertNull(DateParsing.parse(""))
        assertNull(DateParsing.parse("not a date"))
    }
}
