package com.surajdev.talkwise.ui.theme

import androidx.compose.ui.graphics.Color

/** Nocturne palette: ink navy, mist greys and a single mint accent. */
object Noc {
    val BgDeep = Color(0xFF070A16)
    val Bg = Color(0xFF0B1020)
    val BgLift = Color(0xFF121A33)

    val Surface = Color(0x0FFFFFFF)
    val SurfaceStrong = Color(0x1AFFFFFF)
    val Hairline = Color(0x1FFFFFFF)

    val Text = Color(0xFFEEF1F8)
    val TextDim = Color(0xFF9AA3B8)
    val TextFaint = Color(0xFF5C657C)

    val Accent = Color(0xFF5EF2C2)
    val AccentDim = Color(0x335EF2C2)
    val OnAccent = Color(0xFF06231B)

    /** Reserved for mistakes and failures, used sparingly. */
    val Warn = Color(0xFFFFA794)

    // The orb's interior only; never used for UI chrome.
    val OrbMid = Color(0xFF3B4FD8)
    val OrbDeep = Color(0xFF10163A)
}
