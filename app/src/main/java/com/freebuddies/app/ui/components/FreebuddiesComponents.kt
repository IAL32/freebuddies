package com.freebuddies.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.NcIntensity
import com.freebuddies.app.ui.theme.*

@Composable
fun FbSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun LogoIcon(
    modifier: Modifier = Modifier,
    color: Color = AccentTeal
) {
    Canvas(modifier = modifier) {
        // Three concentric rings
        drawCircle(
            color = color,
            radius = size.width * 0.2f,
            style = Stroke(width = 1.dp.toPx()),
            alpha = 0.7f
        )
        drawCircle(
            color = color,
            radius = size.width * 0.35f,
            style = Stroke(width = 1.dp.toPx()),
            alpha = 0.45f
        )
        drawCircle(
            color = color,
            radius = size.width * 0.5f,
            style = Stroke(width = 1.dp.toPx()),
            alpha = 0.25f
        )
    }
}

@Composable
fun ConnectionHeader(
    deviceName: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Canvas(modifier = Modifier.size(200.dp)) {
                drawCircle(
                    color = AccentTeal,
                    radius = 40.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                    alpha = 0.7f
                )
                drawCircle(
                    color = AccentTeal,
                    radius = 60.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                    alpha = 0.45f
                )
                drawCircle(
                    color = AccentTeal,
                    radius = 80.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                    alpha = 0.25f
                )
            }
        }
        
        Text(
            text = deviceName.lowercase(),
            style = MaterialTheme.typography.displayLarge,
            color = if (isConnected) OnDark else OnDarkMuted
        )
    }
}

@Composable
fun AncModeSelector(
    selected: AncMode,
    onSelect: (AncMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val modes = listOf(
        AncMode.OFF to "off",
        AncMode.AWARENESS to "awareness",
        AncMode.NOISE_CANCELLING to "noise\ncancelling"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = (selected == mode) && enabled
            val background = if (isSelected) {
                Brush.linearGradient(listOf(AccentTeal, AccentBlue))
            } else {
                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(background)
                    .clickable(enabled = enabled) { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Primary else OnDarkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
fun NcIntensitySelector(
    selected: NcIntensity,
    onSelect: (NcIntensity) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NcIntensity.entries.forEach { intensity ->
            val isSelected = (selected == intensity) && enabled
            val background = if (isSelected) {
                Brush.linearGradient(listOf(AccentTeal, AccentBlue))
            } else {
                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(background)
                    .clickable(enabled = enabled) { onSelect(intensity) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = intensity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Primary else OnDarkMuted
                )
            }
        }
    }
}

@Composable
fun BudBatteryIndicator(
    percent: Int,
    charging: Boolean,
    inEar: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                // Background track
                drawArc(
                    color = OnDarkFaint,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                // Progress arc
                drawArc(
                    color = if (percent < 20) Warning else Accent,
                    startAngle = -90f,
                    sweepAngle = (percent / 100f) * 360f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                
                if (charging) {
                    // Small dot for charging
                    drawCircle(
                        color = Success,
                        radius = 4.dp.toPx(),
                        center = Offset(size.width - 4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            Text(
                text = percent.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = OnDark
            )
        }
        Text(
            text = "$label ${if (inEar) "in" else "out"}",
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkMuted
        )
    }
}

@Composable
fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
            .background(if (isSelected) SurfaceVariant else Color.Transparent)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(AccentTeal)
                    .align(Alignment.CenterStart)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AccentTeal else OnDarkMuted,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) AccentTeal else OnDark
            )
        }
    }
}

@Composable
fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnDarkMuted)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = OnDark)
    }
}

@Composable
fun FindBudToggle(
    label: String,
    isRinging: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(SurfaceVariant)
            .clickable(enabled = enabled) { onToggle(!isRinging) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) OnDark else OnDarkMuted
        )

        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 24.dp)
                .clip(CircleShape)
                .background(if (isRinging) AccentTeal else OnDarkFaint)
                .padding(2.dp),
            contentAlignment = if (isRinging) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isRinging) Primary else OnDarkMuted)
            )
        }
    }
}
