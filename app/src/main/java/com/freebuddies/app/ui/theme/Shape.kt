package com.freebuddies.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = CircleShape,          // Pill radius
    small = RoundedCornerShape(12.dp), // Button / Input radius
    medium = RoundedCornerShape(16.dp), // Card radius
    large = RoundedCornerShape(16.dp)
)
