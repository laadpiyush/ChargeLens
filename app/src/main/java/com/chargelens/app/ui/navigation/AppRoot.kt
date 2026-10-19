package com.chargelens.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chargelens.app.ui.charging.ChargingActivity
import com.chargelens.app.ui.design.DesignScreen
import com.chargelens.app.ui.details.DetailsScreen
import com.chargelens.app.ui.details.DetailsTopBar
import com.chargelens.app.ui.details.DetailsViewModel
import com.chargelens.app.ui.history.HistoryTabsScreen
import com.chargelens.app.ui.settings.Permissions
import com.chargelens.app.ui.settings.SettingsScreen

enum class Destination(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DETAILS("details", "Details", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
    HISTORY("history", "History", Icons.Outlined.History, Icons.Filled.History),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Pages opened from another screen (no bottom-nav entry). */
private object SubPage {
    const val DESIGN = "settings/design"
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val context = LocalContext.current

    // Ask for notification permission once per launch on Android 13+ (the system stops asking
    // after two denials; Settings → Permissions offers the deep link after that).
    var asked by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.notificationsGranted(context)) {
            asked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun go(dest: Destination) = nav.navigate(dest.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomBar(current) { go(it) } },
    ) { padding ->
        NavHost(nav, startDestination = Destination.DETAILS.route, modifier = Modifier.padding(padding)) {
            composable(Destination.DETAILS.route) {
                val vm: DetailsViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                DetailsScreen(
                    state = state,
                    onToggleMonitoring = vm::toggleMonitoring,
                    topBar = {
                        DetailsTopBar(
                            menuItems = listOf(
                                "Preview charging screen" to { context.startActivity(ChargingActivity.intent(context, preview = true)) },
                                "Screen saver settings" to { Permissions.open(context, Permissions.screenSaverSettings()) },
                                "Settings" to { go(Destination.SETTINGS) },
                            ),
                        )
                    },
                )
            }
            composable(Destination.HISTORY.route) { HistoryTabsScreen() }
            composable(Destination.SETTINGS.route) { SettingsScreen(onOpenDesign = { nav.navigate(SubPage.DESIGN) }) }
            composable(SubPage.DESIGN) { DesignScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
fun BottomBar(currentRoute: String?, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        Destination.entries.forEach { dest ->
            // Sub-pages (e.g. settings/design) keep their parent tab highlighted.
            val selected = currentRoute == dest.route || currentRoute?.startsWith("${dest.route}/") == true
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
                modifier = Modifier.testTag("nav_${dest.route}"),
            )
        }
    }
}
