package com.surajdev.talkwise.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.surajdev.talkwise.ui.talk.Phase
import com.surajdev.talkwise.ui.theme.Noc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Everything the orb needs to draw one frame. Mutated in place so animating allocates nothing. */
private class OrbFrame {
    var time = 0f
    var flow = 0f
    var spin = 0f
    var amp = 0f
    var listen = 0f
    var think = 0f
    var speak = 0f
}

/**
 * The tutor's presence. It breathes when idle, swells with the learner's voice when listening,
 * tightens and swirls when thinking, and pulses with each word when speaking.
 *
 * @param level current loudness, 0..1: the microphone while listening, the tutor's voice while speaking.
 */
@Composable
fun TutorOrb(phase: Phase, modifier: Modifier = Modifier, level: () -> Float = { 0f }) {
    val settle = spring<Float>(dampingRatio = 0.85f, stiffness = 70f)
    val listen by animateFloatAsState(if (phase == Phase.LISTENING) 1f else 0f, settle, label = "listen")
    val think by animateFloatAsState(if (phase == Phase.THINKING) 1f else 0f, settle, label = "think")
    val speak by animateFloatAsState(if (phase == Phase.SPEAKING) 1f else 0f, settle, label = "speak")
    val currentLevel by rememberUpdatedState(level)

    val frame = remember { OrbFrame() }
    var tick by remember { mutableLongStateOf(0L) }
    val shaderOrb = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { ShaderOrb() }.getOrNull() else null
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                frame.listen = listen
                frame.think = think
                frame.speak = speak
                frame.time += dt
                // Speech engines that do not report word timing still get a gentle talking rhythm.
                val rhythm = speak * (0.3f + 0.3f * abs(sin(frame.time * 7f) * sin(frame.time * 2.3f)))
                val target = maxOf(currentLevel().coerceIn(0f, 1f), rhythm)
                frame.amp += (target - frame.amp) * min(1f, dt * 12f)
                // Phase and spin are integrated here so that changing speed never makes the pattern jump.
                frame.flow += dt * (0.35f + 1.3f * think + 0.9f * speak * frame.amp + 0.7f * listen * frame.amp)
                frame.spin += dt * think * 1.7f
                tick = now
            }
        }
    }

    Canvas(modifier) {
        tick // Redraw every frame.
        if (shaderOrb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shaderOrb.draw(this, frame)
        } else {
            drawGradientOrb(frame)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderOrb {
    private val shader = RuntimeShader(ORB_SHADER)
    private val paint = Paint()

    init {
        shader.setFloatUniform("uAccent", Noc.Accent.red, Noc.Accent.green, Noc.Accent.blue)
        shader.setFloatUniform("uMid", Noc.OrbMid.red, Noc.OrbMid.green, Noc.OrbMid.blue)
        shader.setFloatUniform("uDeep", Noc.OrbDeep.red, Noc.OrbDeep.green, Noc.OrbDeep.blue)
    }

    fun draw(scope: DrawScope, frame: OrbFrame) = with(scope) {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", frame.time)
        shader.setFloatUniform("uFlow", frame.flow)
        shader.setFloatUniform("uSpin", frame.spin)
        shader.setFloatUniform("uAmp", frame.amp)
        shader.setFloatUniform("uListen", frame.listen)
        shader.setFloatUniform("uThink", frame.think)
        shader.setFloatUniform("uSpeak", frame.speak)
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

/** Fallback for devices below Android 13, which cannot run AGSL shaders. */
private fun DrawScope.drawGradientOrb(frame: OrbFrame) {
    val unit = size.minDimension
    val live = maxOf(frame.listen, frame.speak)
    val radius = unit * (0.25f + 0.006f * sin(frame.time * 1.3f) + frame.listen * frame.amp * 0.045f +
        frame.speak * frame.amp * 0.03f - frame.think * 0.018f)

    drawCircle(
        brush = Brush.radialGradient(
            0f to Noc.Accent.copy(alpha = 0.30f + 0.3f * live * frame.amp + 0.1f * frame.think),
            radius / (unit * 0.5f) to Noc.Accent.copy(alpha = 0.22f + 0.25f * live * frame.amp),
            1f to Color.Transparent,
            center = center,
            radius = unit * 0.5f,
        ),
        radius = unit * 0.5f,
    )
    val angle = frame.flow * 0.7f + frame.spin
    val drift = Offset(cos(angle), sin(angle)) * (radius * 0.4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Noc.Accent, Noc.OrbMid, Noc.OrbDeep),
            center = center + drift,
            radius = radius * 1.5f,
        ),
        radius = radius,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to Noc.Accent.copy(alpha = 0.45f),
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            center = center + Offset(-0.08f, -0.1f) * unit,
            radius = unit * 0.14f,
        ),
        radius = unit * 0.14f,
        center = center + Offset(-0.08f, -0.1f) * unit,
    )
}

// A lit sphere of slow, domain-warped noise with a soft halo. Three noise octaves keep it cheap on mid-range GPUs.
private const val ORB_SHADER = """
uniform float2 uRes;
uniform float uTime;
uniform float uFlow;
uniform float uSpin;
uniform float uAmp;
uniform float uListen;
uniform float uThink;
uniform float uSpeak;
uniform float3 uAccent;
uniform float3 uMid;
uniform float3 uDeep;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * uRes) / min(uRes.x, uRes.y);
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float live = max(uListen, uSpeak);

    float wobble = 0.006 * sin(ang * 3.0 + uTime * 0.9)
        + uListen * uAmp * 0.022 * sin(ang * 5.0 - uTime * 6.0)
        + uSpeak * uAmp * 0.016 * sin(ang * 4.0 + uTime * 8.0);
    float radius = 0.25 + 0.006 * sin(uTime * 1.3) + uListen * uAmp * 0.045
        + uSpeak * uAmp * 0.03 - uThink * 0.018 + wobble;

    float twist = uSpin + uThink * (0.3 - r) * 5.0;
    float cs = cos(twist);
    float sn = sin(twist);
    float2 p = float2(cs * uv.x - sn * uv.y, sn * uv.x + cs * uv.y);

    float n = fbm(p * 3.6 + float2(uFlow * 0.6, -uFlow * 0.4));
    float n2 = fbm(p * 5.2 - float2(uFlow * 0.3, uFlow * 0.5) + n * 1.6);
    float3 col = mix(uDeep, uMid, smoothstep(0.15, 0.8, n));
    float glint = smoothstep(0.35, 0.85, n2) * (0.6 + 0.4 * live * (0.4 + 0.6 * uAmp) + 0.25 * uThink);
    col = mix(col, uAccent, clamp(glint, 0.0, 1.0));

    float k = clamp(r / radius, 0.0, 1.0);
    col *= 0.4 + 0.8 * sqrt(1.0 - k * k);
    col += 0.16 * (1.0 - smoothstep(0.0, 0.14, length(uv - float2(-0.08, -0.1))));
    col += uAccent * smoothstep(0.72, 1.0, k) * 0.45;

    float body = 1.0 - smoothstep(radius - 0.003, radius + 0.003, r);
    float d = max(r - radius, 0.0);
    float glow = exp(-d * (11.0 - 4.0 * live * uAmp)) * (0.28 + 0.32 * live * uAmp + 0.1 * uThink);
    glow *= 1.0 - smoothstep(0.3, 0.5, r);

    float alpha = clamp(body + glow * (1.0 - body), 0.0, 1.0);
    float3 rgb = min(col, float3(1.0)) * body + uAccent * glow * (1.0 - body);
    return half4(half3(rgb), half(alpha));
}
"""
