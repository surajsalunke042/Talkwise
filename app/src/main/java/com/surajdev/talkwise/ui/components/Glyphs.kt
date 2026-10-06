package com.surajdev.talkwise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { MIC, KEYBOARD, SEND, CLOSE, BACK, FORWARD, SPARK, LINES, SOUND, SOUND_OFF, PLUS, CHECK }

/** The app's own line icons, drawn on a unit square so they stay crisp at any size. */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, at(x1, y1), at(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>, close: Boolean = false) = Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s) }
            if (close) close()
        }

        when (glyph) {
            Glyph.MIC -> {
                drawRoundRect(tint, at(0.36f, 0.1f), Size(0.28f * s, 0.48f * s), CornerRadius(0.14f * s), style = stroke)
                drawArc(tint, 0f, 180f, false, at(0.22f, 0.2f), Size(0.56f * s, 0.54f * s), style = stroke)
                line(0.5f, 0.74f, 0.5f, 0.9f)
            }
            Glyph.KEYBOARD -> {
                drawRoundRect(tint, at(0.08f, 0.24f), Size(0.84f * s, 0.52f * s), CornerRadius(0.12f * s), style = stroke)
                listOf(0.28f, 0.5f, 0.72f).forEach { x -> drawCircle(tint, s * 0.045f, at(x, 0.42f)) }
                line(0.32f, 0.6f, 0.68f, 0.6f)
            }
            Glyph.SEND -> {
                line(0.5f, 0.82f, 0.5f, 0.2f)
                drawPath(path(0.24f to 0.44f, 0.5f to 0.18f, 0.76f to 0.44f), tint, style = stroke)
            }
            Glyph.CLOSE -> {
                line(0.24f, 0.24f, 0.76f, 0.76f)
                line(0.76f, 0.24f, 0.24f, 0.76f)
            }
            Glyph.BACK -> drawPath(path(0.6f to 0.2f, 0.32f to 0.5f, 0.6f to 0.8f), tint, style = stroke)
            Glyph.FORWARD -> drawPath(path(0.4f to 0.2f, 0.68f to 0.5f, 0.4f to 0.8f), tint, style = stroke)
            Glyph.SPARK -> drawSpark(tint, s)
            Glyph.LINES -> {
                line(0.18f, 0.28f, 0.82f, 0.28f)
                line(0.18f, 0.5f, 0.66f, 0.5f)
                line(0.18f, 0.72f, 0.74f, 0.72f)
            }
            Glyph.SOUND, Glyph.SOUND_OFF -> {
                drawPath(
                    path(0.12f to 0.4f, 0.28f to 0.4f, 0.48f to 0.22f, 0.48f to 0.78f, 0.28f to 0.6f, 0.12f to 0.6f, close = true),
                    tint,
                    style = stroke,
                )
                if (glyph == Glyph.SOUND) {
                    drawArc(tint, -50f, 100f, false, at(0.4f, 0.3f), Size(0.4f * s, 0.4f * s), style = stroke)
                } else {
                    line(0.64f, 0.38f, 0.88f, 0.62f)
                    line(0.88f, 0.38f, 0.64f, 0.62f)
                }
            }
            Glyph.PLUS -> {
                line(0.5f, 0.22f, 0.5f, 0.78f)
                line(0.22f, 0.5f, 0.78f, 0.5f)
            }
            Glyph.CHECK -> drawPath(path(0.2f to 0.52f, 0.42f to 0.74f, 0.8f to 0.28f), tint, style = stroke)
        }
    }
}

/** A four-point star: the streak mark. */
private fun DrawScope.drawSpark(tint: Color, s: Float) {
    val path = Path().apply {
        moveTo(0.5f * s, 0.06f * s)
        quadraticTo(0.54f * s, 0.46f * s, 0.94f * s, 0.5f * s)
        quadraticTo(0.54f * s, 0.54f * s, 0.5f * s, 0.94f * s)
        quadraticTo(0.46f * s, 0.54f * s, 0.06f * s, 0.5f * s)
        quadraticTo(0.46f * s, 0.46f * s, 0.5f * s, 0.06f * s)
        close()
    }
    drawPath(path, tint)
}
