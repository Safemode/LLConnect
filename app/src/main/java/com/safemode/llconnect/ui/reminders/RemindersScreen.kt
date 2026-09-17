package com.safemode.llconnect.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.data.ReminderItem
import com.safemode.llconnect.ui.common.EmptyState
import com.safemode.llconnect.ui.common.ErrorState
import com.safemode.llconnect.ui.common.LoadingState
import com.safemode.llconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onOpenDrawer: () -> Unit,
    viewModel: RemindersViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Add your server details in Settings first.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.NotificationsActive,
                        title = "No reminders",
                        message = "Reminders you add to your vehicles show up here, soonest first.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.data) { item -> ReminderCard(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(item: ReminderItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.vehicleName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val due = listOfNotNull(
                    item.dueDate?.takeIf { it.isNotBlank() }?.let { "Due $it" },
                    item.dueOdometer?.let { "at ${"%,d".format(it)}" },
                ).joinToString(" · ")
                if (due.isNotBlank()) {
                    Text(
                        text = due,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.urgency?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
