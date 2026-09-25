package com.ajkueterman.nanodungeon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val Torchlight = Color(0xFFE8B05A)
private val Soot = Color(0xFF15110E)
private val Stone = Color(0xFF231D18)
private val StoneLight = Color(0xFF332A23)
private val Bone = Color(0xFFEDE3D3)
private val Ash = Color(0xFFB3A796)

/** Colors for each `Scene.mood`. The model picks the mood and the UI tints the room card to match. */
object MoodColors {
    val calm = Color(0xFF6E8F72)
    val eerie = Color(0xFF7D6FA8)
    val dangerous = Color(0xFFC0503F)
}

private val DungeonColors = darkColorScheme(
    primary = Torchlight,
    onPrimary = Soot,
    background = Soot,
    onBackground = Bone,
    surface = Soot,
    onSurface = Bone,
    surfaceContainer = Stone,
    surfaceContainerHigh = StoneLight,
    onSurfaceVariant = Ash,
    outline = StoneLight,
)

private val DungeonType = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.Serif),
        bodyLarge = bodyLarge.copy(fontFamily = FontFamily.Serif),
    )
}

@Composable
fun NanoDungeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DungeonColors, typography = DungeonType, content = content)
}
