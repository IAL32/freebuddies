package com.freebuddies.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.freebuddies.app.protocol.CustomEqProfile
import com.freebuddies.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun EqPresetRow(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) AccentTeal.copy(alpha = 0.15f) else SurfaceVariant
    val textColor = when {
        isSelected -> AccentTeal
        enabled -> OnDark
        else -> OnDarkMuted
    }
    val indicator = if (isSelected) AccentTeal else OnDarkFaint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(indicator)
        )
    }
}

@Composable
fun CustomProfileRow(
    profile: CustomEqProfile,
    isSelected: Boolean,
    enabled: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val bg = if (isSelected) AccentTeal.copy(alpha = 0.15f) else SurfaceVariant
    val textColor = when {
        isSelected -> AccentTeal
        enabled -> OnDark
        else -> OnDarkMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .clickable(enabled = enabled && !editMode) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (editMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "edit", tint = OnDarkMuted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "delete", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            val indicator = if (isSelected) AccentTeal else OnDarkFaint
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(indicator)
            )
        }
    }
}

@Composable
fun EqBandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = { onValueChange(it.roundToInt().toFloat()) },
        valueRange = -6f..6f,
        steps = 11,
        modifier = modifier
            .graphicsLayer { rotationZ = -90f }
            .layout { measurable, constraints ->
                val swapped = Constraints(
                    minWidth = constraints.minHeight,
                    maxWidth = constraints.maxHeight,
                    minHeight = constraints.minWidth,
                    maxHeight = constraints.maxWidth,
                )
                val placeable = measurable.measure(swapped)
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width - placeable.height) / 2,
                        y = -(placeable.height - placeable.width) / 2,
                    )
                }
            },
        colors = SliderDefaults.colors(
            thumbColor = AccentTeal,
            activeTrackColor = AccentTeal,
            inactiveTrackColor = OnDarkFaint,
        ),
    )
}
