package com.freebuddies.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebuddies.app.ui.components.EqBandSlider
import com.freebuddies.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val EQ_BANDS = listOf("60", "125", "250", "500", "1K", "2K", "4K", "8K", "12K", "16K")

@Composable
internal fun CustomEqEditor(
    defaultName: String,
    defaultBands: List<Int>,
    onDismiss: () -> Unit,
    onPreview: (name: String, bands: List<Int>) -> Unit,
    onSave: (name: String, bands: List<Int>) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var bands by remember { mutableStateOf(defaultBands.map { it.toFloat() }) }
    var previewPending by remember { mutableIntStateOf(0) }

    LaunchedEffect(previewPending) {
        if (previewPending > 0) {
            delay(800)
            previewPending = 0
        }
    }

    fun sendPreview(updatedBands: List<Float>) {
        onPreview(name, updatedBands.map { it.roundToInt() })
        previewPending++
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "custom eq",
                style = MaterialTheme.typography.titleMedium,
                color = OnDark,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "close",
                    tint = OnDarkMuted,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Name input
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("effect name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = OnDarkFaint,
                focusedLabelColor = AccentTeal,
                unfocusedLabelColor = OnDarkMuted,
                cursorColor = AccentTeal,
                focusedTextColor = OnDark,
                unfocusedTextColor = OnDark,
            ),
        )

        Spacer(Modifier.height(24.dp))

        // Equalizer header with applying indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "equalizer",
                style = MaterialTheme.typography.titleSmall,
                color = OnDarkMuted,
            )
            AnimatedVisibility(
                visible = previewPending > 0,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = "applying...",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentTeal.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Band labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EQ_BANDS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Sliders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bands.forEachIndexed { index, value ->
                EqBandSlider(
                    value = value,
                    onValueChange = { newVal ->
                        val updated = bands.toMutableList().also { l -> l[index] = newVal }
                        bands = updated
                        sendPreview(updated)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Value labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bands.forEach { value ->
                val intVal = value.roundToInt()
                Text(
                    text = if (intVal > 0) "+$intVal" else "$intVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (intVal != 0) AccentTeal else OnDarkFaint,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val zeroed = List(EQ_BANDS.size) { 0f }
                    bands = zeroed
                    sendPreview(zeroed)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnDarkMuted),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            ) {
                Text("reset")
            }

            Button(
                onClick = { onSave(name, bands.map { it.roundToInt() }) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = Primary,
                ),
            ) {
                Text("save")
            }
        }
    }
}
