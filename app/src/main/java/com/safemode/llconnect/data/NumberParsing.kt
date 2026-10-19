package com.safemode.llconnect.data

/**
 * Locale-tolerant parsing for the numeric form fields (cost, fuel used). Many locales' keyboards
 * show a comma as the decimal key, so filtering input down to digits and '.' silently drops the
 * separator and turns "12,50" into "1250" - a 100x error. This accepts both '.' and ',' as the
 * decimal separator, ignores grouping separators, and returns null for blank/invalid input instead
 * of mangling it.
 */
object NumberParsing {

    /** Characters allowed in a decimal input field, so the keyboard's comma key isn't swallowed. */
    fun isDecimalInputChar(c: Char): Boolean = c.isDigit() || c == '.' || c == ','

    /**
     * Parses a user-entered decimal. The last '.' or ',' is treated as the decimal point and every
     * other separator as grouping, so "1,234.56", "1.234,56" and "1234.56" all yield 1234.56.
     */
    fun parseDecimal(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-") return null

        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val decimalIndex = maxOf(lastDot, lastComma)

        val normalized = buildString {
            cleaned.forEachIndexed { i, c ->
                when {
                    i == decimalIndex && (c == '.' || c == ',') -> append('.')
                    c == '.' || c == ',' -> {} // grouping separator - drop
                    else -> append(c)
                }
            }
        }
        return normalized.toDoubleOrNull()
    }
}
