package com.michael.tradelab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // pair chips, % badges
    small = RoundedCornerShape(8.dp),       // text fields, qty steppers
    medium = RoundedCornerShape(12.dp),     // dialogs, indicator cards
    large = RoundedCornerShape(16.dp),      // portfolio cards, bottom sheets
    extraLarge = RoundedCornerShape(28.dp), // primary CTA, hero balance card
)
