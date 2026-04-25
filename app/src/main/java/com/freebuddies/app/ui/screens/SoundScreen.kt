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
import com.freebuddies.app.protocol.EqCategory
import com.freebuddies.app.protocol.EqPreset
import com.freebuddies.app.ui.components.CustomProfileRow
import com.freebuddies.app.ui.components.EqPresetRow
import com.freebuddies.app.ui.components.FbSurface
import com.freebuddies.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(vm: FreeBudsViewModel) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val eqPreset by vm.eqPreset.collectAsStateWithLifecycle(initialValue = null)
    val eqPresetCode by vm.eqPresetCode.collectAsStateWithLifecycle(initialValue = -1)
    val customProfiles by vm.customEqProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    var editMode by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEqEditor by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<CustomEqProfile?>(null) }
    // Preset to restore if the user dismisses the editor without saving
    var presetBeforeEdit by remember { mutableStateOf<EqPreset?>(null) }

    val isCustomActive = eqPreset == null && eqPresetCode >= 0x64

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Specialized presets
        FbSurface {
            Text(
                text = "specialized",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            EqPreset.entries
                .filter { it.category == EqCategory.SPECIALIZED }
                .forEachIndexed { index, preset ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
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
                modifier = Modifier.padding(bottom = 12.dp)
            )
            EqPreset.entries
                .filter { it.category == EqCategory.OFFICIAL }
                .forEachIndexed { index, preset ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    EqPresetRow(
                        label = preset.label,
                        isSelected = eqPreset == preset,
                        enabled = isConnected,
                        onClick = { vm.setEqPreset(preset) }
                    )
                }
        }

        // Custom EQ profiles
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
                if (customProfiles.isNotEmpty()) {
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

            if (customProfiles.isEmpty()) {
                Text(
                    text = "no custom profiles yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                customProfiles.forEachIndexed { index, profile ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    CustomProfileRow(
                        profile = profile,
                        isSelected = isCustomActive && customProfiles.indexOf(profile) == 0,
                        enabled = isConnected,
                        editMode = editMode,
                        onClick = { vm.applyCustomEqProfile(profile) },
                        onEdit = {
                            presetBeforeEdit = eqPreset
                            editingProfile = profile
                            showEqEditor = true
                        },
                        onDelete = {
                            if (isCustomActive) vm.setEqPreset(EqPreset.DEFAULT)
                            vm.deleteCustomEqProfile(profile)
                            if (customProfiles.size <= 1) editMode = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
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
            val defaultName = if (editingProfile != null) {
                editingProfile!!.name
            } else {
                "My sound effect ${customProfiles.size + 1}"
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
                    vm.applyCustomEqProfile(CustomEqProfile(name, bands))
                },
                onSave = { name, bands ->
                    presetBeforeEdit = null
                    val profile = CustomEqProfile(name, bands)
                    vm.saveCustomEqProfile(profile)
                    vm.applyCustomEqProfile(profile)
                    showEqEditor = false
                },
            )
        }
    }
}
