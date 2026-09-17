package com.safemode.llconnect.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.safemode.llconnect.data.settings.AuthMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var showSecret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Connection card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Server connection", style = MaterialTheme.typography.titleMedium)

                    // Scheme
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("https", "http").forEach { scheme ->
                            FilterChip(
                                selected = config.scheme == scheme,
                                onClick = { viewModel.update { it.copy(scheme = scheme) } },
                                label = { Text(scheme.uppercase()) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = config.host,
                        onValueChange = { v -> viewModel.update { it.copy(host = v) } },
                        label = { Text("Host / IP address") },
                        placeholder = { Text("e.g. 192.168.1.50 or lube.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.port,
                        onValueChange = { v -> viewModel.update { it.copy(port = v.filter(Char::isDigit)) } },
                        label = { Text("Port (optional)") },
                        placeholder = { Text("e.g. 8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Base URL: ${config.baseUrl()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Authentication card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Authentication", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.authMode == AuthMode.API_KEY,
                            onClick = { viewModel.update { it.copy(authMode = AuthMode.API_KEY) } },
                            label = { Text("API key") },
                        )
                        FilterChip(
                            selected = config.authMode == AuthMode.BASIC,
                            onClick = { viewModel.update { it.copy(authMode = AuthMode.BASIC) } },
                            label = { Text("Username / password") },
                        )
                    }

                    if (config.authMode == AuthMode.API_KEY) {
                        OutlinedTextField(
                            value = config.apiKey,
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
                            value = config.basicUsername,
                            onValueChange = { v -> viewModel.update { it.copy(basicUsername = v) } },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = config.basicPassword,
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
                }
            }

            // ---- Advanced card ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Culture-invariant responses", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Recommended. Requests locale-neutral numbers and ISO dates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = config.cultureInvariant,
                            onCheckedChange = { v -> viewModel.update { it.copy(cultureInvariant = v) } },
                        )
                    }
                }
            }

            // ---- Test result ----
            when (val result = testResult) {
                is TestResult.Testing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text("Testing connection…")
                }

                is TestResult.Success -> ResultBanner(
                    success = true,
                    message = result.message,
                )

                is TestResult.Failure -> ResultBanner(
                    success = false,
                    message = result.message,
                )

                TestResult.Idle -> {}
            }

            // ---- Actions ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saved) "Saved" else "Save")
                }
                Button(
                    onClick = { viewModel.saveAndTest() },
                    enabled = config.isConfigured,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & test")
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(success: Boolean, message: String) {
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
