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
import com.freebuddies.app.ui.components.*
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
    val voiceLanguage by vm.voiceLanguage.collectAsStateWithLifecycle(initialValue = null)
    val voiceLanguages by vm.voiceLanguages.collectAsStateWithLifecycle(initialValue = emptyList())
    val doubleTap by vm.doubleTap.collectAsStateWithLifecycle(initialValue = null)
    val tripleTap by vm.tripleTap.collectAsStateWithLifecycle(initialValue = null)
    val longTap by vm.longTap.collectAsStateWithLifecycle(initialValue = null)
    val swipe by vm.swipe.collectAsStateWithLifecycle(initialValue = null)

    val bothInCase = inEarState?.let {
        it.left == WearState.IN_CASE && it.right == WearState.IN_CASE
    } ?: false

    var pendingRingSide by remember { mutableIntStateOf(-1) }
    var showEarTips by remember { mutableStateOf(false) }
    var showGestures by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
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
                BudBatteryIndicator(
                    percent = batteryStatus?.casePercent ?: 0,
                    charging = batteryStatus?.caseCharging ?: false,
                    wearState = null,
                    label = "case"
                )
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

        // Sound + Ear tips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
        ) {
            LinkButton("sound", eqPreset?.label ?: "custom", { navController.navigate("sound") }, true, Modifier.weight(1f))
            LinkButton("ear tips", earTipType?.label ?: "", { showEarTips = true }, isConnected, Modifier.weight(1f))
        }

        // Gestures + Voice language
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
        ) {
            LinkButton("gestures", "tap, hold, swipe", { vm.refreshGestureConfig(); showGestures = true }, isConnected, Modifier.weight(1f))
            LinkButton("voice language", voiceLanguage ?: "", { showLanguagePicker = true }, isConnected && voiceLanguages.isNotEmpty(), Modifier.weight(1f))
        }

        // Case tone + Wear detection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
        ) {
            ToggleButton(
                label = "case tone",
                isOn = caseTone == true,
                onToggle = { vm.setCaseTone(it) },
                enabled = isConnected && bothInCase,
                onDisabledTap = if (isConnected && !bothInCase) { { showCaseToneHint = true } } else null,
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
                Text("disconnected", style = MaterialTheme.typography.bodyMedium, color = OnDarkMuted)
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    )
    } // Box

    // --- Bottom sheets ---

    if (showEarTips) {
        PickerSheet(
            title = "ear tips",
            items = EarTipType.entries.toList(),
            selected = earTipType,
            labelOf = { it.label },
            onSelect = { vm.setEarTipType(it); showEarTips = false },
            onDismiss = { showEarTips = false },
        )
    }

    if (showLanguagePicker && voiceLanguages.isNotEmpty()) {
        PickerSheet(
            title = "voice language",
            items = voiceLanguages,
            selected = voiceLanguage,
            labelOf = { it },
            onSelect = { vm.setVoiceLanguage(it); showLanguagePicker = false },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showGestures) {
        GesturesSheet(
            doubleTap = doubleTap,
            tripleTap = tripleTap,
            longTap = longTap,
            swipe = swipe,
            headControl = headControl,
            nodAction = nodAction,
            shakeAction = shakeAction,
            isConnected = isConnected,
            onSetDoubleTap = vm::setDoubleTap,
            onSetTripleTap = vm::setTripleTap,
            onSetLongTap = vm::setLongTap,
            onSetSwipe = vm::setSwipe,
            onSetHeadControl = vm::setHeadControl,
            onSetNodAction = vm::setNodAction,
            onSetShakeAction = vm::setShakeAction,
            onDismiss = { showGestures = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Extracted sheet composables
// ---------------------------------------------------------------------------

/** Generic bottom-sheet picker for a list of items. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSheet(
    title: String,
    items: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnDark, modifier = Modifier.padding(bottom = 8.dp))
            items.forEach { item ->
                val isSelected = item == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.RowHeightLarge)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (isSelected) AccentTeal.copy(alpha = 0.15f) else SurfaceVariant)
                        .clickable { onSelect(item) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(labelOf(item), style = MaterialTheme.typography.bodyLarge, color = if (isSelected) AccentTeal else OnDark)
                    if (isSelected) {
                        Box(Modifier.size(Dimens.DotSize).clip(MaterialTheme.shapes.extraSmall).background(AccentTeal))
                    }
                }
            }
        }
    }
}

/** Gestures & head control bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GesturesSheet(
    doubleTap: TapGestureConfig?,
    tripleTap: TapGestureConfig?,
    longTap: LongTapConfig?,
    swipe: SwipeConfig?,
    headControl: Boolean?,
    nodAction: HeadGestureAction?,
    shakeAction: HeadGestureAction?,
    isConnected: Boolean,
    onSetDoubleTap: (Int, TapAction) -> Unit,
    onSetTripleTap: (Int, TapAction) -> Unit,
    onSetLongTap: (Int, LongTapAction) -> Unit,
    onSetSwipe: (Boolean) -> Unit,
    onSetHeadControl: (Boolean) -> Unit,
    onSetNodAction: (HeadGestureAction) -> Unit,
    onSetShakeAction: (HeadGestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnDarkFaint) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.RowSpacing),
        ) {
            SectionTitle("double tap")
            FbDropdown("left", doubleTap?.left, TapAction.entries, { it.label }) { onSetDoubleTap(1, it) }
            FbDropdown("right", doubleTap?.right, TapAction.entries, { it.label }) { onSetDoubleTap(2, it) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("triple tap")
            FbDropdown("left", tripleTap?.left, TapAction.entries, { it.label }) { onSetTripleTap(1, it) }
            FbDropdown("right", tripleTap?.right, TapAction.entries, { it.label }) { onSetTripleTap(2, it) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("long tap (ANC cycle)")
            FbDropdown("left", longTap?.left, LongTapAction.entries, { it.label }) { onSetLongTap(1, it) }
            FbDropdown("right", longTap?.right, LongTapAction.entries, { it.label }) { onSetLongTap(2, it) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("swipe")
            FindBudToggle("volume control", swipe?.enabled == true, onSetSwipe, isConnected)

            Spacer(Modifier.height(8.dp))
            SectionTitle("head control")
            FindBudToggle("head control", headControl == true, onSetHeadControl, isConnected)
            if (headControl == true) {
                FbDropdown("nod head", nodAction, HeadGestureAction.entries, { it.label }, onSetNodAction)
                FbDropdown("shake head", shakeAction, HeadGestureAction.entries, { it.label }, onSetShakeAction)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = OnDark, modifier = Modifier.padding(bottom = 4.dp))
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
