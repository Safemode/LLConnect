package com.safemode.llconnect.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Preset date ranges for the History and Reports screens. */
enum class DateRange(val label: String) {
    ALL("All"),
    D30("30 days"),
    D90("90 days"),
    Y1("1 year"),
    YTD("Year to date");

    /** (startDate, endDate) as ISO strings, or nulls for [ALL]. */
    fun bounds(today: LocalDate = LocalDate.now()): Pair<String?, String?> = when (this) {
        ALL -> null to null
        D30 -> today.minusDays(30).toString() to today.toString()
        D90 -> today.minusDays(90).toString() to today.toString()
        Y1 -> today.minusYears(1).toString() to today.toString()
        YTD -> today.withDayOfYear(1).toString() to today.toString()
    }
}

/** A horizontally scrollable row of date-range filter chips. */
@Composable
fun DateRangeChips(
    selected: DateRange,
    onSelect: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}
