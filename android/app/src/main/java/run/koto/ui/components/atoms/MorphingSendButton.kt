package run.koto.ui.components.atoms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.ui.theme.KotoTheme

/**
 * MI-01: 4-state morphing send button.
 *
 * State machine:
 *   IDLE_EMPTY  — mic icon          (text == "" && !sending && !justSent)
 *   IDLE_TEXT   — arrow icon        (text != "" && !sending && !justSent)
 *   SENDING     — progress spinner  (sending == true)
 *   JUST_SENT   — checkmark 800ms   (sending just flipped false after being true)
 *
 * All transitions use KotoTheme.motion.springMicro (stiffness=400, dampingRatio=0.7).
 * No raw spring() literals — MI-05 compliance.
 */

private enum class SendButtonState {
    IDLE_EMPTY, IDLE_TEXT, SENDING, JUST_SENT
}

@Composable
fun MorphingSendButton(
    hasText  : Boolean,
    sending  : Boolean,
    onSend   : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val motion = KotoTheme.motion

    // Track justSent to show checkmark for 800ms after sending completes
    var justSent by remember { mutableStateOf(false) }
    var prevSending by remember { mutableStateOf(false) }

    // Detect sending → not-sending edge to trigger justSent flash
    LaunchedEffect(sending) {
        if (prevSending && !sending) {
            justSent = true
            delay(800L)
            justSent = false
        }
        prevSending = sending
    }

    val state = when {
        sending  -> SendButtonState.SENDING
        justSent -> SendButtonState.JUST_SENT
        hasText  -> SendButtonState.IDLE_TEXT
        else     -> SendButtonState.IDLE_EMPTY
    }

    AnimatedContent(
        targetState    = state,
        transitionSpec = {
            // springMicro for enter scale; fast fade out — per MI-05 no raw literals
            scaleIn(motion.springMicro) + fadeIn(tween(80)) togetherWith
            scaleOut(tween(100)) + fadeOut(tween(80))
        },
        label          = "morph_send_button",
        modifier       = modifier,
    ) { s ->
        // Resolve background modifier — Color and Brush have separate .background() overloads
        val backgroundMod = if (s == SendButtonState.IDLE_EMPTY) {
            Modifier.background(colors.surfaceVariant, CircleShape)
        } else {
            Modifier.background(colors.bubbleGradient, CircleShape)
        }
        // Resolve click modifier per state
        val clickMod: Modifier = when (s) {
            SendButtonState.IDLE_TEXT,
            SendButtonState.JUST_SENT  -> Modifier.clickable(onClick = onSend)
            SendButtonState.IDLE_EMPTY -> Modifier.clickable { /* no-op: voice recording future */ }
            SendButtonState.SENDING    -> Modifier  // not clickable while sending
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(backgroundMod)
                .then(clickMod)
                .semantics {
                    contentDescription = when (s) {
                        SendButtonState.IDLE_EMPTY -> "Голосовое сообщение"
                        SendButtonState.IDLE_TEXT  -> "Отправить"
                        SendButtonState.SENDING    -> "Отправка…"
                        SendButtonState.JUST_SENT  -> "Отправлено"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (s) {
                SendButtonState.IDLE_EMPTY -> Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = null,
                    tint               = colors.onSurfaceLow,
                    modifier           = Modifier.size(22.dp),
                )
                SendButtonState.IDLE_TEXT  -> Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint               = colors.onPrimary,
                    modifier           = Modifier.size(22.dp),
                )
                SendButtonState.SENDING    -> CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    color       = colors.onPrimary,
                    strokeWidth = 2.dp,
                    trackColor  = Color.Transparent,
                )
                SendButtonState.JUST_SENT  -> Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = colors.onPrimary,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
    }
}
