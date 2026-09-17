package com.safemode.llconnect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safemode.llconnect.ui.attachments.AttachmentsScreen
import com.safemode.llconnect.ui.dashboard.DashboardScreen
import com.safemode.llconnect.ui.navigation.Routes
import com.safemode.llconnect.ui.navigation.TopDestination
import com.safemode.llconnect.ui.records.RecordFormScreen
import com.safemode.llconnect.ui.records.RecordsScreen
import com.safemode.llconnect.ui.server.ServerInfoScreen
import com.safemode.llconnect.ui.settings.SettingsScreen
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

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only allow swipe gestures while the drawer is already open (to close it).
        // This stops edge/vertical swipes from opening the drawer and fighting with
        // scrolling and pull-to-refresh; the hamburger button always opens it.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
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
                TopDestination.entries.forEach { dest ->
                    NavigationDrawerItem(
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(Routes.DASHBOARD)
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenDrawer = openDrawer,
                    onOpenVehicles = { navController.navigate(Routes.VEHICLES) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                    onEdit = { recordId ->
                        navController.navigate(Routes.recordForm(vehicleId, area, recordId))
                    },
                    onAttachments = { recordId ->
                        navController.navigate(Routes.attachments(vehicleId, area, recordId))
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
            composable(Routes.SERVER) {
                ServerInfoScreen(onOpenDrawer = openDrawer)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDrawer = openDrawer)
            }
        }
    }
}
