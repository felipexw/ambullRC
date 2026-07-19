package com.example.ambullrc.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens for the home screen redesign (feature 005) — see
// specs/005-home-screen-ux-redesign/spec.md's Design Tokens section for the source values.

val Background = Color(0xFF151210)
val SurfaceAppBar = Color(0xFF1C1917)
val SurfaceSheet = Color(0xFF211E1B)
val SurfaceHigh = Color(0xFF2A2622)

val OnSurface = Color(0xFFECE1D9)
val OnSurfaceVariant = Color(0xFFD6C3B7)
val Outline = Color(0xFF9C8B80)
val OutlineVariant = Color(0xFF4D443C)

val Accent = Color(0xFFFF9A5A)
val OnAccent = Color(0xFF3A1E00)

val ConnectingContainer = Color(0xFF4D3C00)
val OnConnectingContainer = Color(0xFFFFE08A)
val ConnectingDot = Color(0xFFFFD166)

val ConnectedContainer = Color(0xFF28401B)
val OnConnectedContainer = Color(0xFFC3E8A8)
val ConnectedDot = Color(0xFFA2D485)

val DisconnectedContainer = Color(0xFF93000A)
val OnDisconnectedContainer = Color(0xFFFFDAD6)
val DisconnectedDot = Color(0xFFFFB4AB)

// Log panel category tag color for LogCategory.CONNECTION ("BLE") — the other three categories
// reuse existing tokens above (SENT/TX = ConnectingDot, RECEIVED/RX = ConnectedDot, APP = OnSurfaceVariant).
val ConnectionTagColor = Color(0xFF8FB8FF)
