package com.safemode.llconnect.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safemode.llconnect.data.AreaSummary
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.ui.common.ErrorState
import com.safemode.llconnect.ui.common.LoadingState
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.records.icon

@Suppress("UNCHECKED_CAST")
private fun detailFactory(vehicleId: String) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VehicleDetailViewModel(vehicleId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onOpenArea: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val viewModel: VehicleDetailViewModel =
        viewModel(key = "vehicle-$vehicleId", factory = detailFactory(vehicleId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val title = (state as? UiState.Success)?.data?.displayName ?: "Vehicle"

    // After a successful delete, leave the screen.
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }
    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is UiState.Success) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit(vehicleId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Not connected.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> VehicleDetailContent(
                    vehicle = s.data,
                    baseUrl = baseUrl,
                    summaries = summaries,
                    onOpenArea = onOpenArea,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("Delete vehicle?") },
            text = {
                Text("This permanently deletes \"$title\" and all of its records on the server. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** Logging areas the user touches most, split from planning/reference for readability. */
private val LoggingAreas = listOf(
    RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE,
    RecordArea.GAS, RecordArea.ODOMETER, RecordArea.TAX,
)
private val PlanningAreas = listOf(
    RecordArea.PLAN, RecordArea.SUPPLY, RecordArea.REMINDER,
    RecordArea.EQUIPMENT, RecordArea.NOTE,
)

@Composable
private fun VehicleDetailContent(
    vehicle: Vehicle,
    baseUrl: String?,
    summaries: Map<RecordArea, AreaSummary>,
    onOpenArea: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            VehicleHeader(vehicle, baseUrl)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("Records")
        }
        items(LoggingAreas) { area ->
            AreaTile(area = area, summary = summaries[area], onClick = { onOpenArea(area.name) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("Planning & reference")
        }
        items(PlanningAreas) { area ->
            AreaTile(area = area, summary = summaries[area], onClick = { onOpenArea(area.name) })
        }
    }
}

@Composable
private fun VehicleHeader(vehicle: Vehicle, baseUrl: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val imageUrl = vehicleImageUrl(vehicle, baseUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val details = buildList {
                    vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { add("Plate: $it") }
                    vehicle.tags?.takeIf { it.isNotEmpty() }?.let { add("Tags: ${it.joinToString(" ")}") }
                    if (vehicle.isElectric == true) add("Electric")
                    if (vehicle.isDiesel == true) add("Diesel")
                    if (vehicle.useHours == true) add("Tracked by engine hours")
                    vehicle.purchaseDate?.takeIf { it.isNotBlank() }?.let { add("Purchased: $it") }
                }
                details.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun AreaTile(area: RecordArea, summary: AreaSummary?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = area.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = area.label,
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary != null) {
                Text(
                    text = "${summary.count} ${if (summary.count == 1) "record" else "records"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.lastDate?.let { date ->
                    val prefix = if (area == RecordArea.REMINDER) "Due " else "Last "
                    Text(
                        text = prefix + date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
