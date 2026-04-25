package com.freebuddies.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.components.NcIntensitySelector
import com.freebuddies.app.ui.components.SettingRow
import com.freebuddies.app.ui.theme.*

private const val REDACTED = "--------"

@Composable
fun SettingsScreen(vm: FreeBudsViewModel) {
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle(initialValue = null)
    val preferredIntensity by vm.preferredNcIntensity.collectAsStateWithLifecycle()
    var showValues by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)) {
        FbSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "device",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { showValues = !showValues }) {
                    Icon(
                        imageVector = if (showValues) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showValues) "hide values" else "show values",
                        tint = if (showValues) AccentTeal else OnDarkMuted,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            deviceInfo?.let { info ->
                fun v(value: String) = if (showValues) value else REDACTED

                SettingRow("model", v(info.modelCode))
                SettingRow("serial", v(info.serialNumber))
                if (info.fullFirmware.isNotEmpty()) {
                    SettingRow("firmware", v(info.fullFirmware))
                } else if (info.firmwareVersion.isNotEmpty()) {
                    SettingRow("firmware", v(info.firmwareVersion))
                }
                if (info.hardwareRevision.isNotEmpty()) {
                    SettingRow("hardware", v(info.hardwareRevision))
                }
                if (info.bluetoothChip.isNotEmpty()) {
                    SettingRow("bt chip", v(info.bluetoothChip))
                }
                if (info.bluetoothFirmwareId.isNotEmpty()) {
                    SettingRow("bt firmware", v(info.bluetoothFirmwareId))
                }
                if (info.region.isNotEmpty()) {
                    SettingRow("region", v(info.region))
                }
                if (info.colorCode.isNotEmpty()) {
                    SettingRow("color code", v(info.colorCode))
                }
                if (info.budSerials.isNotEmpty()) {
                    SettingRow("bud serials", v(info.budSerials))
                }
            } ?: Text(
                "connect to your freebuds to see device information",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted
            )
        }

        FbSurface {
            Text(
                text = "noise cancelling default",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "intensity used when switching to NC from quick settings",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkMuted,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
            )
            NcIntensitySelector(
                selected = preferredIntensity,
                onSelect = { vm.setPreferredNcIntensity(it) },
                enabled = true
            )
        }
    }
}
