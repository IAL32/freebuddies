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
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle(initialValue = null)
    val batteryStatus by vm.batteryStatus.collectAsStateWithLifecycle(initialValue = null)
    val inEarState by vm.inEarState.collectAsStateWithLifecycle(initialValue = null)
    val soundControl by vm.soundControl.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FbSurface {
            Text(
                text = "device",
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

        FbSurface {
            Text(
                text = "battery",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            batteryStatus?.let { bat ->
                SettingRow("left", "${bat.leftPercent}%" + if (bat.leftCharging) " (charging)" else "")
                SettingRow("right", "${bat.rightPercent}%" + if (bat.rightCharging) " (charging)" else "")
                SettingRow("case", "${bat.casePercent}%" + if (bat.caseCharging) " (charging)" else "")
            } ?: Text(
                "no battery data",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted
            )
        }

        FbSurface {
            Text(
                text = "status",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            SettingRow("connection", if (isConnected) "connected" else "disconnected")

            val leftIn = inEarState?.leftInEar ?: batteryStatus?.leftInEar
            val rightIn = inEarState?.rightInEar ?: batteryStatus?.rightInEar
            if (leftIn != null && rightIn != null) {
                SettingRow("left ear", if (leftIn) "in" else "out")
                SettingRow("right ear", if (rightIn) "in" else "out")
            }

            soundControl?.let { sc ->
                val ancLabel = when (sc.mode) {
                    com.freebuddies.app.protocol.AncMode.NOISE_CANCELLING -> "NC (${sc.ncIntensity.label})"
                    com.freebuddies.app.protocol.AncMode.AWARENESS -> "awareness" + if (sc.voiceMode) " (voice)" else ""
                    com.freebuddies.app.protocol.AncMode.OFF -> "off"
                    else -> "unknown"
                }
                SettingRow("noise control", ancLabel)
            }
        }
    }
}
