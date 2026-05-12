package run.koto.ui.screens.chat

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import run.koto.domain.model.ChatItem
import run.koto.domain.model.MessageStatus
import run.koto.domain.model.MessageUi
import run.koto.ui.components.atoms.HeartReaction
import run.koto.ui.components.atoms.MorphingSendButton
import run.koto.ui.components.atoms.TypingWaveIndicator
import run.koto.ui.theme.KotoHaptics
import run.koto.ui.theme.KotoTheme
import run.koto.ui.theme.avatarGradient
import run.koto.ui.theme.rememberKotoHaptics

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    convId                  : String,
    viewModel               : ChatViewModel = hiltViewModel(),
    onBack                  : () -> Unit,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val state      by viewModel.state.collectAsState()
    val chatItems  by viewModel.chatItems.collectAsState()
    val replyTarget by viewModel.replyTarget.collectAsState()
    val unreadBelowFold by viewModel.unreadBelowFold.collectAsState()
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    val haptics    = rememberKotoHaptics()

    LaunchedEffect(convId) { viewModel.load(convId) }

    // Auto-scroll to bottom on new messages — only if user is already near the bottom
    // reverseLayout=true: firstVisibleItemIndex==0 means bottom (newest message visible)
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)  // 0 = bottom in reversed layout
        }
    }

    // Track unread count below fold for FAB badge (CH-08, deferred to Plan 05)
    LaunchedEffect(listState.firstVisibleItemIndex) {
        viewModel.updateUnreadBelowFold(listState.firstVisibleItemIndex.coerceAtLeast(0))
    }

    // Stable lambda references — prevents new lambda allocation on every recompose
    val onReply       = remember { viewModel::onReply }
    val onInputChange = remember { viewModel::onInputChange }
    // MI-03: onSend fires haptics.onSend() then delegates to ViewModel
    val onSend = remember(haptics) {
        {
            haptics.onSend()
            viewModel.sendMessage()
        }
    }
    val onClearReply  = remember { viewModel::clearReply }

    // Context menu target — screen-level state (overlay must be in same composition tree)
    var contextMenuTarget by remember { mutableStateOf<MessageUi?>(null) }

    // onLongPress sets contextMenuTarget locally; MI-03: haptics.onLongPress() fires on every long-press
    val onLongPress: (String) -> Unit = remember(chatItems, haptics) {
        { msgId ->
            haptics.onLongPress()
            val msg = chatItems.filterIsInstance<ChatItem.Message>().find { it.msg.id == msgId }?.msg
            contextMenuTarget = msg
        }
    }

    // NAV-04: Predictive back gesture — API 34+ shows live preview of ConversationsScreen behind chat.
    // Degrades gracefully to standard BackHandler on Android 13 and below.
    PredictiveBackHandler(enabled = true) { backEvent ->
        // Collect progress events during the gesture preview (0f..1f).
        // Custom visual animation can be added here in future (e.g., scale the screen).
        backEvent.collect { /* progress: it.progress 0f..1f */ }
        // Flow completes without exception = user committed the back gesture
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),  // let LazyColumn own inset handling
        containerColor      = KotoTheme.colors.background,
        topBar = {
            ChatTopBar(
                convId                  = convId,
                displayName             = state.displayName,
                peerId                  = state.peerId,
                online                  = state.online,
                onBack                  = onBack,
                sharedTransitionScope   = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        },
        bottomBar = {
            // NO imePadding here — belongs on the LazyColumn (RESEARCH.md Pitfall 2)
            MessageInputBar(
                text         = state.inputText,
                onTextChange = onInputChange,
                onSend       = onSend,
                sending      = state.sending,
                replyTarget  = replyTarget,
                onClearReply = onClearReply,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ChatMessageList(
                items       = chatItems,
                listState   = listState,
                isTyping    = state.isTyping,
                onLongPress = onLongPress,
                onReply     = onReply,
                haptics     = haptics,
                modifier    = Modifier
                    .fillMaxSize()
                    .imePadding()  // CORRECT placement: on the scrollable content (RESEARCH.md Pattern 3)
                    .padding(horizontal = 12.dp),
            )

            // CH-08: Scroll-to-bottom FAB
            ScrollToBottomFab(
                listState        = listState,
                unreadBelowFold  = unreadBelowFold,
                onScrollToBottom = { scope.launch { listState.animateScrollToItem(0) } },
                modifier         = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )

            // Context menu overlay — must be inside the same composition tree, NOT a Dialog
            // (RESEARCH.md Pattern 6, PITFALLS.md A5)
            if (contextMenuTarget != null) {
                // Blur/scrim backdrop — API 31+ uses RenderEffect blur; older = semi-transparent overlay
                val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                } else {
                    Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.40f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod)
                        .clickable { contextMenuTarget = null }  // tap outside dismisses
                )

                // Context menu card — spring scale entry
                var menuVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { menuVisible = true }

                AnimatedVisibility(
                    visible  = menuVisible,
                    enter    = scaleIn(
                        animationSpec   = spring(dampingRatio = 0.65f, stiffness = 380f),
                        transformOrigin = TransformOrigin(0.5f, 0.5f),
                    ) + fadeIn(tween(80)),
                    exit     = scaleOut(tween(120)) + fadeOut(tween(80)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    ContextMenuCard(
                        target    = contextMenuTarget!!,
                        onReply   = { viewModel.onReply(contextMenuTarget!!); contextMenuTarget = null },
                        onCopy    = { /* TODO: clipboard */ contextMenuTarget = null },
                        onDelete  = { /* TODO: delete */ contextMenuTarget = null },
                        onDismiss = { contextMenuTarget = null },
                    )
                }
            }
        }
    }
}

// ─── Message List ─────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    items       : ImmutableList<ChatItem>,
    listState   : LazyListState,
    isTyping    : Boolean,
    onLongPress : (String) -> Unit,
    onReply     : (MessageUi) -> Unit,
    haptics     : KotoHaptics,
    modifier    : Modifier = Modifier,
) {
    LazyColumn(
        state         = listState,
        reverseLayout = true,   // index 0 = newest = visually at bottom (RESEARCH.md Pattern 2)
        modifier      = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Typing indicator at reversed-top = visually bottom-most (RESEARCH.md Pattern 7)
        if (isTyping) {
            item(key = "typing-indicator", contentType = 99) {
                // AnimatedVisibility wraps the indicator so the InfiniteTransition is disposed
                // when typing stops, not just visually hidden (RESEARCH.md Pitfall 3, PITFALLS.md A4)
                AnimatedVisibility(
                    visible  = true,
                    enter    = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit     = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                    modifier = Modifier.animateItem(),
                ) {
                    TypingWaveIndicator(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp),
                    )
                }
            }
        }

        items(
            items       = items,
            key         = { it.key },
            contentType = { item ->
                when (item) {
                    is ChatItem.Message       -> item.contentType
                    is ChatItem.DateSeparator -> 0
                }
            },
        ) { item ->
            when (item) {
                is ChatItem.Message -> SwipeToReplyContainer(
                    onReply  = { onReply(item.msg) },
                    haptics  = haptics,
                    modifier = Modifier.animateItem(),
                ) {
                    MessageRow(
                        item        = item,
                        onLongPress = onLongPress,
                        onReply     = onReply,
                        haptics     = haptics,
                    )
                }
                is ChatItem.DateSeparator -> DateSeparatorRow(
                    label    = item.label,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ─── Message Row (with send/receive animations — CH-11, CH-12) ───────────────

@Composable
private fun MessageRow(
    item        : ChatItem.Message,
    onLongPress : (String) -> Unit,
    onReply     : (MessageUi) -> Unit,
    haptics     : KotoHaptics,
    modifier    : Modifier = Modifier,
) {
    val msg    = item.msg
    val topPad = if (item.showTail) 6.dp else 2.dp

    // MI-02: Heart reaction state — double-tap triggers HeartReaction overlay
    var showHeart by remember { mutableStateOf(false) }

    // CH-11: Send animation — scale up + alpha fade for outgoing messages only.
    // Animatable starts at initial values; LaunchedEffect(msg.id) triggers once per unique message.
    // Incoming messages use Modifier.animateItem() at the LazyColumn level (CH-12).
    val scale = remember { Animatable(if (msg.isOutgoing) 0.9f else 1.0f) }
    val alpha = remember { Animatable(if (msg.isOutgoing) 0.0f else 1.0f) }

    LaunchedEffect(msg.id) {
        if (msg.isOutgoing) {
            // Parallel alpha fade in (fast)
            launch { alpha.animateTo(1f, tween(100)) }
            // Sequential spring scale: grow slightly past 1.0 then settle (RESEARCH.md Pattern 8)
            scale.animateTo(
                targetValue   = 1.02f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            )
            scale.animateTo(
                targetValue   = 1.0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
            )
        }
    }

    Column(modifier = modifier) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(top = topPad)
                .graphicsLayer {
                    scaleX     = scale.value
                    scaleY     = scale.value
                    this.alpha = alpha.value
                }
                .combinedClickable(
                    onClick       = {},
                    onLongClick   = { onLongPress(msg.id) },
                    // MI-02/MI-03: double-tap fires haptic immediately + triggers heart overlay
                    onDoubleClick = {
                        haptics.onDoubleTap()
                        showHeart = true
                    },
                ),
            horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            // MI-02: Wrap bubble in Box so HeartReaction can overlay at centre
            Box {
                MessageBubble(
                    msg  = msg,
                    item = item,
                )
                if (showHeart) {
                    HeartReaction(
                        modifier         = Modifier.align(Alignment.Center),
                        onFinished       = { showHeart = false },
                        onReactionSettle = { haptics.onReactionSettle() },
                    )
                }
            }
        }
    }
}

// ─── Message Bubble (gradient + grouping-aware — CH-01, CH-02) ───────────────

@Composable
private fun MessageBubble(
    msg  : MessageUi,
    item : ChatItem.Message,
) {
    val colors = KotoTheme.colors
    val shape  = bubbleShape(isOutgoing = msg.isOutgoing, showTail = item.showTail)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 292.dp)
                .clip(shape)
                .then(
                    if (msg.isOutgoing)
                        Modifier.background(colors.bubbleGradient)   // CH-01: violet diagonal gradient
                    else
                        Modifier.background(colors.bubbleIn)         // CH-01: flat surface variant
                )
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Reply preview strip — populated in Plan 04 when replyTo != null
                if (msg.replyTo != null) {
                    ReplyPreviewChip(
                        replyMsg   = msg.replyTo,
                        isOutgoing = msg.isOutgoing,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // CH-07: Media preview BEFORE text caption
                if (msg.mediaUrl != null) {
                    MediaMessageContent(mediaUrl = msg.mediaUrl, blurHash = msg.blurHash)
                    if (msg.text.isNotBlank()) Spacer(Modifier.height(4.dp))
                }

                if (msg.text.isNotBlank()) {
                    Text(
                        text  = msg.text,
                        style = KotoTheme.typography.bodyMedium,
                        color = if (msg.isOutgoing) colors.onBubbleOut else colors.onBubbleIn,
                    )
                }

                // Time + read receipt row
                Row(
                    modifier              = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = item.formattedTime,
                        style = KotoTheme.typography.labelSmall,
                        color = if (msg.isOutgoing)
                            colors.onBubbleOut.copy(alpha = 0.65f)
                        else
                            colors.onSurfaceMuted,
                    )
                    if (msg.isOutgoing) {
                        ReadReceiptIcon(status = msg.status)
                    }
                }
            }
        }
    }
}

/** Quoted message preview strip inside a bubble. Full implementation: Plan 04 (CH-05). */
@Composable
private fun ReplyPreviewChip(
    replyMsg   : MessageUi,
    isOutgoing : Boolean,
) {
    val colors = KotoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing)
                    colors.onBubbleOut.copy(alpha = 0.15f)
                else
                    colors.onBubbleIn.copy(alpha = 0.10f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text  = replyMsg.text.take(80),
            style = KotoTheme.typography.labelSmall,
            color = if (isOutgoing) colors.onBubbleOut.copy(alpha = 0.85f) else colors.onSurfaceLow,
            maxLines = 2,
        )
    }
}

/**
 * Selects the correct RoundedCornerShape for a message bubble based on
 * sender direction and group position (CH-02).
 *
 * showTail=true  → last in group → use the tail shape (4dp corner on send side)
 * showTail=false → middle of group → use compact shape (5dp on both sender-side corners)
 *
 * Outgoing: tail is bottomEnd. Connected corners are topEnd + bottomEnd.
 * Incoming: tail is bottomStart. Connected corners are topStart + bottomStart.
 */
private fun bubbleShape(isOutgoing: Boolean, showTail: Boolean): Shape {
    val full = 18.dp
    val mid  = 5.dp
    val tail = 4.dp
    return if (isOutgoing) {
        if (showTail)
            RoundedCornerShape(full, full, tail, full)  // = KotoTheme.shapes.bubbleOut
        else
            RoundedCornerShape(full, mid, mid, full)    // middle of group
    } else {
        if (showTail)
            RoundedCornerShape(full, full, full, tail)  // = KotoTheme.shapes.bubbleIn
        else
            RoundedCornerShape(mid, full, full, mid)    // middle of group
    }
}

// ─── Media Preview ────────────────────────────────────────────────────────────

/**
 * Media image preview for messages with mediaUrl.
 * CH-07: Uses Coil AsyncImage with BlurHash placeholder to avoid layout shifts.
 * Fixed height prevents content jump when image loads (PITFALLS.md C3).
 */
@Composable
private fun MediaMessageContent(
    mediaUrl : String,
    blurHash : String?,
) {
    val context = LocalContext.current
    val colors  = KotoTheme.colors

    // Decode BlurHash on first composition — remember(blurHash) caches the result
    val placeholder: Painter = remember(blurHash) {
        if (blurHash != null) {
            try {
                BitmapPainter(decodeBlurHash(blurHash, 32, 32).asImageBitmap())
            } catch (_: Exception) {
                ColorPainter(colors.surfaceVariant)
            }
        } else {
            ColorPainter(colors.surfaceVariant)
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(mediaUrl)
            .crossfade(300)
            .build(),
        placeholder = placeholder,
        contentDescription = "Изображение",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(KotoTheme.shapes.md),
        contentScale = ContentScale.Crop,
    )
}

// ─── Swipe-to-Reply ───────────────────────────────────────────────────────────

/**
 * Wraps a message item with a swipe-right-to-reply gesture.
 * CH-05: Swipe right >48dp triggers onReply; spring snap-back always returns to 0.
 *
 * Gesture arbitration (PITFALLS.md A2, RESEARCH.md Pattern 5):
 * - Uses detectHorizontalDragGestures which already includes touchSlop direction lock
 * - Max drag limited to threshold*1.5 to give resistance feel
 * - change.consume() only called inside onHorizontalDrag to avoid stealing scroll events
 */
@Composable
private fun SwipeToReplyContainer(
    onReply  : () -> Unit,
    haptics  : KotoHaptics,
    modifier : Modifier = Modifier,
    content  : @Composable () -> Unit,
) {
    val density   = LocalDensity.current
    val threshold = with(density) { 48.dp.toPx() }
    val offsetX   = remember { Animatable(0f) }
    val scope     = rememberCoroutineScope()

    // Progress 0→1 as drag approaches threshold — drives the reply icon appearance
    val progress by remember { derivedStateOf { (offsetX.value / threshold).coerceIn(0f, 1f) } }

    // MI-03: Track threshold crossing so haptic fires exactly once per swipe gesture
    var crossedThreshold by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart  = { crossedThreshold = false },
                onDragEnd    = {
                    scope.launch {
                        if (offsetX.value >= threshold) onReply()
                        // Spring snap-back (RESEARCH.md Pattern 5: dampingRatio=0.6, stiffness=400)
                        offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                    }
                    crossedThreshold = false
                },
                onDragCancel = {
                    scope.launch { offsetX.animateTo(0f) }
                    crossedThreshold = false
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val newOffset = (offsetX.value + dragAmount)
                        .coerceIn(0f, threshold * 1.5f)    // resist past threshold
                    scope.launch { offsetX.snapTo(newOffset) }
                    // MI-03: fire haptic exactly once when crossing threshold in either direction
                    if (!crossedThreshold && newOffset >= threshold) {
                        crossedThreshold = true
                        haptics.onSwipeThreshold()
                    } else if (newOffset < threshold) {
                        crossedThreshold = false
                    }
                },
            )
        },
    ) {
        // Reply affordance icon — appears as swipe progresses
        if (progress > 0.1f) {
            Box(
                modifier        = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(KotoTheme.colors.primary.copy(alpha = progress * 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Ответить",
                    tint               = KotoTheme.colors.onPrimary.copy(alpha = progress),
                    modifier           = Modifier.size(16.dp),
                )
            }
        }

        // Message content offset by drag — lambda-based offset defers read to layout phase (PITFALLS P3)
        Box(Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}

// ─── Context Menu ─────────────────────────────────────────────────────────────

/**
 * Floating context menu card for long-pressed messages.
 * CH-06: Spring scale animation; blur backdrop (API 31+); NOT a Dialog.
 * Min touch target: 48dp per row (AC2 accessibility).
 */
@Composable
private fun ContextMenuCard(
    target    : MessageUi,
    onReply   : () -> Unit,
    onCopy    : () -> Unit,
    onDelete  : () -> Unit,
    onDismiss : () -> Unit,
) {
    val colors = KotoTheme.colors

    Surface(
        modifier        = Modifier.widthIn(min = 200.dp, max = 280.dp),
        shape           = KotoTheme.shapes.md,
        color           = colors.surface,
        tonalElevation  = 8.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Message preview
            Text(
                text     = target.text.take(60),
                style    = KotoTheme.typography.labelSmall,
                color    = colors.onSurfaceMuted,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

            ContextMenuItem(
                icon    = Icons.AutoMirrored.Filled.ArrowBack,
                label   = "Ответить",
                color   = colors.onSurface,
                onClick = onReply,
            )
            ContextMenuItem(
                icon    = Icons.Default.ContentCopy,
                label   = "Копировать",
                color   = colors.onSurface,
                onClick = onCopy,
            )
            ContextMenuItem(
                icon    = Icons.Default.Delete,
                label   = "Удалить",
                color   = colors.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    color   : androidx.compose.ui.graphics.Color,
    onClick : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)   // AC2: minimum 48dp touch target
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = color,
            modifier           = Modifier.size(20.dp),
        )
        Text(
            text  = label,
            style = KotoTheme.typography.bodyMedium,
            color = color,
        )
    }
}

/**
 * Scroll-to-bottom FAB with unread count badge.
 * CH-08: Uses derivedStateOf to avoid recomposition per scroll pixel (PITFALLS.md P7).
 * Spring scaleIn animation from KotoTheme.motion.springBouncy.
 * reverseLayout=true: scrollToItem(0) = scrolls to NEWEST (bottom) message.
 */
@Composable
private fun ScrollToBottomFab(
    listState        : LazyListState,
    unreadBelowFold  : Int,
    onScrollToBottom : () -> Unit,
    modifier         : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val motion = KotoTheme.motion

    // derivedStateOf: only recomposes when boolean value changes, not on every pixel (PITFALLS P7)
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    AnimatedVisibility(
        visible  = showFab,
        enter    = scaleIn(motion.springBouncy) + fadeIn(tween(80)),
        exit     = scaleOut(tween(120)) + fadeOut(tween(80)),
        modifier = modifier,
    ) {
        Box {
            FloatingActionButton(
                onClick            = onScrollToBottom,
                shape              = KotoTheme.shapes.pill,
                containerColor     = colors.surface,
                contentColor       = colors.primary,
                modifier           = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Прокрутить вниз",
                    modifier           = Modifier.size(24.dp),
                )
            }

            // Unread count badge — only shown when there are unread messages below fold
            if (unreadBelowFold > 0) {
                Badge(
                    containerColor = colors.primary,
                    modifier       = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text  = if (unreadBelowFold > 99) "99+" else unreadBelowFold.toString(),
                        style = KotoTheme.typography.labelSmall,
                        color = colors.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Animated read receipt indicator for outgoing messages.
 * CH-03: Morphs through SENDING → SENT → DELIVERED → READ states.
 * Uses AnimatedContent keyed on MessageStatus with fade transitions (KotoTheme.motion.tweenFast).
 * Each state uses a distinct icon; READ state tinted with KotoTheme.colors.primary (violet).
 */
@Composable
private fun ReadReceiptIcon(status: MessageStatus) {
    val colors = KotoTheme.colors

    AnimatedContent(
        targetState    = status,
        transitionSpec = {
            fadeIn(tween(200)) togetherWith fadeOut(tween(100))
        },
        label = "read_receipt",
    ) { s ->
        val (icon, tint, desc) = when (s) {
            MessageStatus.SENDING   -> Triple(
                Icons.Default.Schedule,
                colors.onBubbleOut.copy(alpha = 0.45f),
                "Отправляется",
            )
            MessageStatus.SENT      -> Triple(
                Icons.Default.Check,
                colors.onBubbleOut.copy(alpha = 0.55f),
                "Отправлено",
            )
            MessageStatus.DELIVERED -> Triple(
                Icons.Default.DoneAll,
                colors.onBubbleOut.copy(alpha = 0.70f),
                "Доставлено",
            )
            MessageStatus.READ      -> Triple(
                Icons.Default.DoneAll,
                colors.primary,          // Violet — read receipt (CH-03)
                "Прочитано",
            )
            MessageStatus.FAILED    -> Triple(
                Icons.Default.ErrorOutline,
                colors.error,
                "Ошибка",
            )
        }
        Icon(
            imageVector        = icon,
            contentDescription = desc,
            tint               = tint,
            modifier           = Modifier
                .size(14.dp)
                .semantics { contentDescription = desc },
        )
    }
}

// ─── Date Separator ───────────────────────────────────────────────────────────

@Composable
private fun DateSeparatorRow(
    label    : String,
    modifier : Modifier = Modifier,
) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(KotoTheme.shapes.md)
                .background(KotoTheme.colors.surface.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text  = label,
                style = KotoTheme.typography.labelSmall,
                color = KotoTheme.colors.onSurfaceMuted,
            )
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ChatTopBar(
    convId                  : String,
    displayName             : String,
    peerId                  : String,
    online                  : Boolean,
    onBack                  : () -> Unit,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val colors = KotoTheme.colors

    // Shared element modifier for avatar morph from conversation list (NAV-02)
    // Key must match "avatar-${conv.id}" in ConversationItem exactly
    val avatarMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState      = rememberSharedContentState(key = "avatar-$convId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform         = { _, _ ->
                    spring(dampingRatio = 0.85f, stiffness = 380f)
                },
            )
        }
    } else {
        Modifier
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = colors.onBackground)
            }
        },
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Gradient seed MUST come from the peer's stable account ID, not
                // from the display name — otherwise the colour flips every time
                // we rename the contact and differs from the conversation list.
                val avatarSeed = peerId.ifBlank { convId }
                Box(
                    modifier = avatarMod
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(avatarGradient(avatarSeed))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color      = colors.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style      = KotoTheme.typography.labelSmall,
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text     = displayName.ifBlank { "Пользователь Koto" },
                        style    = KotoTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = colors.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (online) {
                        Text(
                            "в сети",
                            style = KotoTheme.typography.labelSmall,
                            color = colors.online,
                        )
                    } else {
                        Text(
                            "не в сети",
                            style = KotoTheme.typography.labelSmall,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
            }
        },
        colors       = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
        // Scaffold has contentWindowInsets=WindowInsets(0), so TopAppBar must own status bar inset
        windowInsets = WindowInsets.statusBars,
    )
}

// ─── Input Bar ────────────────────────────────────────────────────────────────

@Composable
private fun MessageInputBar(
    text         : String,
    onTextChange : (String) -> Unit,
    onSend       : () -> Unit,
    sending      : Boolean,
    replyTarget  : MessageUi?,
    onClearReply : () -> Unit,
) {
    val colors = KotoTheme.colors
    Surface(
        color          = colors.background,
        tonalElevation = 0.dp,
    ) {
        Column {
            // Reply preview strip — CH-05: accent border + quoted text + dismiss button
            if (replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Left accent bar — primary color border
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.primary)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Ответ",
                            style = KotoTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.primary,
                        )
                        Text(
                            replyTarget.text.take(60),
                            style    = KotoTheme.typography.labelSmall,
                            color    = colors.onSurfaceLow,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClearReply, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Отменить ответ",
                            tint               = colors.onSurfaceLow,
                            modifier           = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // All three elements share the same min-height (44dp) and are centered
            // vertically. The text field gets its own internal padding so it
            // line-aligns with the icons regardless of how many lines are typed.
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick  = { /* TODO: attach */ },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        "Прикрепить",
                        tint     = colors.onSurfaceLow,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Custom pill input — BasicTextField with an explicit focus requester
                // and an explicit click target on the pill shell so tapping anywhere
                // on the container brings up the keyboard.
                val focusRequester = remember { FocusRequester() }
                val keyboard       = LocalSoftwareKeyboardController.current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(KotoTheme.shapes.inputField)
                        .background(colors.surfaceVariant)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                        ) {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value         = text,
                        onValueChange = onTextChange,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle     = KotoTheme.typography.bodyMedium.copy(color = colors.onBackground),
                        cursorBrush   = androidx.compose.ui.graphics.SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        maxLines      = 5,
                    )
                    if (text.isEmpty()) {
                        Text(
                            "Сообщение…",
                            color = colors.onSurfaceMuted,
                            style = KotoTheme.typography.bodyMedium,
                        )
                    }
                }

                MorphingSendButton(
                    hasText = text.isNotBlank(),
                    sending = sending,
                    onSend  = onSend,
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
//
// Avatar gradients are centralised in ui/theme/Color.kt — do NOT define a
// screen-local version here, or the same user will get different colours in
// the conversation list and the chat header.
