package com.safemode.llconnect.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.notifications.NotificationScheduler
import com.safemode.llconnect.data.settings.CacheSize
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.safemode.llconnect.data.settings.DistanceUnit
import com.safemode.llconnect.data.settings.FuelEconomyUnit
import com.safemode.llconnect.data.settings.RecordSortOrder
import com.safemode.llconnect.data.settings.ThemePreference
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshCacheUsage()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var qualityExpanded by remember { mutableStateOf(false) }
    // Result ignored: posting is gated by Notifications.canPost() at send time, so a denied grant
    // simply means nothing is shown until the user allows it in system settings.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    // Persist a notification-pref change, reschedule the daily worker from the new config, and
    // request the notification permission the first time a type is turned on.
    fun onNotifyChange(enabling: Boolean, transform: (ConnectionConfig) -> ConnectionConfig) {
        viewModel.updateAndSave(transform)
        NotificationScheduler.schedule(context, viewModel.config.value)
        if (enabling && needsNotificationPermission()) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Changes here save automatically. Server connection details live under Server " +
                    "in the menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- Notifications card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Off by default. The reminder and queue checks run once a day, at the " +
                            "odometer reminder time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NotifyRow(
                        label = "Daily odometer reminder",
                        description = "A daily nudge to log an odometer reading.",
                        checked = config.notifyOdometerDaily,
                        onCheckedChange = { on -> onNotifyChange(on) { it.copy(notifyOdometerDaily = on) } },
                    )
                    if (config.notifyOdometerDaily) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Reminder time", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(formatReminderTime(context, config.odometerReminderTime))
                            }
                        }
                    }
                    NotifyRow(
                        label = "Reminders due or overdue",
                        description = "Notify about reminders your server marks urgent or past due.",
                        checked = config.notifyReminders,
                        onCheckedChange = { on -> onNotifyChange(on) { it.copy(notifyReminders = on) } },
                    )
                    NotifyRow(
                        label = "Queue upload results",
                        description = "Notify when records queued offline finish uploading.",
                        checked = config.notifyQueue,
                        onCheckedChange = { on -> onNotifyChange(on) { it.copy(notifyQueue = on) } },
                    )
                }
            }

            // ---- Receipt scan quality card ----
            ReceiptQualityCard(
                fullQualityAreas = config.fullQualityAreas,
                expanded = qualityExpanded,
                onToggleExpanded = { qualityExpanded = !qualityExpanded },
                onSetFullQuality = { area, full ->
                    viewModel.updateAndSave {
                        val areas = it.fullQualityAreas.toMutableSet()
                        if (full) areas.add(area.name) else areas.remove(area.name)
                        it.copy(fullQualityAreas = areas)
                    }
                },
            )

            // ---- Theme card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "App appearance. Midnight uses pure-black backgrounds, best on OLED screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = config.themePreference == pref,
                                onClick = { viewModel.updateAndSave { it.copy(themePreference = pref) } },
                                label = { Text(pref.label) },
                            )
                        }
                    }
                }
            }

            // ---- Fuel economy card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Fuel economy units", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How fuel economy is calculated for gas records. Default uses your " +
                            "server's setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FuelEconomyUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = config.fuelEconomyUnit == unit,
                                onClick = { viewModel.updateAndSave { it.copy(fuelEconomyUnit = unit) } },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                }
            }

            // ---- Distance unit card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Distance unit", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How odometer readings are labeled throughout the app. LubeLogger " +
                            "doesn't report a unit, so this is a display choice only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DistanceUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = config.distanceUnit == unit,
                                onClick = { viewModel.updateAndSave { it.copy(distanceUnit = unit) } },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                }
            }

            // ---- Record list order card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Record list order", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How dated record lists (odometer, service, gas, …) are ordered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordSortOrder.entries.forEach { order ->
                            FilterChip(
                                selected = config.recordSortOrder == order,
                                onClick = { viewModel.updateAndSave { it.copy(recordSortOrder = order) } },
                                label = { Text(order.label) },
                            )
                        }
                    }
                }
            }

            // ---- Cache card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Offline cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Server data is cached on the device so it's available when the server " +
                            "can't be reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Currently using ${formatBytes(cacheUsage)} of ${config.cacheSize.label}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Maximum cache size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CacheSize.entries.forEach { size ->
                            FilterChip(
                                selected = config.cacheSize == size,
                                onClick = { viewModel.updateAndSave { it.copy(cacheSize = size) } },
                                label = { Text(size.label) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCache() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Text("  Clear cache")
                    }
                }
            }
        }

        if (showTimePicker) {
            val (hour, minute) = parseHhMm(config.odometerReminderTime)
            NotificationTimePickerDialog(
                initialHour = hour,
                initialMinute = minute,
                is24Hour = DateFormat.is24HourFormat(context),
                onConfirm = { h, m ->
                    val value = String.format(Locale.US, "%02d:%02d", h, m)
                    viewModel.updateAndSave { it.copy(odometerReminderTime = value) }
                    NotificationScheduler.schedule(context, viewModel.config.value)
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false },
            )
        }
    }
}

/**
 * Collapsible card for choosing per-section receipt scan quality. Each attachment-carrying area
 * gets a two-segment Compressed / Full-quality chooser. Collapsed by default since there are many
 * sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptQualityCard(
    fullQualityAreas: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetFullQuality: (RecordArea, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Receipt scan quality", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }

            if (expanded) {
                Text(
                    "Compressed keeps uploads small. Full quality uploads a full-resolution, " +
                        "lossless image (much larger) — set it per section where receipt " +
                        "detail matters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RecordArea.entries.filter { it.supportsAttachments }.forEach { area ->
                    val full = area.name in fullQualityAreas
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(area.label, style = MaterialTheme.typography.bodyLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !full,
                                onClick = { onSetFullQuality(area, false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Compressed") }
                            SegmentedButton(
                                selected = full,
                                onClick = { onSetFullQuality(area, true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Full quality") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifyRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

/**
 * Formats the stored 24-hour "HH:mm" reminder time for display using the device's clock format,
 * so a phone set to AM/PM shows e.g. "6:00 PM" and a 24-hour phone shows "18:00".
 */
private fun formatReminderTime(context: Context, value: String): String {
    val (hour, minute) = parseHhMm(value)
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** Parses an "HH:mm" string into (hour, minute), defaulting to 18:00 when malformed. */
private fun parseHhMm(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 18
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

/** Human-readable byte size, e.g. "3.4 MB". */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
