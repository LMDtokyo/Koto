package run.koto.desktop.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

@Composable
fun AuthHost(
    viewModel : AuthViewModel,
    modifier  : Modifier = Modifier,
) {
    var stage by remember { mutableStateOf(AuthStage.Welcome) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KotoTheme.colors.background),
    ) {
        AnimatedContent(
            targetState    = stage,
            transitionSpec = {
                val dirIntoSub = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(tween(320, easing = KotoEasing)) { full ->
                    if (dirIntoSub) full else -full
                } + fadeIn(tween(240))
                val exit  = slideOutHorizontally(tween(320, easing = KotoEasing)) { full ->
                    if (dirIntoSub) -full / 3 else full / 3
                } + fadeOut(tween(160))
                enter togetherWith exit
            },
            label          = "auth-stage",
        ) { current ->
            when (current) {
                AuthStage.Welcome  -> WelcomeScreen(
                    onCreate  = { stage = AuthStage.Register },
                    onRestore = { stage = AuthStage.Login },
                )
                AuthStage.Register -> RegisterScreen(
                    viewModel = viewModel,
                    onBack    = { stage = AuthStage.Welcome },
                )
                AuthStage.Login    -> LoginScreen(
                    viewModel = viewModel,
                    onBack    = { stage = AuthStage.Welcome },
                )
            }
        }
    }
}
