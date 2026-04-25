package com.freebuddies.app.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.NcIntensity
import com.freebuddies.app.protocol.WearState
import com.freebuddies.app.ui.components.AncModeSelector
import com.freebuddies.app.ui.components.NcIntensitySelector
import com.freebuddies.app.ui.components.BudBatteryIndicator
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.components.FindBudToggle
import com.freebuddies.app.ui.theme.*

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(vm: FreeBudsViewModel) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val batteryStatus by vm.batteryStatus.collectAsStateWithLifecycle(initialValue = null)
    val soundControl by vm.soundControl.collectAsStateWithLifecycle(initialValue = null)
    val inEarState by vm.inEarState.collectAsStateWithLifecycle(initialValue = null)
    val ringingStatus by vm.ringingStatus.collectAsStateWithLifecycle(initialValue = null)
    val preferredIntensity by vm.preferredNcIntensity.collectAsStateWithLifecycle()

    var pendingRingSide by remember { mutableIntStateOf(-1) }

    if (pendingRingSide >= 0) {
        AlertDialog(
            onDismissRequest = { pendingRingSide = -1 },
            title = { Text("Start ringing?") },
            text = { Text("This will play a loud sound on the earbud. Remove it from your ear first.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRinging(side = pendingRingSide, active = true)
                    pendingRingSide = -1
                }) { Text("Ring") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRingSide = -1 }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FbSurface {
            Text(
                text = "status",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val leftWear = inEarState?.left
                    ?: if (batteryStatus?.leftInEar == true) WearState.IN_EAR else WearState.OUT
                val rightWear = inEarState?.right
                    ?: if (batteryStatus?.rightInEar == true) WearState.IN_EAR else WearState.OUT

                BudBatteryIndicator(
                    percent = batteryStatus?.leftPercent ?: 0,
                    charging = batteryStatus?.leftCharging ?: false,
                    wearState = leftWear,
                    label = "left"
                )
                BudBatteryIndicator(
                    percent = batteryStatus?.casePercent ?: 0,
                    charging = batteryStatus?.caseCharging ?: false,
                    wearState = null,
                    label = "case"
                )
                BudBatteryIndicator(
                    percent = batteryStatus?.rightPercent ?: 0,
                    charging = batteryStatus?.rightCharging ?: false,
                    wearState = rightWear,
                    label = "right"
                )
            }
        }

        FbSurface {
            Text(
                text = "noise control",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val currentMode = soundControl?.mode ?: AncMode.OFF
            val currentIntensity = soundControl?.ncIntensity ?: NcIntensity.GENERAL
            val currentVoice = soundControl?.voiceMode ?: false

            AncModeSelector(
                selected = currentMode,
                onSelect = { mode ->
                    val intensity = if (mode == AncMode.NOISE_CANCELLING) preferredIntensity else currentIntensity
                    vm.setAncMode(mode, intensity, currentVoice)
                },
                enabled = isConnected
            )

            if (currentMode == AncMode.NOISE_CANCELLING) {
                Spacer(Modifier.height(8.dp))
                NcIntensitySelector(
                    selected = currentIntensity,
                    onSelect = { vm.setAncMode(AncMode.NOISE_CANCELLING, it) },
                    enabled = isConnected
                )
            }

            if (currentMode == AncMode.AWARENESS) {
                Spacer(Modifier.height(8.dp))
                FindBudToggle(
                    label = "voice mode",
                    isRinging = currentVoice,
                    onToggle = { vm.setAncMode(AncMode.AWARENESS, voiceMode = it) },
                    enabled = isConnected
                )
            }
        }

        FbSurface {
            Text(
                text = "find my buds",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            FindBudToggle(
                label = "left bud",
                isRinging = ringingStatus?.left ?: false,
                onToggle = { active ->
                    if (active) pendingRingSide = 0 else vm.setRinging(side = 0, active = false)
                },
                enabled = isConnected
            )

            Spacer(Modifier.height(8.dp))

            FindBudToggle(
                label = "right bud",
                isRinging = ringingStatus?.right ?: false,
                onToggle = { active ->
                    if (active) pendingRingSide = 1 else vm.setRinging(side = 1, active = false)
                },
                enabled = isConnected
            )
        }

        if (!isConnected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = "disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
