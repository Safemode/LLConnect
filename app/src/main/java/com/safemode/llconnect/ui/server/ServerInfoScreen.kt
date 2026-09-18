package com.safemode.llconnect.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.OutlinedTextField
import com.safemode.llconnect.data.settings.AuthMode
import com.safemode.llconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInfoScreen(
    onOpenDrawer: () -> Unit,
    viewModel: ServerViewModel = viewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var showSecret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- Connection ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Server connection", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("https", "http").forEach { scheme ->
                                FilterChip(
                                    selected = form.scheme == scheme,
                                    onClick = { viewModel.update { it.copy(scheme = scheme) } },
                                    label = { Text(scheme.uppercase()) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = form.host,
                            onValueChange = { v -> viewModel.update { it.copy(host = v) } },
                            label = { Text("Host / IP address") },
                            placeholder = { Text("e.g. 192.168.1.50 or lube.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.port,
                            onValueChange = { v -> viewModel.update { it.copy(port = v.filter(Char::isDigit)) } },
                            label = { Text("Port (optional)") },
                            placeholder = { Text("e.g. 8080") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Base URL: ${form.baseUrl()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---- Authentication (+ Save & test) ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Authentication", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = form.authMode == AuthMode.API_KEY,
                                onClick = { viewModel.update { it.copy(authMode = AuthMode.API_KEY) } },
                                label = { Text("API key") },
                            )
                            FilterChip(
                                selected = form.authMode == AuthMode.BASIC,
                                onClick = { viewModel.update { it.copy(authMode = AuthMode.BASIC) } },
                                label = { Text("Username / password") },
                            )
                        }

                        if (form.authMode == AuthMode.API_KEY) {
                            OutlinedTextField(
                                value = form.apiKey,
                                onValueChange = { v -> viewModel.update { it.copy(apiKey = v) } },
                                label = { Text("API key") },
                                singleLine = true,
                                visualTransformation = if (showSecret) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showSecret = !showSecret }) {
                                        Icon(
                                            if (showSecret) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                            contentDescription = "Toggle visibility",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Sent as the x-api-key header. Generate a key in LubeLogger under " +
                                    "Settings → API Access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = form.basicUsername,
                                onValueChange = { v -> viewModel.update { it.copy(basicUsername = v) } },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = form.basicPassword,
                                onValueChange = { v -> viewModel.update { it.copy(basicPassword = v) } },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (showSecret) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showSecret = !showSecret }) {
                                        Icon(
                                            if (showSecret) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                            contentDescription = "Toggle visibility",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Button(
                            onClick = { viewModel.saveAndTest() },
                            enabled = form.isConfigured,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save & test connection")
                        }
                    }
                }

                // ---- Advanced ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Culture-invariant responses", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Recommended. Requests locale-neutral numbers and ISO dates. " +
                                    "Applied when you save & test.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = form.cultureInvariant,
                            onCheckedChange = { v -> viewModel.update { it.copy(cultureInvariant = v) } },
                        )
                    }
                }

                // ---- Connection status / server info ----
                when (val s = state) {
                    UiState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text("Checking connection…")
                    }

                    UiState.NotConfigured -> Text(
                        "Enter your server details above and tap Save & test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is UiState.Error -> StatusBanner(success = false, message = s.message)

                    is UiState.Success -> {
                        StatusBanner(
                            success = true,
                            message = "Connected as ${s.data.user?.userName ?: "server"}.",
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Signed in", style = MaterialTheme.typography.titleMedium)
                                InfoRow(
                                    label = if (s.data.usingApiKey) "API Key Name" else "User",
                                    value = s.data.user?.userName ?: "—",
                                )
                                InfoRow("Email", s.data.user?.emailAddress ?: "—")
                                InfoRow("Admin", if (s.data.user?.isAdmin == true) "Yes" else "No")
                                InfoRow("Root", if (s.data.user?.isRootUser == true) "Yes" else "No")
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Instance", style = MaterialTheme.typography.titleMedium)
                                InfoRow("Version", s.data.version ?: "—")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(success: Boolean, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
