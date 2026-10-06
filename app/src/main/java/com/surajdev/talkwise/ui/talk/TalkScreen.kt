package com.surajdev.talkwise.ui.talk

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surajdev.talkwise.data.Band
import com.surajdev.talkwise.data.ChatLine
import com.surajdev.talkwise.data.Correction
import com.surajdev.talkwise.data.PocketWord
import com.surajdev.talkwise.ui.components.CardShape
import com.surajdev.talkwise.ui.components.GhostButton
import com.surajdev.talkwise.ui.components.GlassCard
import com.surajdev.talkwise.ui.components.Glyph
import com.surajdev.talkwise.ui.components.GlyphIcon
import com.surajdev.talkwise.ui.components.IconChip
import com.surajdev.talkwise.ui.components.TutorOrb
import com.surajdev.talkwise.ui.components.Txt
import com.surajdev.talkwise.ui.components.hapticConfirm
import com.surajdev.talkwise.ui.components.hapticReject
import com.surajdev.talkwise.ui.components.hapticTick
import com.surajdev.talkwise.ui.components.pressable
import com.surajdev.talkwise.ui.theme.Noc
import com.surajdev.talkwise.ui.theme.Type
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TalkScreen(vm: TalkViewModel, onEnd: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val beat by vm.voiceBeat.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var showTranscript by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (showTranscript) showTranscript = false else onEnd() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }

    // Haptics mark the moments that matter: the mic opening, a reply arriving, something going wrong.
    LaunchedEffect(ui.phase) {
        when (ui.phase) {
            Phase.LISTENING -> view.hapticTick()
            Phase.SPEAKING -> view.hapticConfirm()
            else -> Unit
        }
    }
    LaunchedEffect(ui.notice) { if (ui.notice != null) view.hapticReject() }
    LaunchedEffect(ui.levelUp) {
        if (ui.levelUp != null) {
            view.hapticConfirm()
            delay(5000)
            vm.dismissLevelUp()
        }
    }

    // Each spoken word gives the orb a small push that then fades.
    val voiceEnergy = remember { Animatable(0f) }
    LaunchedEffect(beat) {
        if (beat > 0) {
            voiceEnergy.snapTo(1f)
            voiceEnergy.animateTo(0.25f, tween(280))
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleMic() else vm.onMicDenied()
    }
    val onMic = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.toggleMic()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val typing = WindowInsets.isImeVisible
    val orbSize by animateDpAsState(
        targetValue = if (typing) 150.dp else 290.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "orbSize",
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(Glyph.CLOSE, onClick = onEnd)
                Txt(
                    ui.scenario.title.uppercase(),
                    Type.Label,
                    Modifier.weight(1f),
                    color = Noc.TextFaint,
                    align = TextAlign.Center,
                    maxLines = 1,
                )
                IconChip(Glyph.LINES) { showTranscript = true }
            }

            AnimatedVisibility(
                visible = ui.levelUp != null,
                enter = fadeIn() + expandVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LevelUpBanner(ui.levelUp)
            }

            TutorOrb(
                phase = ui.phase,
                modifier = Modifier.size(orbSize),
                level = {
                    when (ui.phase) {
                        Phase.LISTENING -> vm.micLevel.value
                        Phase.SPEAKING -> voiceEnergy.value
                        else -> 0f
                    }
                },
            )
            StatusLabel(ui.phase, ui.voiceMode)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = ui.tutorText,
                    transitionSpec = {
                        (fadeIn(tween(420, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 140f)) { it / 5 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 6 })
                    },
                    label = "tutorText",
                ) { text ->
                    Txt(text, Type.Utterance, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }

                AnimatedVisibility(ui.word != null && ui.phase != Phase.THINKING, enter = fadeIn(tween(400, 300)), exit = fadeOut(tween(120))) {
                    ui.word?.let { WordChip(it, ui.wordSaved, vm::saveWord) }
                }

                val heard = if (ui.phase == Phase.LISTENING) partial else ui.userText
                AnimatedVisibility(heard.isNotEmpty(), enter = fadeIn(tween(300)), exit = fadeOut(tween(120))) {
                    YourWords(heard, ui.correction.takeIf { ui.phase != Phase.LISTENING })
                }

                AnimatedVisibility(ui.notice != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ui.notice?.let { NoticeCard(it, vm::retry, vm::dismissNotice) }
                }
            }

            AnimatedVisibility(
                visible = ui.suggestions.isNotEmpty() && ui.phase != Phase.THINKING && ui.phase != Phase.LISTENING && !typing,
                enter = fadeIn(tween(400, 200)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically(),
            ) {
                Suggestions(ui.suggestions, vm::sendSuggestion)
            }

            AnimatedContent(
                targetState = ui.voiceMode,
                transitionSpec = { fadeIn(tween(300, 90)) togetherWith fadeOut(tween(120)) },
                label = "dock",
            ) { voiceMode ->
                if (voiceMode) {
                    VoiceDock(ui.phase, vm, onMic, onType = { vm.setVoiceMode(false) })
                } else {
                    TextDock(
                        busy = ui.phase == Phase.THINKING,
                        soundOn = ui.soundOn,
                        onSend = vm::send,
                        onVoice = { vm.setVoiceMode(true) },
                        onSound = vm::toggleSound,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showTranscript,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 3 },
        ) {
            Transcript(ui.lines) { showTranscript = false }
        }
    }
}

@Composable
private fun StatusLabel(phase: Phase, voiceMode: Boolean) {
    val text = when (phase) {
        Phase.LISTENING -> "Listening"
        Phase.THINKING -> "Thinking"
        Phase.SPEAKING -> "Speaking"
        Phase.IDLE -> if (voiceMode) "Tap to speak" else "Your turn"
    }
    AnimatedContent(text, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) }, label = "status") { shown ->
        Txt(shown.uppercase(), Type.Label, color = if (phase == Phase.IDLE) Noc.TextFaint else Noc.Accent)
    }
}

@Composable
private fun LevelUpBanner(band: Band?) {
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(Noc.AccentDim)
            .border(1.dp, Noc.Accent.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.SPARK, Noc.Accent, size = 14.dp)
        Txt("You've reached ${band?.label.orEmpty()}", Type.Caption, Modifier.padding(start = 10.dp))
    }
}

/** A word worth keeping from the tutor's reply; tap to save it to the pocket. */
@Composable
private fun WordChip(word: PocketWord, saved: Boolean, onSave: () -> Unit) {
    val edge by animateColorAsState(if (saved) Noc.Accent else Noc.Hairline, label = "wordEdge")
    Row(
        Modifier
            .padding(top = 22.dp)
            .pressable(enabled = !saved, onClick = onSave)
            .clip(RoundedCornerShape(22.dp))
            .background(Noc.Surface)
            .border(1.dp, edge, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Txt(word.word, Type.BodyStrong, color = Noc.Accent)
            Txt(word.meaning, Type.Caption, color = Noc.TextDim)
        }
        GlyphIcon(
            if (saved) Glyph.CHECK else Glyph.PLUS,
            if (saved) Noc.Accent else Noc.TextDim,
            Modifier.padding(start = 14.dp),
            size = 18.dp,
        )
    }
}

/** The learner's last message. A correction is shown as a mark on their own sentence, not as a lecture. */
@Composable
private fun YourWords(text: String, correction: Correction?) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("YOU", Type.Label, color = Noc.TextFaint)
        Txt(
            text,
            Type.Body.copy(textDecoration = if (correction != null) TextDecoration.LineThrough else TextDecoration.None),
            Modifier.padding(top = 8.dp),
            color = if (correction != null) Noc.TextFaint else Noc.TextDim,
            align = TextAlign.Center,
        )
        AnimatedVisibility(correction != null, enter = fadeIn(tween(400, 200)) + expandVertically(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(correction?.corrected.orEmpty(), Type.BodyStrong, Modifier.padding(top = 6.dp), color = Noc.Accent, align = TextAlign.Center)
                if (!correction?.tip.isNullOrEmpty()) {
                    Txt(correction?.tip.orEmpty(), Type.Caption, Modifier.padding(top = 8.dp), color = Noc.TextDim, align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Noc.Surface)
            .border(1.dp, Noc.Warn.copy(alpha = 0.35f), CardShape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(notice.text, Type.Body, color = Noc.Text, align = TextAlign.Center)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notice.canRetry) GhostButton("Try again", onClick = onRetry)
            GhostButton(if (notice.canRetry) "Dismiss" else "Okay", onClick = onDismiss)
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        suggestions.forEach { suggestion ->
            Box(
                Modifier
                    .pressable { onPick(suggestion) }
                    .clip(CircleShape)
                    .background(Noc.Surface)
                    .border(1.dp, Noc.Hairline, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Txt(suggestion, Type.Caption, color = Noc.TextDim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VoiceDock(phase: Phase, vm: TalkViewModel, onMic: () -> Unit, onType: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconChip(Glyph.KEYBOARD, onClick = onType)
        MicButton(phase, vm, onMic)
        IconChip(if (ui.soundOn) Glyph.SOUND else Glyph.SOUND_OFF, onClick = vm::toggleSound)
    }
}

@Composable
private fun MicButton(phase: Phase, vm: TalkViewModel, onClick: () -> Unit) {
    val level by vm.micLevel.collectAsStateWithLifecycle()
    val listening = phase == Phase.LISTENING
    val busy = phase == Phase.THINKING
    val halo by animateFloatAsState(
        targetValue = if (listening) 1.12f + level * 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "halo",
    )
    val fill by animateColorAsState(if (listening) Noc.Accent else Noc.Surface, label = "micFill")
    val dim by animateFloatAsState(if (busy) 0.35f else 1f, label = "micDim")

    Box(Modifier.size(104.dp).graphicsLayer { alpha = dim }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = halo
                    scaleY = halo
                    alpha = if (halo > 1.001f) 1f else 0f
                }
                .clip(CircleShape)
                .background(Noc.AccentDim),
        )
        Box(
            Modifier
                .pressable(enabled = !busy, onClick = onClick)
                .size(76.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, Noc.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.MIC, if (listening) Noc.OnAccent else Noc.Accent, size = 30.dp)
        }
    }
}

@Composable
private fun TextDock(busy: Boolean, soundOn: Boolean, onSend: (String) -> Unit, onVoice: () -> Unit, onSound: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = draft.isNotBlank() && !busy
    val submit = {
        if (canSend) {
            onSend(draft)
            draft = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconChip(Glyph.MIC, Modifier.padding(bottom = 4.dp), onClick = onVoice)
        IconChip(if (soundOn) Glyph.SOUND else Glyph.SOUND_OFF, Modifier.padding(bottom = 4.dp), onClick = onSound)
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Noc.Surface)
                .border(1.dp, Noc.Hairline, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) Txt("Type in English", Type.Body, color = Noc.TextFaint)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = Type.Body.copy(color = Noc.Text),
                cursorBrush = SolidColor(Noc.Accent),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
        }
        val sendFill by animateColorAsState(if (canSend) Noc.Accent else Noc.SurfaceStrong, label = "sendFill")
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .pressable(enabled = canSend, onClick = submit)
                .size(44.dp)
                .clip(CircleShape)
                .background(sendFill),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.SEND, if (canSend) Noc.OnAccent else Noc.TextFaint, size = 20.dp)
        }
    }
}

/** The whole conversation as a quiet script, for looking back. */
@Composable
private fun Transcript(lines: List<ChatLine>, onClose: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (lines.size - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().background(Noc.Bg).safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Transcript", Type.Title, Modifier.weight(1f).padding(start = 4.dp))
            IconChip(Glyph.CLOSE, onClick = onClose)
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(lines) { _, line ->
                Column {
                    Txt(if (line.fromTutor) "WISE" else "YOU", Type.Label, color = if (line.fromTutor) Noc.Accent else Noc.TextFaint)
                    Txt(line.text, Type.Body, Modifier.padding(top = 6.dp), color = if (line.fromTutor) Noc.Text else Noc.TextDim)
                    line.correction?.let {
                        Txt(it.corrected, Type.Body, Modifier.padding(top = 4.dp), color = Noc.Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}
