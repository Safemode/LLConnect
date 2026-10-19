package com.safemode.llconnect.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parsing for the date strings LubeLogger emits, which vary by the server's culture setting
 * (ISO, US, UK, and a few European layouts). Centralized so the repository and its tests agree on
 * exactly which layouts are accepted.
 */
object DateParsing {

    /** Date layouts LubeLogger may emit depending on locale/culture settings. */
    val formats: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd", "M/d/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy", "d.M.yyyy",
    ).map { DateTimeFormatter.ofPattern(it) }

    /** Strips any time component, then parses against the known layouts. Null if none match. */
    fun parse(raw: String?): LocalDate? {
        val d = raw?.substringBefore('T')?.substringBefore(' ')?.trim().orEmpty()
        if (d.isEmpty()) return null
        return formats.firstNotNullOfOrNull { runCatching { LocalDate.parse(d, it) }.getOrNull() }
    }
}
