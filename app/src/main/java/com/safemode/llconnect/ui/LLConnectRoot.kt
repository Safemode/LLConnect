package com.safemode.llconnect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
            val navigateTo: (TopDestination) -> Unit = { dest ->
                scope.launch { drawerState.close() }
                if (currentRoute != dest.route) {
                    navController.navigate(dest.route) {
                        popUpTo(Routes.DASHBOARD)
                        launchSingleTop = true
                    }
                }
            }
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "LLConnect",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "LubeLogger companion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    // Primary destinations at the top; tools/server/about/settings at the bottom.
                    val bottomItems = listOf(
                        TopDestination.TOOLS,
                        TopDestination.SERVER,
                        TopDestination.ABOUT,
                        TopDestination.SETTINGS,
                    )
                    TopDestination.entries
                        .filter { it !in bottomItems }
                        .forEach { dest ->
                            DrawerItem(dest, currentRoute == dest.route) { navigateTo(dest) }
                        }
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    bottomItems.forEach { dest ->
                        DrawerItem(dest, currentRoute == dest.route) { navigateTo(dest) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
    ) {
        // When the banner shows, consume the status-bar inset here so the banner sits below the
        // status bar (and the screens below don't double-inset). When hidden, leave insets to the
        // screens' own top bars so they keep the normal edge-to-edge look.
        val contentModifier = if (showBanner) {
            Modifier.fillMaxSize().statusBarsPadding()
        } else {
            Modifier.fillMaxSize()
        }
        Column(modifier = contentModifier) {
            ConnectivityBanner(state = serverState)
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.weight(1f),
            ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenDrawer = openDrawer,
                    onOpenVehicles = { navController.navigate(Routes.VEHICLES) },
                    onOpenServer = { navController.navigate(Routes.SERVER) },
                    onOpenVehicle = { id -> navController.navigate(Routes.vehicleDetail(id)) },
                )
            }
            composable(Routes.VEHICLES) {
                VehiclesScreen(
                    onOpenDrawer = openDrawer,
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
                RemindersScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.REPORTS) {
                ReportsScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.TOOLS) {
                ToolsScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.SERVER) {
                ServerInfoScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.ABOUT) {
                AboutScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDrawer = openDrawer)
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

@Composable
private fun DrawerItem(dest: TopDestination, selected: Boolean, onClick: () -> Unit) {
    // The item keeps the standard M3 drawer-item footprint (56dp), but the selected
    // highlight pill is shorter top-to-bottom (inset vertically inside the row).
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(dest.icon, contentDescription = null, tint = content)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = dest.label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
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
