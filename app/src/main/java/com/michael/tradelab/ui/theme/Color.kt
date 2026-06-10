package com.michael.tradelab.ui.theme

import androidx.compose.ui.graphics.Color

// Brand — electric teal on deep graphite: technical, focused, non-casino
val Brand50 = Color(0xFFE0F7F5)
val Brand100 = Color(0xFFB3ECE8)
val Brand400 = Color(0xFF14B8A6) // primary interactive — buttons, active tabs, FAB
val Brand600 = Color(0xFF0D9488) // pressed/focused
val Brand900 = Color(0xFF064E47) // on-dark accents

// Neutrals — cool graphite (not pure black)
val Neutral50 = Color(0xFFF7F9FA)  // light page background
val Neutral100 = Color(0xFFEFF2F4) // light card surface
val Neutral200 = Color(0xFFDEE3E7) // dividers/borders
val Neutral600 = Color(0xFF5C6B73) // secondary text
val Neutral800 = Color(0xFF22282C) // dark elevated surface
val Neutral900 = Color(0xFF15191C) // primary text (light) / surfaces (dark)
val Neutral950 = Color(0xFF0C0F11) // dark page background — never #000

// Semantic — fixed, never brand-tinted
val SemanticSuccess = Color(0xFF2E7D32) // price up / filled buy
val SemanticError = Color(0xFFB00020)   // price down / filled sell
val SemanticWarning = Color(0xFFF57C00) // risk notices, disclaimer banner accent
val SemanticInfo = Color(0xFF0277BD)    // educational callouts

// On-dark variants for gain/loss text legibility
val SuccessOnDark = Color(0xFF66BB6A)
val ErrorOnDark = Color(0xFFEF5350)
