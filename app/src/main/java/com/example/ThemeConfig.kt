package com.example

import androidx.compose.ui.graphics.Color

enum class PrayerTheme(
    val id: String,
    val displayName: String,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val textOnBg: Color,
    val textSub: Color,
    val textMuted: Color,
    val hasAmbientStars: Boolean,
    val startColor: Color,
    val endColor: Color
) {
    MIDNIGHT(
        id = "midnight",
        displayName = "Midnight Gold",
        primary = Color(0xFFC9A84C),
        primaryVariant = Color(0xFFE8C97A),
        secondary = Color(0xFF2E8B7A),
        background = Color(0xFF0A0C14),
        surface = Color(0xFF121520),
        textOnBg = Color(0xFFE8E0D0),
        textSub = Color(0xFFA09070),
        textMuted = Color(0xFF5A5040),
        hasAmbientStars = true,
        startColor = Color(0xFF121520),
        endColor = Color(0xFF1C2030)
    ),
    EMERALD(
        id = "emerald",
        displayName = "Emerald Mosque",
        primary = Color(0xFF2ECC8A),
        primaryVariant = Color(0xFF4DE8A4),
        secondary = Color(0xFFE8B84C),
        background = Color(0xFF051510),
        surface = Color(0xFF0A2018),
        textOnBg = Color(0xFFD0F0E0),
        textSub = Color(0xFF70A888),
        textMuted = Color(0xFF3A6050),
        hasAmbientStars = false,
        startColor = Color(0xFF0A2018),
        endColor = Color(0xFF0F2D22)
    ),
    DESERT(
        id = "desert",
        displayName = "Desert Sand",
        primary = Color(0xFF8B4513),
        primaryVariant = Color(0xFFA05020),
        secondary = Color(0xFF1A6B5A),
        background = Color(0xFFF5EDE0),
        surface = Color(0xFFEDE0CC),
        textOnBg = Color(0xFF2A1A0A),
        textSub = Color(0xFF6B4A2A),
        textMuted = Color(0xFFA08060),
        hasAmbientStars = false,
        startColor = Color(0xFFEDE0CC),
        endColor = Color(0xFFE0CFB4)
    ),
    SAPPHIRE(
        id = "sapphire",
        displayName = "Sapphire Night",
        primary = Color(0xFF4A8FFF),
        primaryVariant = Color(0xFF7AADFF),
        secondary = Color(0xFFFF9F40),
        background = Color(0xFF060A18),
        surface = Color(0xFF0C1228),
        textOnBg = Color(0xFFD0DCF5),
        textSub = Color(0xFF7090C0),
        textMuted = Color(0xFF3A5080),
        hasAmbientStars = true,
        startColor = Color(0xFF0C1228),
        endColor = Color(0xFF141A38)
    ),
    CRIMSON(
        id = "crimson",
        displayName = "Crimson Dusk",
        primary = Color(0xFFE05050),
        primaryVariant = Color(0xFFF07878),
        secondary = Color(0xFFE8B84C),
        background = Color(0xFF120808),
        surface = Color(0xFF1E0E0E),
        textOnBg = Color(0xFFF0D8D8),
        textSub = Color(0xFFA07070),
        textMuted = Color(0xFF604040),
        hasAmbientStars = true,
        startColor = Color(0xFF1E0E0E),
        endColor = Color(0xFF281414)
    );

    companion object {
        fun fromId(id: String): PrayerTheme =
            values().firstOrNull { it.id == id } ?: MIDNIGHT
    }
}
