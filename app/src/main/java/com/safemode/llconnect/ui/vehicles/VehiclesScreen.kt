package com.safemode.llconnect.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.ui.common.clearImageCache
import com.safemode.llconnect.ui.common.EmptyState
import com.safemode.llconnect.ui.common.FavoriteStar
import com.safemode.llconnect.ui.common.ErrorState
import com.safemode.llconnect.ui.common.LoadingState
import com.safemode.llconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    viewModel: VehiclesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val favoriteId by viewModel.favoriteVehicleId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh quietly when returning to this screen (e.g. after add/edit/delete).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add vehicle") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onAddVehicle,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                clearImageCache(context)
                viewModel.refresh()
            },
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.DirectionsCar,
                    title = "Not connected yet",
                    message = "Open the menu and go to Server to add your LubeLogger server address and API key.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.DirectionsCar,
                        title = "No vehicles",
                        message = "Add your first vehicle to start logging.",
                        actionLabel = "Add vehicle",
                        onAction = onAddVehicle,
                    )
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id ?: it.hashCode().toLong() }) { vehicle ->
                            VehicleCard(
                                vehicle = vehicle,
                                baseUrl = baseUrl,
                                isFavorite = vehicle.id?.toString() == favoriteId,
                                onToggleFavorite = {
                                    vehicle.id?.let { viewModel.toggleFavorite(it.toString()) }
                                },
                                onClick = { vehicle.id?.let { onOpenVehicle(it.toString()) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    baseUrl: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val imageUrl = vehicleImageUrl(vehicle, baseUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(
                    vehicle.licensePlate?.ifBlank { null },
                    vehicle.tags?.joinToString(" ")?.ifBlank { null },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FavoriteStar(
                isFavorite = isFavorite,
                onFavoriteChange = { onToggleFavorite() },
                itemName = vehicle.displayName,
            )
        }
    }
}

/** Builds an absolute URL for the vehicle image when it is a relative path. */
internal fun vehicleImageUrl(vehicle: Vehicle, baseUrl: String?): String? {
    val loc = vehicle.imageLocation?.takeIf { it.isNotBlank() } ?: return null
    if (loc.startsWith("http")) return loc
    val base = (baseUrl ?: return null).trimEnd('/')
    return base + "/" + loc.trimStart('/')
}
