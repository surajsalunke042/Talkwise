package com.surajdev.talkwise.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.surajdev.talkwise.data.LevelEngine
import com.surajdev.talkwise.data.Scenario
import com.surajdev.talkwise.data.Scenarios
import com.surajdev.talkwise.data.StreakEngine
import com.surajdev.talkwise.data.StreakState
import com.surajdev.talkwise.data.UserProfile
import com.surajdev.talkwise.ui.components.CardShape
import com.surajdev.talkwise.ui.components.GhostButton
import com.surajdev.talkwise.ui.components.GlassCard
import com.surajdev.talkwise.ui.components.Glyph
import com.surajdev.talkwise.ui.components.GlyphIcon
import com.surajdev.talkwise.ui.components.LabelRow
import com.surajdev.talkwise.ui.components.PrimaryButton
import com.surajdev.talkwise.ui.components.PrivacyLink
import com.surajdev.talkwise.ui.components.TutorOrb
import com.surajdev.talkwise.ui.components.Txt
import com.surajdev.talkwise.ui.components.enter
import com.surajdev.talkwise.ui.components.glass
import com.surajdev.talkwise.ui.components.pressable
import com.surajdev.talkwise.ui.talk.Phase
import com.surajdev.talkwise.ui.theme.Noc
import com.surajdev.talkwise.ui.theme.Type
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    profile: UserProfile,
    wordCount: Int,
    onTalk: (Scenario) -> Unit,
    onPocket: () -> Unit,
    onDeleteData: () -> Unit,
) {
    val today = LocalDate.now()
    val streak = StreakEngine.visible(
        StreakState(profile.streak, profile.bestStreak, profile.freezes, profile.lastActiveDay?.let(LocalDate::parse)),
        today,
    )
    val minutes = profile.secondsOn(today.toString()) / 60
    val goalProgress = (profile.secondsOn(today.toString()) / (profile.goalMinutes * 60f)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp).enter(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(greeting().uppercase(), Type.Label, color = Noc.TextFaint)
                Txt("Ready when you are", Type.Title, Modifier.padding(top = 6.dp))
            }
            StreakPill(streak)
        }

        Box(Modifier.fillMaxWidth().enter(1), contentAlignment = Alignment.Center) {
            TutorOrb(Phase.IDLE, Modifier.size(300.dp))
            GoalRing(goalProgress, Modifier.size(214.dp))
        }
        Txt(
            text = if (goalProgress >= 1f) "Goal reached · $minutes min today" else "$minutes of ${profile.goalMinutes} min today",
            style = Type.Caption,
            modifier = Modifier.fillMaxWidth().enter(1),
            color = if (goalProgress >= 1f) Noc.Accent else Noc.TextDim,
            align = TextAlign.Center,
        )

        PrimaryButton(
            "Start talking",
            Modifier.padding(horizontal = 24.dp).padding(top = 24.dp).fillMaxWidth().enter(2),
        ) { onTalk(Scenarios.free) }

        LabelRow("Scenarios", Modifier.padding(horizontal = 24.dp).padding(top = 40.dp).enter(3))
        Row(
            Modifier
                .padding(top = 14.dp)
                .enter(3)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Scenarios.all.forEach { scenario -> ScenarioCard(scenario) { onTalk(scenario) } }
        }

        LabelRow("Your level", Modifier.padding(horizontal = 24.dp).padding(top = 40.dp).enter(4))
        GlassCard(Modifier.padding(horizontal = 24.dp).padding(top = 14.dp).fillMaxWidth().enter(4)) {
            val calibrating = profile.assessedTurns < LevelEngine.CALIBRATION_TURNS
            Txt(if (calibrating) "Finding your level" else profile.band.label, Type.Title)
            Txt(
                if (calibrating) "Talk for a few turns and Wise will tune itself to you." else "Wise adjusts as you improve.",
                Type.Caption,
                Modifier.padding(top = 4.dp, bottom = 20.dp),
                color = Noc.TextDim,
            )
            SkillBar("Vocabulary", profile.skills.vocabulary, dimmed = calibrating)
            SkillBar("Grammar", profile.skills.grammar, dimmed = calibrating)
            SkillBar("Fluency", profile.skills.fluency, dimmed = calibrating, last = true)
        }

        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .enter(5)
                .pressable(onClick = onPocket)
                .fillMaxWidth()
                .clip(CardShape)
                .background(Noc.Surface)
                .border(1.dp, Noc.Hairline, CardShape)
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt("Word pocket", Type.Title)
                Txt(
                    when (wordCount) {
                        0 -> "Save words while you talk"
                        1 -> "1 word saved"
                        else -> "$wordCount words saved"
                    },
                    Type.Caption,
                    Modifier.padding(top = 4.dp),
                    color = Noc.TextDim,
                )
            }
            GlyphIcon(Glyph.FORWARD, Noc.TextDim)
        }

        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        if (confirmDelete) {
            GlassCard(Modifier.padding(horizontal = 24.dp).padding(top = 28.dp).fillMaxWidth()) {
                Txt("Delete everything?", Type.Title)
                Txt(
                    "Your level, streak, saved words and session history will be erased. This can't be undone.",
                    Type.Caption,
                    Modifier.padding(top = 6.dp, bottom = 18.dp),
                    color = Noc.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Delete", onClick = onDeleteData)
                    GhostButton("Keep") { confirmDelete = false }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp).enter(6),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                PrivacyLink()
                Txt(
                    "Delete my data",
                    Type.Caption,
                    Modifier.pressable { confirmDelete = true }.padding(vertical = 8.dp),
                    color = Noc.TextFaint,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}

@Composable
private fun StreakPill(streak: Int) {
    val lit = streak > 0
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (lit) Noc.AccentDim else Noc.Surface)
            .border(1.dp, if (lit) Noc.Accent.copy(alpha = 0.4f) else Noc.Hairline, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.SPARK, if (lit) Noc.Accent else Noc.TextFaint, size = 14.dp)
        Txt(
            if (streak == 1) "1 day" else "$streak days",
            Type.Caption,
            Modifier.padding(start = 8.dp),
            color = if (lit) Noc.Text else Noc.TextDim,
        )
    }
}

/** Today's goal as a thin ring around the orb. */
@Composable
private fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress, spring(dampingRatio = 0.8f, stiffness = 40f), label = "goal")
    Canvas(modifier) {
        val width = 3.dp.toPx()
        val inset = Offset(width / 2, width / 2)
        val arc = Size(size.width - width, size.height - width)
        drawArc(Noc.Hairline, 0f, 360f, false, inset, arc, style = Stroke(width))
        if (shown > 0.001f) {
            drawArc(Noc.Accent, -90f, 360f * shown, false, inset, arc, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun ScenarioCard(scenario: Scenario, onClick: () -> Unit) {
    Column(
        Modifier
            .pressable(onClick = onClick)
            .width(172.dp)
            .height(132.dp)
            .glass()
            .padding(20.dp),
    ) {
        Txt(scenario.title, Type.BodyStrong, maxLines = 1)
        Txt(scenario.subtitle, Type.Caption, Modifier.padding(top = 6.dp).weight(1f), color = Noc.TextDim, maxLines = 2)
        GlyphIcon(Glyph.FORWARD, Noc.Accent, size = 18.dp)
    }
}

@Composable
private fun SkillBar(name: String, score: Float, dimmed: Boolean, last: Boolean = false) {
    val shown by animateFloatAsState(score / 100f, spring(dampingRatio = 0.9f, stiffness = 50f), label = name)
    Column(Modifier.padding(bottom = if (last) 0.dp else 16.dp)) {
        Row {
            Txt(name, Type.Caption, Modifier.weight(1f), color = Noc.TextDim)
            Txt(if (dimmed) "–" else "${score.toInt()}", Type.Caption, color = Noc.Text)
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Noc.SurfaceStrong),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (dimmed) 0f else shown.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Noc.Accent),
            )
        }
    }
}
