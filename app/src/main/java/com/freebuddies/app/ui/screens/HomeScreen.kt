package com.freebuddies.app.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.protocol.*
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
    val pairedDevices by vm.pairedDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    val caseTone by vm.caseTone.collectAsStateWithLifecycle(initialValue = null)
    val wearDetection by vm.wearDetection.collectAsStateWithLifecycle(initialValue = null)
    val headControl by vm.headControl.collectAsStateWithLifecycle(initialValue = null)
    val nodAction by vm.nodAction.collectAsStateWithLifecycle(initialValue = null)
    val shakeAction by vm.shakeAction.collectAsStateWithLifecycle(initialValue = null)

    val bothInCase = inEarState?.let {
        it.left == WearState.IN_CASE && it.right == WearState.IN_CASE
    } ?: false

    var pendingRingSide by remember { mutableIntStateOf(-1) }
    var showEarTips by remember { mutableStateOf(false) }
    var showHeadControl by remember { mutableStateOf(false) }
    var showCaseToneHint by remember { mutableStateOf(false) }

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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showCaseToneHint) {
        if (showCaseToneHint) {
            snackbarHostState.showSnackbar("Place both buds in the case to change this setting")
            showCaseToneHint = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
    ) {
        // Status + Find My Buds
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

                // Left bud + find
                BudWithFind(
                    percent = batteryStatus?.leftPercent ?: 0,
                    charging = batteryStatus?.leftCharging ?: false,
                    wearState = leftWear,
                    label = "left",
                    isRinging = ringingStatus?.left ?: false,
                    enabled = isConnected,
                    onToggleRing = { active ->
                        if (active) pendingRingSide = 0 else vm.setRinging(side = 0, active = false)
                    },
                )
                // Case
                BudBatteryIndicator(
                    percent = batteryStatus?.casePercent ?: 0,
                    charging = batteryStatus?.caseCharging ?: false,
                    wearState = null,
                    label = "case"
                )
                // Right bud + find
                BudWithFind(
                    percent = batteryStatus?.rightPercent ?: 0,
                    charging = batteryStatus?.rightCharging ?: false,
                    wearState = rightWear,
                    label = "right",
                    isRinging = ringingStatus?.right ?: false,
                    enabled = isConnected,
                    onToggleRing = { active ->
                        if (active) pendingRingSide = 1 else vm.setRinging(side = 1, active = false)
                    },
                )
            }
        }

        // Paired devices
        if (pairedDevices.isNotEmpty()) {
            FbSurface {
                Text(
                    text = "paired devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
                )
                pairedDevices.forEachIndexed { index, device ->
                    if (index > 0) Spacer(Modifier.height(Dimens.RowSpacing))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.RowHeight)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(SurfaceVariant)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (device.connected) AccentTeal else OnDarkMuted,
                        )
                        if (device.connected && device.isPlaying) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "playing",
                                tint = AccentTeal,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Noise control
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

        // Sound configuration + Ear tips (side by side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
        ) {
            LinkButton(
                label = "sound",
                value = eqPreset?.label ?: "custom",
                onClick = { navController.navigate("sound") },
                enabled = true,
                modifier = Modifier.weight(1f),
            )
            LinkButton(
                label = "ear tips",
                value = earTipType?.label ?: "",
                onClick = { showEarTips = true },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
            )
        }

        // Head control
        LinkButton(
            label = "head control",
            value = if (headControl == true) "on" else "off",
            onClick = { showHeadControl = true },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth(),
        )

        // Charging case tone + Smart wear detection (side by side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
        ) {
            ToggleButton(
                label = "case tone",
                isOn = caseTone == true,
                onToggle = { vm.setCaseTone(it) },
                enabled = isConnected && bothInCase,
                onDisabledTap = if (isConnected && !bothInCase) {
                    { showCaseToneHint = true }
                } else null,
                modifier = Modifier.weight(1f),
            )
            ToggleButton(
                label = "wear detection",
                isOn = wearDetection == true,
                onToggle = { vm.setWearDetection(it) },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
            )
        }

        if (!isConnected) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                )
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    )
    } // Box

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
                                    .size(Dimens.DotSize)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(AccentTeal)
                            )
                        }
                    }
                }
            }
        }
    }

    // Head control bottom sheet
    if (showHeadControl) {
        ModalBottomSheet(
            onDismissRequest = { showHeadControl = false },
            containerColor = Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = OnDarkFaint) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.RowSpacing),
            ) {
                Text(
                    text = "head control",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDark,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                FindBudToggle(
                    label = "head control",
                    isRinging = headControl == true,
                    onToggle = { vm.setHeadControl(it) },
                    enabled = isConnected,
                )

                if (headControl == true) {
                    Spacer(Modifier.height(8.dp))

                    GestureDropdown(
                        label = "nod head",
                        selected = nodAction,
                        onSelect = { vm.setNodAction(it) },
                    )

                    GestureDropdown(
                        label = "shake head",
                        selected = shakeAction,
                        onSelect = { vm.setShakeAction(it) },
                    )
                }
            }
        }
    }
}

/** Battery indicator with a small find-my-buds icon below it. */
@Composable
private fun BudWithFind(
    percent: Int,
    charging: Boolean,
    wearState: WearState,
    label: String,
    isRinging: Boolean,
    enabled: Boolean,
    onToggleRing: (Boolean) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BudBatteryIndicator(
            percent = percent,
            charging = charging,
            wearState = wearState,
            label = label,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isRinging) AccentTeal.copy(alpha = 0.2f) else SurfaceVariant)
                .clickable(enabled = enabled) { onToggleRing(!isRinging) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isRinging) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (isRinging) "stop ringing" else "ring $label",
                tint = if (isRinging) AccentTeal else OnDarkFaint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Compact link row with label + current value. */
@Composable
private fun LinkButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(Dimens.RowHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) AccentTeal else OnDarkMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkMuted,
        )
    }
}

/** Compact toggle card for side-by-side layout. */
@Composable
private fun ToggleButton(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDisabledTap: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(Dimens.RowHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                if (enabled) onToggle(!isOn)
                else onDisabledTap?.invoke()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) OnDark else OnDarkMuted,
        )
        Box(
            modifier = Modifier
                .size(Dimens.DotSize)
                .clip(CircleShape)
                .background(if (isOn) AccentTeal else OnDarkFaint)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureDropdown(
    label: String,
    selected: HeadGestureAction?,
    onSelect: (HeadGestureAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.RowHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(SurfaceVariant)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = OnDark,
            )
            Text(
                text = selected?.label ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = AccentTeal,
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Surface,
        ) {
            HeadGestureAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action == selected) AccentTeal else OnDark,
                        )
                    },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                )
            }
        }
    }
}
