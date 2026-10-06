package com.surajdev.talkwise.ui.recap

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surajdev.talkwise.data.StreakEngine
import com.surajdev.talkwise.data.StreakState
import com.surajdev.talkwise.data.UserProfile
import com.surajdev.talkwise.ui.components.GlassCard
import com.surajdev.talkwise.ui.components.Glyph
import com.surajdev.talkwise.ui.components.GlyphIcon
import com.surajdev.talkwise.ui.components.LabelRow
import com.surajdev.talkwise.ui.components.PrimaryButton
import com.surajdev.talkwise.ui.components.Skeleton
import com.surajdev.talkwise.ui.components.Txt
import com.surajdev.talkwise.ui.components.enter
import com.surajdev.talkwise.ui.components.hapticConfirm
import com.surajdev.talkwise.ui.talk.TalkViewModel
import com.surajdev.talkwise.ui.theme.Noc
import com.surajdev.talkwise.ui.theme.Type
import java.time.LocalDate

@Composable
fun RecapScreen(vm: TalkViewModel, profile: UserProfile?, onDone: () -> Unit) {
    val recap by vm.recap.collectAsStateWithLifecycle()
    val shown = recap ?: return
    val view = LocalView.current
    LaunchedEffect(shown.loading) { if (!shown.loading) view.hapticConfirm() }

    val streak = profile?.let {
        StreakEngine.visible(
            StreakState(it.streak, it.bestStreak, it.freezes, it.lastActiveDay?.let(LocalDate::parse)),
            LocalDate.now(),
        )
    } ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Txt("SESSION COMPLETE", Type.Label, Modifier.enter(0), color = Noc.Accent)
        Txt("Nicely done", Type.Display, Modifier.padding(top = 10.dp).enter(0))

        Row(Modifier.fillMaxWidth().padding(top = 32.dp).enter(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${shown.minutes}", "MIN", Modifier.weight(1f))
            Stat("${shown.turns}", if (shown.turns == 1) "TURN" else "TURNS", Modifier.weight(1f))
            Stat("$streak", "DAY STREAK", Modifier.weight(1f))
        }

        Crossfade(shown.loading, Modifier.padding(top = 12.dp).enter(2), animationSpec = tween(420), label = "recap") { loading ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    LabelRow("What went well")
                    Spacer(Modifier.height(16.dp))
                    if (loading) {
                        Skeleton(lines = 3)
                    } else {
                        shown.wins.forEachIndexed { index, win ->
                            Row(Modifier.padding(top = if (index == 0) 0.dp else 14.dp)) {
                                GlyphIcon(Glyph.CHECK, Noc.Accent, Modifier.padding(top = 3.dp), size = 18.dp)
                                Txt(win, Type.Body, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    LabelRow("Next time, try")
                    Spacer(Modifier.height(16.dp))
                    if (loading) Skeleton(lines = 2) else Txt(shown.focus, Type.Body)
                }
            }
        }

        if (shown.wordsSaved > 0) {
            Txt(
                if (shown.wordsSaved == 1) "1 new word is in your pocket." else "${shown.wordsSaved} new words are in your pocket.",
                Type.Caption,
                Modifier.padding(top = 18.dp).enter(3),
                color = Noc.TextDim,
            )
        }

        Spacer(Modifier.height(36.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth().enter(3), onDone)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    GlassCard(modifier, padding = 18.dp) {
        Txt(value, Type.Number)
        Txt(label, Type.Label, Modifier.padding(top = 6.dp), color = Noc.TextFaint, maxLines = 1)
    }
}
