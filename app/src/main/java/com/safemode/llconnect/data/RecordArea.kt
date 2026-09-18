package com.safemode.llconnect.data

/** The per-vehicle record areas exposed by LubeLogger. */
enum class RecordArea(
    val label: String,
    val supportsAdd: Boolean,
    /** Whether records in this area carry file attachments. */
    val supportsAttachments: Boolean,
) {
    SERVICE("Service", true, true),
    REPAIR("Repairs", true, true),
    UPGRADE("Upgrades", true, true),
    GAS("Fuel", true, true),
    ODOMETER("Odometer", true, true),
    TAX("Taxes", true, true),
    PLAN("Planner", true, true),
    SUPPLY("Supplies", true, true),
    REMINDER("Reminders", true, false),
    EQUIPMENT("Equipment", true, true),
    NOTE("Notes", true, true),
}

/** Lightweight per-area summary shown on the vehicle dashboard tiles. */
data class AreaSummary(
    val count: Int,
    /** Most recent activity date (or, for reminders, the soonest due date); null if none/undated. */
    val lastDate: String?,
)

/** A flattened, display-ready view of any record type. */
data class RecordRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val trailing: String?,
    val meta: String?,
    /** True when the record has at least one file attachment (drives the list indicator). */
    val hasAttachments: Boolean = false,
)
