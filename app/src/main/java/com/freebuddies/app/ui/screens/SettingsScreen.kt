package com.freebuddies.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.components.SettingRow
import com.freebuddies.app.ui.theme.OnDarkMuted

@Composable
fun SettingsScreen(vm: FreeBudsViewModel) {
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FbSurface {
            Text(
                text = "device settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            deviceInfo?.let { info ->
                SettingRow("model", info.modelCode)
                SettingRow("serial", info.serialNumber)
                SettingRow("firmware", info.firmwareVersion)
                SettingRow("hardware", info.hardwareRevision)
            } ?: Text(
                "connect to your freebuds to see device information",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted
            )
        }
    }
}
