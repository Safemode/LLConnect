package com.safemode.llconnect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.ServerReachability
import androidx.compose.material3.CircularProgressIndicator
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safemode.llconnect.ui.about.AboutScreen
import com.safemode.llconnect.ui.attachments.AttachmentsScreen
import com.safemode.llconnect.ui.dashboard.DashboardScreen
import com.safemode.llconnect.ui.history.HistoryScreen
import com.safemode.llconnect.ui.navigation.AppDrawerContent
import com.safemode.llconnect.ui.navigation.Routes
import com.safemode.llconnect.ui.navigation.TopDestination
import com.safemode.llconnect.ui.records.RecordFormScreen
import com.safemode.llconnect.ui.records.RecordsScreen
import com.safemode.llconnect.ui.reminders.RemindersScreen
import com.safemode.llconnect.ui.reports.ReportsScreen
import com.safemode.llconnect.ui.server.ServerInfoScreen
import com.safemode.llconnect.ui.settings.SettingsScreen
import com.safemode.llconnect.ui.tools.ToolsScreen
import com.safemode.llconnect.ui.vehicles.VehicleDetailScreen
import com.safemode.llconnect.ui.vehicles.VehicleFormScreen
import com.safemode.llconnect.ui.vehicles.VehiclesScreen
import kotlinx.coroutines.launch

@Composable
fun LLConnectRoot() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val serverState by Graph.serverStatus.state.collectAsStateWithLifecycle()
    val showBanner = serverState != ServerReachability.REACHABLE

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    // Back gesture handling:
    // 1. If the drawer is open, back closes it first.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    // 2. On the start destination, require a second back press within 2s to exit.
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute == Routes.DASHBOARD && !drawerState.isOpen) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000L) {
            context.findActivity()?.finish()
        } else {
            lastBackPress = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe from the left edge to open, and swipe to close while open.
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                onDestinationClick = { dest ->
                    scope.launch { drawerState.close() }
                    if (currentRoute != dest.route) {
                        navController.navigate(dest.route) {
                            popUpTo(Routes.DASHBOARD)
                            launchSingleTop = true
                        }
                    }
                },
            )
        },
    ) {
        // When the banner shows, consume the status-bar inset here so the banner sits below the
        // status bar (and the app bar below it doesn't double-inset). When hidden, the top app bar
        // (or a pushed screen's own bar) consumes the status inset itself, keeping the normal
        // edge-to-edge look.
        val contentModifier = if (showBanner) {
            Modifier.fillMaxSize().statusBarsPadding()
        } else {
            Modifier.fillMaxSize()
        }
        Column(modifier = contentModifier) {
            ConnectivityBanner(state = serverState)
            // A single top app bar for the flat drawer destinations; pushed screens (detail,
            // forms, attachments) carry their own back-arrow bar instead.
            val topDest = TopDestination.entries.firstOrNull { it.route == currentRoute }
            if (topDest != null) {
                TopBar(
                    title = if (topDest == TopDestination.DASHBOARD) "LLConnect" else topDest.label,
                    onOpenDrawer = openDrawer,
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.weight(1f),
            ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenVehicles = { navController.navigate(Routes.VEHICLES) },
                    onOpenServer = { navController.navigate(Routes.SERVER) },
                    onOpenVehicle = { id -> navController.navigate(Routes.vehicleDetail(id)) },
                    onQuickEntry = { vehicleId, area ->
                        navController.navigate(Routes.recordForm(vehicleId, area))
                    },
                )
            }
            composable(Routes.VEHICLES) {
                VehiclesScreen(
                    onAddVehicle = { navController.navigate(Routes.vehicleForm()) },
                    onOpenVehicle = { id -> navController.navigate(Routes.vehicleDetail(id)) },
                )
            }
            composable(
                route = Routes.VEHICLE_FORM,
                arguments = listOf(
                    navArgument("vehicleId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                VehicleFormScreen(
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.VEHICLE_DETAIL,
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType }),
            ) { entry ->
                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                VehicleDetailScreen(
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                    onOpenArea = { area -> navController.navigate(Routes.records(vehicleId, area)) },
                    onEdit = { id -> navController.navigate(Routes.vehicleForm(id)) },
                )
            }
            composable(
                route = Routes.RECORDS,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("area") { type = NavType.StringType },
                ),
            ) { entry ->
                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                val area = entry.arguments?.getString("area").orEmpty()
                RecordsScreen(
                    vehicleId = vehicleId,
                    areaName = area,
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.recordForm(vehicleId, area)) },
                    onOpen = { recordId ->
                        navController.navigate(Routes.recordForm(vehicleId, area, recordId))
                    },
                )
            }
            composable(
                route = Routes.RECORD_FORM,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("area") { type = NavType.StringType },
                    navArgument("recordId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                val area = entry.arguments?.getString("area").orEmpty()
                val recordId = entry.arguments?.getString("recordId").orEmpty()
                RecordFormScreen(
                    vehicleId = vehicleId,
                    areaName = area,
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                    onAttachments = { rid ->
                        navController.navigate(Routes.attachments(vehicleId, area, rid))
                    },
                )
            }
            composable(
                route = Routes.ATTACHMENTS,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("area") { type = NavType.StringType },
                    navArgument("recordId") { type = NavType.StringType },
                ),
            ) { entry ->
                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                val area = entry.arguments?.getString("area").orEmpty()
                val recordId = entry.arguments?.getString("recordId").orEmpty()
                AttachmentsScreen(
                    vehicleId = vehicleId,
                    areaName = area,
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REMINDERS) {
                RemindersScreen()
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.REPORTS) {
                ReportsScreen()
            }
            composable(Routes.TOOLS) {
                ToolsScreen()
            }
            composable(Routes.SERVER) {
                ServerInfoScreen()
            }
            composable(Routes.ABOUT) {
                AboutScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            }
        }
    }
}

@Composable
private fun ConnectivityBanner(state: ServerReachability) {
    when (state) {
        ServerReachability.REACHABLE -> return
        ServerReachability.CHECKING -> ConnectivityBar(
            background = MaterialTheme.colorScheme.secondaryContainer,
            foreground = MaterialTheme.colorScheme.onSecondaryContainer,
            text = "Connecting to server… showing cached data",
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        ServerReachability.UNREACHABLE -> ConnectivityBar(
            background = MaterialTheme.colorScheme.error,
            foreground = MaterialTheme.colorScheme.onError,
            text = "Server unreachable — showing cached data if available",
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConnectivityBar(
    background: Color,
    foreground: Color,
    text: String,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        },
    )
}

/** Unwraps the [Activity] from a (possibly wrapped) Compose [Context]. */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
