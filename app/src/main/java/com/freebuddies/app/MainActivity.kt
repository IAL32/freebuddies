package com.freebuddies.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freebuddies.app.protocol.AncMode
import kotlinx.coroutines.launch

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val vm by lazy { FreeBudsViewModel() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            vm.findDevice(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            val isDarkMode by vm.isDarkMode.collectAsStateWithLifecycle()
            FreebuddiesTheme(darkTheme = isDarkMode) {
                FreebuddiesApp(vm)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}

@Composable
fun FreebuddiesTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreebuddiesApp(vm: FreeBudsViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.startAutoReconnect(context)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Homepage") },
                    selected = currentRoute == "home",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (currentRoute != "settings") {
                            navController.navigate("settings")
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = currentRoute == "about",
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (currentRoute != "about") {
                            navController.navigate("about")
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Freebuddies") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home") { HomeScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
                composable("about") { AboutScreen(vm) }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: FreeBudsViewModel) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val batteryStatus by vm.batteryStatus.collectAsStateWithLifecycle(initialValue = null)
    val soundControl by vm.soundControl.collectAsStateWithLifecycle(initialValue = null)
    val inEarState by vm.inEarState.collectAsStateWithLifecycle(initialValue = null)
    val targetDevice by vm.targetDevice.collectAsStateWithLifecycle()

    var findLeftActive by remember { mutableStateOf(false) }
    var findRightActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isConnected) "Connected: ${targetDevice?.name ?: "Unknown Device"}" else "Disconnected",
                    style = MaterialTheme.typography.titleMedium
                )
                batteryStatus?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("L: ${status.leftPercent}%")
                        Text("R: ${status.rightPercent}%")
                        Text("Case: ${status.casePercent}%")
                    }
                    
                    val leftIn = inEarState?.leftInEar ?: status.leftInEar
                    val rightIn = inEarState?.rightInEar ?: status.rightInEar
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("L: ${if (leftIn) "In-Ear" else "Out"}", style = MaterialTheme.typography.bodySmall)
                        Text("R: ${if (rightIn) "In-Ear" else "Out"}", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (status.leftCharging) Text("L Charging", style = MaterialTheme.typography.bodySmall)
                        if (status.rightCharging) Text("R Charging", style = MaterialTheme.typography.bodySmall)
                        if (status.caseCharging) Text("Case Charging", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Noise Control
        Text("Noise Control", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentMode = soundControl?.mode ?: AncMode.UNKNOWN
            
            FilterChip(
                selected = isConnected && currentMode == AncMode.OFF,
                onClick = { vm.setAncMode(AncMode.OFF) },
                label = { Text("Off") },
                enabled = isConnected
            )
            FilterChip(
                selected = isConnected && currentMode == AncMode.AWARENESS,
                onClick = { vm.setAncMode(AncMode.AWARENESS) },
                label = { Text("Awareness") },
                enabled = isConnected
            )
            FilterChip(
                selected = isConnected && currentMode == AncMode.NOISE_CANCELLING,
                onClick = { vm.setAncMode(AncMode.NOISE_CANCELLING) },
                label = { Text("Noise Cancelling") },
                enabled = isConnected
            )
        }

        // Find Device (Mocked command for now)
        Text("Find Device", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { findLeftActive = !findLeftActive },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (findLeftActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (findLeftActive) "Stop Left" else "Play Left")
            }
            Button(
                onClick = { findRightActive = !findRightActive },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (findRightActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (findRightActive) "Stop Right" else "Play Right")
            }
        }
        
        if (!isConnected) {
            Button(onClick = { /* Re-scan logic */ }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Search for FreeBuds")
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: FreeBudsViewModel) {
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle(initialValue = null)
    val isDarkMode by vm.isDarkMode.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("App Settings", style = MaterialTheme.typography.titleLarge)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dark Mode")
            Switch(checked = isDarkMode, onCheckedChange = { vm.toggleDarkMode() })
        }

        HorizontalDivider()

        Text("Device Settings", style = MaterialTheme.typography.titleLarge)
        deviceInfo?.let { info ->
            Text("Model: ${info.modelCode}")
            Text("Serial: ${info.serialNumber}")
            Text("Firmware: ${info.firmwareVersion}")
            Text("Hardware: ${info.hardwareRevision}")
        } ?: Text("Connect to your FreeBuds to see device information.")
    }
}

@Composable
fun AboutScreen(@Suppress("UNUSED_PARAMETER") vm: FreeBudsViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Freebuddies", style = MaterialTheme.typography.headlineMedium)
        Text("v1.0", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "An open-source companion app for Huawei FreeBuds Pro 4.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/IAL32/freebuddies".toUri())
            context.startActivity(intent)
        }) {
            Text("GitHub Repository")
        }
        
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/IAL32".toUri())
            context.startActivity(intent)
        }) {
            Text("Author: IAL32")
        }
    }
}
