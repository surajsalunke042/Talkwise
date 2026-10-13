package com.surajdev.talkwise.ui.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private val Goals = listOf(5, 10, 15, 20)

@Composable
fun WelcomeScreen(onBegin: (goalMinutes: Int) -> Unit) {
    var goal by rememberSaveable { mutableIntStateOf(10) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorOrb(Phase.IDLE, Modifier.size(300.dp).enter(0))
        Txt("Talk Wise", Type.Display, Modifier.enter(1), align = TextAlign.Center)
        Txt(
            "Your English conversation partner. Just talk, Wise finds your level and grows with you.",
            Type.Body,
            Modifier.padding(top = 12.dp).enter(2),
            color = Noc.TextDim,
            align = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))
        LabelRow("Daily goal", Modifier.enter(3))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).enter(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Goals.forEach { minutes ->
                GoalChoice(minutes, selected = minutes == goal, Modifier.weight(1f)) { goal = minutes }
            }
        }

        Spacer(Modifier.height(40.dp))
        PrimaryButton("Begin", Modifier.fillMaxWidth().enter(4)) { onBegin(goal) }
        Txt(
            "No account needed.",
            Type.Caption,
            Modifier.padding(top = 14.dp).enter(4),
            color = Noc.TextFaint,
        )
        PrivacyLink(Modifier.enter(4))
    }
}

@Composable
private fun GoalChoice(minutes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // The chosen goal is marked by a mint ring only; the glass itself stays the same.
    val lit by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = 300f), label = "lit")
    Box(
        modifier
            .pressable(onClick = onClick)
            .height(72.dp)
            .glass(CircleShape)
            .border(1.5.dp, Noc.Accent.copy(alpha = lit.coerceIn(0f, 1f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("$minutes", Type.Title)
            Txt("MIN", Type.Label, color = Noc.TextFaint)
        }
    }
}
