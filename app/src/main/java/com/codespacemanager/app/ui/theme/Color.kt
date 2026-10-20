package com.codespacemanager.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Modern-monochromes Farbsystem (GitHub-Dark-Graustufen + #1F6FEB als einziger Akzent).
 * Es werden bewusst keine weiteren Farbtöne verwendet: Fehler werden über Text, Icon,
 * kräftigeren Rahmen und Bestätigungsdialoge kommuniziert.
 */
object AppPalette {
    val Canvas = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceInset = Color(0xFF010409)
    val SurfaceRaised = Color(0xFF21262D)

    val Border = Color(0xFF30363D)
    val BorderStrong = Color(0xFF484F58)

    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted = Color(0xFF7D8590)

    val Accent = Color(0xFF1F6FEB)
    val AccentHover = Color(0xFF388BFD)
    val AccentPressed = Color(0xFF1158C7)

    val OnAccent = Color(0xFFFFFFFF)
    val AccentSubtle = Color(0x221F6FEB)
    val Selection = Color(0xFF264F78)
}
