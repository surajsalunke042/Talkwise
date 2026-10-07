package com.surajdev.talkwise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.surajdev.talkwise.AppContainer
import com.surajdev.talkwise.ui.components.OrbMessage
import com.surajdev.talkwise.ui.home.HomeScreen
import com.surajdev.talkwise.ui.pocket.PocketScreen
import com.surajdev.talkwise.ui.recap.RecapScreen
import com.surajdev.talkwise.ui.talk.TalkScreen
import com.surajdev.talkwise.ui.talk.TalkViewModel
import com.surajdev.talkwise.ui.welcome.WelcomeScreen

private enum class Screen { HOME, TALK, RECAP, POCKET }

/** What is on screen right now; the key that screen transitions animate between. */
private enum class Stage { CONNECTING, FAILED, WELCOME, HOME, TALK, RECAP, POCKET }

private val Glide = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun AppRoot(container: AppContainer) {
    val backend = container.backend
    val appVm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(backend) } })
    val boot by appVm.boot.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }

    if (backend == null) {
        Centered {
            OrbMessage(
                title = "One step left",
                detail = "Add your Firebase google-services.json to the app folder and rebuild. The README has the steps.",
            )
        }
        return
    }

    val profile by backend.users.profile.collectAsStateWithLifecycle()
    val words by backend.users.words.collectAsStateWithLifecycle()
    val talkVm: TalkViewModel = viewModel(factory = viewModelFactory { initializer { TalkViewModel(container, backend) } })

    val current = profile
    val stage = when {
        boot is Boot.Failed -> Stage.FAILED
        current == null -> Stage.CONNECTING
        !current.onboarded -> Stage.WELCOME
        else -> Stage.valueOf(screen.name)
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            // Going deeper pushes the old screen back; coming home lets it settle forward.
            val deeper = targetState.ordinal > initialState.ordinal
            (fadeIn(tween(460, delayMillis = 60, easing = Glide)) +
                scaleIn(tween(520, easing = Glide), initialScale = if (deeper) 1.05f else 0.95f)) togetherWith
                (fadeOut(tween(220)) + scaleOut(tween(320, easing = Glide), targetScale = if (deeper) 0.95f else 1.05f))
        },
        label = "stage",
    ) { shown ->
        when (shown) {
            Stage.CONNECTING -> Centered { OrbMessage(title = "Tuning in", detail = "", thinking = true) }
            Stage.FAILED -> {
                val failure = boot as? Boot.Failed
                Centered {
                    OrbMessage(
                        title = failure?.title.orEmpty(),
                        detail = failure?.detail.orEmpty(),
                        action = "Try again",
                        onAction = appVm::connect,
                    )
                }
            }
            Stage.WELCOME -> WelcomeScreen(onBegin = backend.users::completeOnboarding)
            Stage.HOME -> if (current != null) {
                HomeScreen(
                    profile = current,
                    wordCount = words.size,
                    onTalk = { scenario ->
                        talkVm.begin(scenario)
                        screen = Screen.TALK
                    },
                    onPocket = { screen = Screen.POCKET },
                    onDeleteData = appVm::deleteData,
                )
            }
            Stage.TALK -> TalkScreen(
                vm = talkVm,
                onEnd = { screen = if (talkVm.end()) Screen.RECAP else Screen.HOME },
            )
            Stage.RECAP -> RecapScreen(vm = talkVm, profile = current, onDone = { screen = Screen.HOME })
            Stage.POCKET -> PocketScreen(
                words = words,
                onRemove = backend.users::removeWord,
                onBack = { screen = Screen.HOME },
            )
        }
    }

    BackHandler(enabled = stage == Stage.POCKET || stage == Stage.RECAP) { screen = Screen.HOME }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) { content() }
}
