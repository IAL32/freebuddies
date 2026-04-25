package com.freebuddies.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.protocol.CustomEqProfile
import com.freebuddies.app.protocol.DeviceEqPreset
import com.freebuddies.app.protocol.EqCategory
import com.freebuddies.app.protocol.EqPreset
import com.freebuddies.app.ui.components.DevicePresetRow
import com.freebuddies.app.ui.components.EqPresetRow
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.components.FindBudToggle
import com.freebuddies.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(vm: FreeBudsViewModel) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val lowLatency by vm.lowLatency.collectAsStateWithLifecycle(initialValue = null)
    val eqPreset by vm.eqPreset.collectAsStateWithLifecycle(initialValue = null)
    val eqPresetCode by vm.eqPresetCode.collectAsStateWithLifecycle(initialValue = -1)
    val devicePresets by vm.deviceEqPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    var editMode by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEqEditor by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<CustomEqProfile?>(null) }
    // Preset to restore if the user dismisses the editor without saving
    var presetBeforeEdit by remember { mutableStateOf<EqPreset?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing),
    ) {
        // Audio quality
        FbSurface {
            Text(
                text = "audio quality",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
            )
            EqPresetRow(
                label = "prioritize sound quality",
                isSelected = lowLatency == true,
                enabled = isConnected,
                onClick = { vm.setLowLatency(true) }
            )
            Spacer(Modifier.height(Dimens.RowSpacing))
            EqPresetRow(
                label = "prioritize connection quality",
                isSelected = lowLatency != true,
                enabled = isConnected,
                onClick = { vm.setLowLatency(false) }
            )
            Spacer(Modifier.height(Dimens.RowSpacing))
            FindBudToggle(
                label = "low audio latency",
                isRinging = lowLatency == true,
                onToggle = { vm.setLowLatency(it) },
                enabled = isConnected,
            )
        }

        // Specialized presets
        FbSurface {
            Text(
                text = "specialized",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
            )
            EqPreset.entries
                .filter { it.category == EqCategory.SPECIALIZED }
                .forEachIndexed { index, preset ->
                    if (index > 0) Spacer(Modifier.height(Dimens.RowSpacing))
                    EqPresetRow(
                        label = preset.label,
                        isSelected = eqPreset == preset,
                        enabled = isConnected,
                        onClick = { vm.setEqPreset(preset) }
                    )
                }
        }

        // Official presets
        FbSurface {
            Text(
                text = "official presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.TitleBottomPadding)
            )
            EqPreset.entries
                .filter { it.category == EqCategory.OFFICIAL }
                .forEachIndexed { index, preset ->
                    if (index > 0) Spacer(Modifier.height(Dimens.RowSpacing))
                    EqPresetRow(
                        label = preset.label,
                        isSelected = eqPreset == preset,
                        enabled = isConnected,
                        onClick = { vm.setEqPreset(preset) }
                    )
                }
        }

        // Custom profiles (stored on the buds)
        FbSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "custom profiles",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (devicePresets.isNotEmpty()) {
                    TextButton(onClick = { editMode = !editMode }) {
                        Text(
                            text = if (editMode) "done" else "edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentTeal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (devicePresets.isEmpty()) {
                Text(
                    text = "no custom profiles yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            devicePresets.forEachIndexed { index, preset ->
                if (index > 0) Spacer(Modifier.height(Dimens.RowSpacing))
                val isSelected = eqPresetCode == preset.id
                DevicePresetRow(
                    preset = preset,
                    isSelected = isSelected,
                    enabled = isConnected,
                    editMode = editMode,
                    onClick = {
                        vm.applyCustomEqProfile(CustomEqProfile(preset.name, preset.uiBands, preset.id))
                    },
                    onEdit = {
                        presetBeforeEdit = eqPreset
                        editingProfile = CustomEqProfile(preset.name, preset.uiBands, preset.id)
                        showEqEditor = true
                    },
                    onDelete = {
                        if (isSelected) vm.setEqPreset(EqPreset.DEFAULT)
                        vm.deleteDeviceEqPreset(preset)
                        if (devicePresets.size <= 1) editMode = false
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.RowHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(SurfaceVariant)
                    .clickable {
                        presetBeforeEdit = eqPreset
                        editingProfile = null
                        showEqEditor = true
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "+ add custom profile",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AccentTeal,
                )
            }
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

    // Custom EQ editor bottom sheet
    if (showEqEditor) {
        fun restorePreviousPreset() {
            presetBeforeEdit?.let { vm.setEqPreset(it) }
            presetBeforeEdit = null
        }

        ModalBottomSheet(
            onDismissRequest = {
                showEqEditor = false
                restorePreviousPreset()
            },
            sheetState = sheetState,
            containerColor = Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = OnDarkFaint) },
        ) {
            val usedSlots = devicePresets.map { it.id }.toSet()
            val editSlot = editingProfile?.slotCode
                ?: (0x64..0x66).first { it !in usedSlots }
            val defaultName = if (editingProfile != null) {
                editingProfile!!.name
            } else {
                "My sound effect ${devicePresets.size + 1}"
            }
            val defaultBands = editingProfile?.bands ?: List(EQ_BANDS.size) { 0 }

            CustomEqEditor(
                defaultName = defaultName,
                defaultBands = defaultBands,
                onDismiss = {
                    showEqEditor = false
                    restorePreviousPreset()
                },
                onPreview = { name, bands ->
                    vm.applyCustomEqProfile(CustomEqProfile(name, bands, editSlot))
                },
                onSave = { name, bands ->
                    presetBeforeEdit = null
                    vm.saveDeviceEqPreset(editSlot, name, bands)
                    showEqEditor = false
                },
            )
        }
    }
}
