package com.safemode.llconnect.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberParsingTest {

    @Test fun dotDecimalParses() {
        assertEquals(12.5, NumberParsing.parseDecimal("12.50")!!, 0.0001)
    }

    @Test fun commaDecimalParses() {
        // The bug this guards against: a comma-decimal keyboard turning 12,50 into 1250.
        assertEquals(12.5, NumberParsing.parseDecimal("12,50")!!, 0.0001)
    }

    @Test fun usGroupingWithDotDecimal() {
        assertEquals(1234.56, NumberParsing.parseDecimal("1,234.56")!!, 0.0001)
    }

    @Test fun euGroupingWithCommaDecimal() {
        assertEquals(1234.56, NumberParsing.parseDecimal("1.234,56")!!, 0.0001)
    }

    @Test fun plainIntegerParses() {
        assertEquals(1234.0, NumberParsing.parseDecimal("1234")!!, 0.0001)
    }

    @Test fun negativeParses() {
        assertEquals(-5.5, NumberParsing.parseDecimal("-5.5")!!, 0.0001)
    }

    @Test fun blankIsNull() {
        assertNull(NumberParsing.parseDecimal(""))
        assertNull(NumberParsing.parseDecimal("   "))
    }

    @Test fun nonNumericIsNull() {
        assertNull(NumberParsing.parseDecimal("abc"))
        assertNull(NumberParsing.parseDecimal("-"))
    }

    @Test fun commaIsAnAllowedInputChar() {
        // So the keyboard's comma key isn't stripped before parsing.
        assertTrue(NumberParsing.isDecimalInputChar(','))
        assertTrue(NumberParsing.isDecimalInputChar('.'))
        assertTrue(NumberParsing.isDecimalInputChar('7'))
    }
}
