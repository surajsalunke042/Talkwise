package com.surajdev.talkwise.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Provides the Nocturne backdrop: a deep vertical wash with a faint glow where the tutor lives. */
@Composable
fun TalkwiseTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(handleColor = Noc.Accent, backgroundColor = Noc.AccentDim),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Noc.Bg, Noc.BgDeep)))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Noc.BgLift, Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.28f),
                            radius = size.width * 0.95f,
                        ),
                    )
                },
        ) {
            content()
        }
    }
}
