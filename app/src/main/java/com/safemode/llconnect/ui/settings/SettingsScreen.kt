package com.safemode.llconnect.ui.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.data.settings.CacheSize
import com.safemode.llconnect.data.settings.FuelEconomyUnit
import com.safemode.llconnect.data.settings.RecordSortOrder
import com.safemode.llconnect.data.settings.ThemePreference
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
    }
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
