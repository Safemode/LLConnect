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
    PLAN("Planner", false, true),
    SUPPLY("Supplies", false, true),
    REMINDER("Reminders", false, false),
    EQUIPMENT("Equipment", false, true),
    NOTE("Notes", false, true),
}

/** A flattened, display-ready view of any record type. */
data class RecordRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val trailing: String?,
    val meta: String?,
)
