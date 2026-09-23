package com.safemode.llconnect.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.ui.common.clearImageCache
import com.safemode.llconnect.ui.common.EmptyState
import com.safemode.llconnect.ui.common.ErrorState
import com.safemode.llconnect.ui.common.LoadingState
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.vehicles.VehicleCard
import com.safemode.llconnect.ui.vehicles.VehiclesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenVehicles: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    viewModel: VehiclesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh quietly when returning to the dashboard (e.g. after add/edit/delete).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
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
                    icon = Icons.Filled.Settings,
                    title = "Welcome to LLConnect",
                    message = "Connect to your self-hosted LubeLogger instance to get started.",
                    actionLabel = "Set up connection",
                    onAction = onOpenServer,
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "${s.data.size} vehicle${if (s.data.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "in your garage",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    if (s.data.isNotEmpty()) {
                        item {
                            Text(
                                "Your vehicles",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(s.data, key = { it.id ?: it.hashCode().toLong() }) { vehicle ->
                            VehicleCard(
                                vehicle = vehicle,
                                baseUrl = baseUrl,
                                onClick = { vehicle.id?.let { onOpenVehicle(it.toString()) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
