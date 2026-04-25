package com.freebuddies.app.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.EarTipType
import com.freebuddies.app.protocol.NcIntensity
import com.freebuddies.app.protocol.WearState
import com.freebuddies.app.ui.components.AncModeSelector
import com.freebuddies.app.ui.components.NcIntensitySelector
import com.freebuddies.app.ui.components.BudBatteryIndicator
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.components.FindBudToggle
import com.freebuddies.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(vm: FreeBudsViewModel, navController: NavController) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val batteryStatus by vm.batteryStatus.collectAsStateWithLifecycle(initialValue = null)
    val soundControl by vm.soundControl.collectAsStateWithLifecycle(initialValue = null)
    val inEarState by vm.inEarState.collectAsStateWithLifecycle(initialValue = null)
    val ringingStatus by vm.ringingStatus.collectAsStateWithLifecycle(initialValue = null)
    val preferredIntensity by vm.preferredNcIntensity.collectAsStateWithLifecycle()
    val eqPreset by vm.eqPreset.collectAsStateWithLifecycle(initialValue = null)
    val earTipType by vm.earTipType.collectAsStateWithLifecycle(initialValue = null)

    var pendingRingSide by remember { mutableIntStateOf(-1) }
    var showEarTips by remember { mutableStateOf(false) }

    if (pendingRingSide >= 0) {
        AlertDialog(
            onDismissRequest = { pendingRingSide = -1 },
            title = { Text("Start ringing?") },
            text = { Text("This will play a loud sound on the earbud. Remove it from your ear first.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRinging(side = pendingRingSide, active = true)
                    pendingRingSide = -1
                }) { Text("Ring", color = AccentTeal) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRingSide = -1 }) { Text("Cancel", color = OnDarkMuted) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
    ) {
        FbSurface {
            Text(
                text = "status",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
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
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
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

        // Sound configuration link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.RowHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { navController.navigate("sound") }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "sound configuration",
                style = MaterialTheme.typography.bodyLarge,
                color = AccentTeal,
            )
            Text(
                text = eqPreset?.label ?: "custom",
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkMuted,
            )
        }

        // Ear tips link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.RowHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = isConnected) { showEarTips = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ear tips",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isConnected) AccentTeal else OnDarkMuted,
            )
            Text(
                text = earTipType?.label ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkMuted,
            )
        }

        FbSurface {
            Text(
                text = "find my buds",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
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

    // Ear tips bottom sheet
    if (showEarTips) {
        ModalBottomSheet(
            onDismissRequest = { showEarTips = false },
            containerColor = Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = OnDarkFaint) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "ear tips",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDark,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                EarTipType.entries.forEach { type ->
                    val isSelected = earTipType == type
                    val bg = if (isSelected) AccentTeal.copy(alpha = 0.15f) else SurfaceVariant
                    val textColor = if (isSelected) AccentTeal else OnDark

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.RowHeightLarge)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(bg)
                            .clickable {
                                vm.setEarTipType(type)
                                showEarTips = false
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(AccentTeal)
                            )
                        }
                    }
                }
            }
        }
    }
}
