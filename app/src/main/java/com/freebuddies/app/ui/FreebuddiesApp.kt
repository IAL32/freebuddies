package com.freebuddies.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.ui.components.ConnectionHeader
import com.freebuddies.app.ui.components.DrawerItem
import com.freebuddies.app.ui.components.LogoIcon
import com.freebuddies.app.ui.screens.AboutScreen
import com.freebuddies.app.ui.screens.HomeScreen
import com.freebuddies.app.ui.screens.SettingsScreen
import com.freebuddies.app.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreebuddiesApp(vm: FreeBudsViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val context = LocalContext.current

    val targetDevice by vm.targetDevice.collectAsStateWithLifecycle()
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(Unit) {
        vm.startAutoReconnect(context)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "freebuddies",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnDark
                            )
                            Spacer(Modifier.width(8.dp))
                            LogoIcon(modifier = Modifier.size(20.dp))
                        }
                    }

                    ConnectionHeader(
                        deviceName = targetDevice?.name ?: "freebuddies",
                        isConnected = isConnected
                    )
                    
                    DrawerItem(
                        icon = Icons.Default.Home,
                        label = "homepage",
                        isSelected = currentRoute == "home",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                    DrawerItem(
                        icon = Icons.Default.Settings,
                        label = "settings",
                        isSelected = currentRoute == "settings",
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute != "settings") {
                                navController.navigate("settings")
                            }
                        }
                    )
                    DrawerItem(
                        icon = Icons.Default.Info,
                        label = "about",
                        isSelected = currentRoute == "about",
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute != "about") {
                                navController.navigate("about")
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "freebuddies",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            LogoIcon(modifier = Modifier.size(20.dp))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "menu",
                                tint = OnDark
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = OnDark
                    )
                )
            },
            containerColor = Color.Transparent // Background is handled by the gradient root
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Primary, PrimaryVariant)))
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") { HomeScreen(vm) }
                    composable("settings") { SettingsScreen(vm) }
                    composable("about") { AboutScreen(vm) }
                }
            }
        }
    }
}
