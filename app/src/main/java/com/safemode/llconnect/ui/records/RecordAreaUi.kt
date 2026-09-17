package com.safemode.llconnect.ui.records

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.ui.graphics.vector.ImageVector
import com.safemode.llconnect.data.RecordArea

fun RecordArea.icon(): ImageVector = when (this) {
    RecordArea.SERVICE -> Icons.Filled.Build
    RecordArea.REPAIR -> Icons.Filled.Handyman
    RecordArea.UPGRADE -> Icons.Filled.Upgrade
    RecordArea.GAS -> Icons.Filled.LocalGasStation
    RecordArea.ODOMETER -> Icons.Filled.Speed
    RecordArea.TAX -> Icons.Filled.Toll
    RecordArea.PLAN -> Icons.Filled.EventNote
    RecordArea.SUPPLY -> Icons.Filled.Inventory2
    RecordArea.REMINDER -> Icons.Filled.Notifications
    RecordArea.EQUIPMENT -> Icons.Filled.Backpack
    RecordArea.NOTE -> Icons.Filled.StickyNote2
}

fun recordAreaFromName(name: String): RecordArea =
    runCatching { RecordArea.valueOf(name) }.getOrDefault(RecordArea.SERVICE)
