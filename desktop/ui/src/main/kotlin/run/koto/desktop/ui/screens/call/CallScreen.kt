package run.koto.desktop.ui.screens.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme
import kotlin.math.abs
import kotlin.math.sin

/**
 * Call state — drives which UI bits render. `Ringing` is what the callee sees
 * on incoming; `Connecting` is the RTC negotiation state; `Connected` is live.
 */
enum class CallState { Ringing, Connecting, Connected, Ended }

/**
 * Fullscreen Call overlay. Desktop adaptation of Call.jsx:
 *   - Aurora gradient background (drifting radial blobs)
 *   - Particle field that speeds up with speaker amplitude
 *   - Avatar with pulsing aura (active rings when not connected, glow when live)
 *   - Speaker name + status line (Shimmer when connecting, timer when connected)
 *   - VoiceBars indicator under the name reacting to fake amplitude
 *   - Control row at the bottom: mute, video-toggle, speaker, end
 *
 * The Call controls target mouse/trackpad not swipes — no IncomingPad here.
 * If the call is `Ringing`, show Accept / Reject side-by-side instead.
 */
@Composable
fun CallScreen(
    peerId       : String,
    video        : Boolean,
    onEnd        : () -> Unit,
    onToggleMute : (Boolean) -> Unit = {},
    onToggleVideo: (Boolean) -> Unit = {},
    modifier     : Modifier = Modifier,
) {
    // Placeholder presentation: peer name/avatar plumbing comes via the contact
    // / profile lookup once the conversation→profile pipe lands. For now we
    // render the truncated peer id so the screen still renders post-MockData.
    val contact = remember(peerId) {
        object {
            val name     = if (peerId.length > 12) peerId.take(8) + "…" else peerId
            val initials = peerId.take(2).uppercase()
            val color    = androidx.compose.ui.graphics.Color(0xFF7C5CFF)
        }
    }

    var state   by remember { mutableStateOf(CallState.Connecting) }
    var muted   by remember { mutableStateOf(false) }
    var videoOn by remember { mutableStateOf(video) }
    var speaker by remember { mutableStateOf(false) }

    // State transitions — Connecting → Connected after a couple seconds.
    LaunchedEffect(Unit) {
        delay(1800)
        state = CallState.Connected
    }

    // ── Fake amplitude (drives particles, voice bars, aura pulse) ──────────
    val amp = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state != CallState.Connected) {
            amp.animateTo(0.15f, tween(400))
            return@LaunchedEffect
        }
        // Alternate talking / quiet windows
        var talking = true
        var until   = 2500L
        var elapsed = 0L
        while (state == CallState.Connected) {
            withFrameMillis { now ->
                elapsed = now
            }
            val tNow = System.currentTimeMillis()
            if (tNow - tNow.mod(1L) > until + tNow) {
                talking = !talking
                until   = tNow + if (talking) (2500L..4500L).random() else (600L..1600L).random()
            }
            val t = tNow / 1000.0
            val target = if (talking) {
                (0.45 + 0.35 * abs(sin(t * 8 + sin(t * 1.5) * 2)) + Math.random() * 0.2).toFloat()
            } else {
                (0.04 + Math.random() * 0.06).toFloat()
            }
            amp.snapTo(amp.value + (target - amp.value) * 0.35f)
            delay(33)
        }
    }

    // ── Elapsed call timer ─────────────────────────────────────────────────
    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(state) {
        if (state != CallState.Connected) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (state == CallState.Connected) {
            elapsedMs = System.currentTimeMillis() - start
            delay(33)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
    ) {
        AuroraBg(accent = KotoTheme.colors.accent, intensity = 0.55f + amp.value * 0.45f)
        ParticleField(amp = amp.value, accent = KotoTheme.colors.accent)

        // ── Top-of-screen title/status ─────────────────────────────────────
        Column(
            modifier             = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = if (videoOn) "Видеозвонок · Koto" else "Голосовой звонок · Koto",
                style = KotoTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Lock,
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = 0.55f),
                    modifier           = Modifier.size(10.dp),
                )
                Text(
                    text  = "зашифровано end-to-end",
                    style = KotoTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        // ── Center: avatar + aura + name + state line ──────────────────────
        Column(
            modifier             = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp),
            verticalArrangement  = Arrangement.Center,
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                AvatarAura(
                    accent   = KotoTheme.colors.accent,
                    amp      = amp.value,
                    connected = state == CallState.Connected,
                    size     = 180.dp,
                )
                Avatar(
                    initials = contact.initials,
                    color    = contact.color,
                    size     = 160.dp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text       = contact.name,
                style      = KotoTheme.typography.headlineLarge,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            when (state) {
                CallState.Ringing    -> Text(
                    text  = "входящий вызов",
                    style = KotoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                CallState.Connecting -> ShimmerLabel("соединение…")
                CallState.Connected  -> Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VoiceBars(amp = amp.value, color = Color.White)
                    Text(
                        text       = fmtTime(elapsedMs),
                        style      = KotoTheme.typography.monoLarge,
                        color      = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
                CallState.Ended      -> Text(
                    text  = "завершён",
                    style = KotoTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }

        // ── Bottom controls ─────────────────────────────────────────────────
        if (state == CallState.Ringing) {
            IncomingButtons(
                onAccept = { state = CallState.Connecting },
                onReject = onEnd,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            ActiveControls(
                muted         = muted,
                videoOn       = videoOn,
                speaker       = speaker,
                onToggleMute  = { muted = !muted; onToggleMute(muted) },
                onToggleVideo = { videoOn = !videoOn; onToggleVideo(videoOn) },
                onToggleSpeak = { speaker = !speaker },
                onEnd         = { state = CallState.Ended; onEnd() },
                modifier      = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ─── Aurora background ──────────────────────────────────────────────────────

@Composable
private fun AuroraBg(accent: Color, intensity: Float) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(16_000), RepeatMode.Reverse),
        label         = "phase",
    )

    val accentStops = remember(accent, intensity) {
        listOf(accent.copy(alpha = 0.55f * intensity), Color.Transparent)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .drawBehind {
            val w = size.width; val h = size.height
            val b1 = Offset(w * (0.50f + 0.06f * sin(phase * 3.14f * 2f).toFloat()),
                            h * (0.30f + 0.03f * sin(phase * 3.14f + 0.4f).toFloat()))
            val r1 = w * 0.55f
            drawCircle(
                brush  = Brush.radialGradient(accentStops, center = b1, radius = r1),
                center = b1, radius = r1,
            )
            val b2 = Offset(w * (0.30f + 0.08f * sin(phase * 3.14f * 1.5f + 1.2f).toFloat()),
                            h * (0.65f + 0.04f * sin(phase * 3.14f + 0.8f).toFloat()))
            val r2 = w * 0.45f
            drawCircle(
                brush  = Brush.radialGradient(VioletStops, center = b2, radius = r2),
                center = b2, radius = r2,
            )
            val b3 = Offset(w * (0.75f + 0.05f * sin(phase * 3.14f + 2.4f).toFloat()),
                            h * (0.70f + 0.04f * sin(phase * 3.14f * 1.2f).toFloat()))
            val r3 = w * 0.40f
            drawCircle(
                brush  = Brush.radialGradient(SkyBlueStops, center = b3, radius = r3),
                center = b3, radius = r3,
            )
        },
    )
}

private val VioletStops  = listOf(Color(0xFFAF52DE).copy(alpha = 0.45f), Color.Transparent)
private val SkyBlueStops = listOf(Color(0xFF3276FF).copy(alpha = 0.35f), Color.Transparent)

// ─── Particle field ─────────────────────────────────────────────────────────

@Composable
private fun ParticleField(amp: Float, accent: Color, count: Int = 36) {
    // Stable seed so particles don't jump every recomposition
    data class P(val x: Float, val y: Float, val r: Float, val speed: Float, val seed: Float, val orange: Boolean)
    val particles = remember {
        List(count) { i ->
            P(
                x      = (Math.random() * 100).toFloat(),
                y      = (50 + (Math.random() - 0.5) * 50).toFloat(),
                r      = (1.2 + Math.random() * 2.6).toFloat(),
                speed  = (0.3 + Math.random() * 1.2).toFloat(),
                seed   = (Math.random() * 1000).toFloat(),
                orange = i % 5 == 0,
            )
        }
    }

    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { now -> t = now / 1000f }
        }
    }

    val speedMult = 1f + amp * 4f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        particles.forEach { p ->
            val drift = sin(t * p.speed * speedMult + p.seed) * 30f
            val lift  = (t * p.speed * speedMult * 8f + p.seed) % 120f
            val yPct  = (p.y - lift + 120f) % 120f
            val xPx   = ((p.x + drift * 0.3f + 100f) % 100f) / 100f * w
            val yPx   = yPct / 100f * h
            val opacity = (0.15f + amp * 0.65f + (1f - abs(yPct - 50f) / 60f) * 0.2f).coerceIn(0.05f, 0.9f)
            val radius = (p.r * 2f + amp * 4f)
            drawCircle(
                color  = (if (p.orange) accent else Color.White).copy(alpha = opacity),
                center = Offset(xPx, yPx),
                radius = radius,
            )
        }
    }
}

// ─── Voice bars ────────────────────────────────────────────────────────────

@Composable
private fun VoiceBars(amp: Float, color: Color, bars: Int = 5) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it / 120f } }
    Row(
        modifier              = Modifier.height(18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (i in 0 until bars) {
            val phase = (i.toFloat() / bars) * Math.PI.toFloat() * 2f
            val h = (4 + abs(sin(t + phase)) * amp * 16f + amp * 4f).coerceAtLeast(3f)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.5f + amp * 0.5f)),
            )
        }
    }
}

// ─── Avatar aura (pulsing rings + glow halo) ──────────────────────────────

@Composable
private fun AvatarAura(accent: Color, amp: Float, connected: Boolean, size: Dp) {
    val transition = rememberInfiniteTransition(label = "aura")
    val ring1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200)), label = "r1")
    val ring2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200)), label = "r2")
    val ring3 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200)), label = "r3")

    Box(modifier = Modifier.size(size)) {
        val haloScale = 1.55f + amp * 0.6f
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale }
                .align(Alignment.Center)
                .drawBehind {
                    drawCircle(
                        brush  = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.5f + amp * 0.4f), Color.Transparent),
                            radius = this.size.minDimension / 2f,
                        ),
                    )
                },
        )
        if (!connected) {
            val phases = floatArrayOf(ring1, -ring2 + 1f, ring3)
            repeat(3) { i ->
                val scaled = (phases[i] + i * 0.23f) % 1f
                Box(
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = 0.8f + scaled * 0.75f
                            scaleY = 0.8f + scaled * 0.75f
                            alpha  = (1f - scaled) * 0.55f
                        }
                        .align(Alignment.Center)
                        .border(1.5.dp, accent, CircleShape),
                )
            }
        }
    }
}

// ─── Shimmer label (encryption "connecting" text) ──────────────────────────

@Composable
private fun ShimmerLabel(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(1600)),
        label = "phase",
    )
    Text(
        text       = text,
        style      = KotoTheme.typography.bodyMedium,
        color      = Color.White.copy(alpha = 0.5f + phase * 0.5f),
        fontWeight = FontWeight.Medium,
    )
}

// ─── Controls ──────────────────────────────────────────────────────────────

@Composable
private fun ActiveControls(
    muted         : Boolean,
    videoOn       : Boolean,
    speaker       : Boolean,
    onToggleMute  : () -> Unit,
    onToggleVideo : () -> Unit,
    onToggleSpeak : () -> Unit,
    onEnd         : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        CallControl(
            icon   = KotoIcons.Mic,
            label  = if (muted) "Выкл" else "Микрофон",
            active = muted,
            onClick = onToggleMute,
        )
        CallControl(
            icon   = KotoIcons.Video,
            label  = if (videoOn) "Видео" else "Вкл видео",
            active = videoOn,
            onClick = onToggleVideo,
        )
        CallControl(
            icon   = KotoIcons.Bell,
            label  = "Динамик",
            active = speaker,
            onClick = onToggleSpeak,
        )
        Box(
            modifier          = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onEnd,
                ),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Phone,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier
                    .size(32.dp)
                    .graphicsLayer(rotationZ = 135f),
            )
        }
    }
}

@Composable
private fun CallControl(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    active  : Boolean,
    onClick : () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier          = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onClick,
                ),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (active) Color.Black else Color.White,
                modifier           = Modifier.size(26.dp),
            )
        }
        Text(
            text  = label,
            style = KotoTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun IncomingButtons(
    onAccept : () -> Unit,
    onReject : () -> Unit,
    modifier : Modifier = Modifier,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Box(
            modifier          = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onReject,
                ),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Phone,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(32.dp).graphicsLayer(rotationZ = 135f),
            )
        }
        Box(
            modifier          = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF34C759))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onAccept,
                ),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Phone,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(32.dp),
            )
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────

private fun fmtTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val tenths = (ms % 1000) / 100
    return "%02d:%02d.%d".format(m, s, tenths)
}
