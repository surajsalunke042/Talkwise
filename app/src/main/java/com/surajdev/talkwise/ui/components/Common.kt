package com.surajdev.talkwise.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.surajdev.talkwise.ui.talk.Phase
import com.surajdev.talkwise.ui.theme.Noc
import com.surajdev.talkwise.ui.theme.Type
import kotlinx.coroutines.delay

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Noc.Text,
    align: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// --- Haptics: a light tick for taps, a firmer confirm or reject for outcomes. ---

fun View.hapticTick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

fun View.hapticConfirm() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

fun View.hapticReject() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )
}

/** A tap target that sinks slightly under the finger and springs back, with a haptic tick. No ripple. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val view = LocalView.current
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = null, enabled = enabled) {
        view.hapticTick()
        onClick()
    }
}

/** Fades and lifts content into place when it first appears; [order] staggers siblings. */
fun Modifier.enter(order: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * 70L)
        progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 120f))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/**
 * A pane of frosted glass: light catches the top-left edge, the body fades with depth,
 * and a soft [glow] gathers in the far corner. Drawn with gradients only, so it stays cheap to animate.
 */
fun Modifier.glass(shape: Shape = CardShape, glow: Color = Noc.Accent): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))))
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.maxDimension * 0.8f,
            ),
        )
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.07f), glow.copy(alpha = 0.32f)),
        ),
        shape = shape,
    )

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(CardShape)
            .background(Noc.Surface)
            .border(1.dp, Noc.Hairline, CardShape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .pressable(onClick = onClick)
            .height(58.dp)
            .clip(CircleShape)
            .background(Noc.Accent)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, Type.BodyStrong, color = Noc.OnAccent)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .pressable(onClick = onClick)
            .height(50.dp)
            .clip(CircleShape)
            .border(1.dp, Noc.Hairline, CircleShape)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, Type.BodyStrong, color = Noc.Text)
    }
}

/** A round, glassy icon button. */
@Composable
fun IconChip(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = Noc.TextDim, onClick: () -> Unit) {
    Box(
        modifier
            .pressable(onClick = onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(Noc.Surface)
            .border(1.dp, Noc.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint, size = 20.dp)
    }
}

/** Softly breathing placeholder bars, shown while content loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, lines: Int = 3) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(modifier.graphicsLayer { alpha = pulse }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.55f else 1f - index * 0.1f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Noc.SurfaceStrong),
            )
        }
    }
}

/** A full-screen message with the orb: used for loading, empty and error states. */
@Composable
fun OrbMessage(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    thinking: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TutorOrb(
            phase = if (thinking) Phase.THINKING else Phase.IDLE,
            modifier = Modifier.size(220.dp),
        )
        Txt(title, Type.Title, align = TextAlign.Center)
        if (detail.isNotEmpty()) {
            Txt(detail, Type.Body, Modifier.padding(top = 10.dp), color = Noc.TextDim, align = TextAlign.Center)
        }
        if (action != null) {
            GhostButton(action, Modifier.padding(top = 28.dp), onAction)
        }
    }
}

@Composable
fun LabelRow(label: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(label.uppercase(), Type.Label, Modifier.weight(1f), color = Noc.TextFaint)
        trailing()
    }
}
