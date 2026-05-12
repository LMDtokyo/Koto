# Phase 2: Chat Screen — Research

**Researched:** 2026-04-05
**Domain:** Jetpack Compose premium chat screen — bubbles, animations, gestures, media
**Confidence:** HIGH (grounded in project files already read, verified against PITFALLS.md, ARCHITECTURE.md, FEATURES.md, STACK.md which are project-authored and authoritative; supplemented by direct reading of Phase 1 delivered source)

---

## Summary

Phase 1 delivered KotoTheme fully — colors (including `bubbleGradient: Brush`), typography, spacing, shapes, motion specs, and `@Immutable` UiModels with `ImmutableList<MessageUi>` — all verified. Phase 2 builds directly on top of this foundation without touching any Phase 1 file.

The current `ChatScreen.kt` is a working skeleton: gradient bubbles render via `Brush.linearGradient`, basic message grouping exists via `buildRender()`, and `imePadding()` is applied on the input bar. However it has 8 structural issues that must be resolved before the premium features can be added: the ViewModel's local `ChatState` conflicts with the `@Immutable ChatState` in UiModels.kt; the messages list is `List<MessageUi>` (unstable); `LazyColumn` uses no `reverseLayout`; date-formatting runs in composition; and five CH requirements (CH-03 through CH-08) have no implementation at all.

Phase 2 is an in-place upgrade of `ChatScreen.kt` + `ChatViewModel.kt`, the creation of new UI component files under `ui/screens/chat/` and `ui/components/`, plus additions to `UiModels.kt` for reply and read-receipt state. No backend or Room schema changes are needed — all required data fields already exist on `MessageUi`.

**Primary recommendation:** Treat the existing ChatScreen as a scaffold to delete-and-rebuild incrementally, wave by wave — fix stability and data model issues first, then layer on premium animations, gestures, and media, in that order.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CH-01 | Gradient chat bubbles — sent с violet gradient, received с surface variant | `KotoColors.bubbleGradient` already defined; current ChatScreen uses it partially; needs migration to KotoTheme tokens |
| CH-02 | Message grouping по sender + time proximity с smart tail/no-tail | `buildRender()` skeleton exists; needs sealed `ChatItem` model, 60s window, date separators, `contentType` |
| CH-03 | Read receipts animation (sent → delivered → read) с morphing | `MessageUi.status: MessageStatus` enum already has all 4 states (SENDING/SENT/DELIVERED/READ); needs `AnimatedContent` morphing icons |
| CH-04 | Typing indicator — custom wave animation | `ChatState.isTyping: Boolean` exists; needs custom Canvas wave composable with `InfiniteTransition` |
| CH-05 | Reply (swipe right) с quoted message preview и spring snap-back | Needs `AnchoredDraggable` or `pointerInput(detectHorizontalDragGestures)`, `MessageUi.replyTo` already a field |
| CH-06 | Long press context menu с blur backdrop и spring scale animation | Needs `RenderEffect` (API 31+) or fallback, `Popup`/overlay pattern to avoid Dialog SharedTransitionScope issues |
| CH-07 | Image/media preview с BlurHash placeholder | `MessageUi.mediaUrl` and `blurHash` fields exist; needs Coil 3.1.0 (already in deps) + BlurHash decoder |
| CH-08 | Scroll-to-bottom FAB с unread count badge и spring animation | Needs `derivedStateOf` for show/hide, `animateItem()` or `AnimatedVisibility`, unread counter in ViewModel |
| CH-09 | LazyColumn 120fps (keys, stable params, deferred state reads) | Fix `List<MessageUi>` → `ImmutableList`, add `reverseLayout=true`, `key`, `contentType`, `Modifier.offset{}`  |
| CH-10 | Keyboard handling: smooth imePadding, no layout jumps | `WindowCompat.setDecorFitsSystemWindows(false)` in Activity; `imePadding()` already on input bar; must confirm no double-padding |
| CH-11 | Message send animation: bubble scale-up → spring settle | `Animatable` sequence: scale 0.9→1.02→1.0, opacity 0→1 in `LaunchedEffect` on new outgoing message |
| CH-12 | Message receive animation: slide-in from left с spring physics | `Modifier.animateItem()` on LazyColumn items + initial offset via `AnimatedVisibility` enter spec |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- Platform: Android minSdk 26, Kotlin 2.1.20, Jetpack Compose BOM 2026.03.01
- Performance: 120fps target, <100ms touch response, <16ms frame time
- Do not break existing functionality: crypto (CryptoManager/uniffi), networking (Retrofit/OkHttp/KotoWebSocket), Room DB (MessageDao, ConversationDao)
- Libraries: prefer non-generic, modern; no Accompanist; no XML layouts; no third-party UI kits
- All color/spacing/shape/motion access must go through `KotoTheme.*` — never hardcode
- `@Immutable` required on all data classes passed to composables; `ImmutableList<T>` for all collection parameters
- ChatScreen.kt currently uses `ColorCompat.kt` legacy aliases (`BgPrimary`, `TextPrimary`, etc.) — these must be migrated to `KotoTheme.colors.*` during Phase 2

---

## What Phase 1 Delivered (Foundation Status)

| Token | Kotlin Access | Phase 2 Usage |
|-------|---------------|---------------|
| `KotoColors.bubbleGradient` | `KotoTheme.colors.bubbleGradient` | Sent bubble background (CH-01) |
| `KotoColors.bubbleIn` | `KotoTheme.colors.bubbleIn` | Received bubble background (CH-01) |
| `KotoColors.onBubbleOut` | `KotoTheme.colors.onBubbleOut` | Text color on sent bubble |
| `KotoColors.onBubbleIn` | `KotoTheme.colors.onBubbleIn` | Text color on received bubble |
| `KotoColors.onSurfaceMuted` | `KotoTheme.colors.onSurfaceMuted` | Timestamps (38% opacity) |
| `KotoColors.primary` | `KotoTheme.colors.primary` | Read receipt color, reply border |
| `KotoColors.online` | `KotoTheme.colors.online` | Online dot in top bar |
| `KotoMotion.springBouncy` | `KotoTheme.motion.springBouncy` | FAB, badge spring (CH-08) |
| `KotoMotion.springSnappy` | `KotoTheme.motion.springSnappy` | Bubble appear, swipe snap |
| `KotoMotion.springGentle` | `KotoTheme.motion.springGentle` | Context menu scale |
| `KotoMotion.tweenFast` | `KotoTheme.motion.tweenFast` | Read receipt crossfade (150ms) |
| `KotoShapes.bubbleOut` | `KotoTheme.shapes.bubbleOut` | Outgoing bubble shape |
| `KotoShapes.bubbleIn` | `KotoTheme.shapes.bubbleIn` | Incoming bubble shape |
| `KotoSpacing.sm` | `KotoTheme.spacing.sm` | Inter-bubble gap (2dp/8dp) |
| `MessageStatus` enum | `run.koto.domain.model.MessageStatus` | CH-03 delivery states |
| `MessageUi.replyTo` | field on `@Immutable MessageUi` | CH-05 quoted preview |
| `MessageUi.mediaUrl` + `blurHash` | fields on `@Immutable MessageUi` | CH-07 media preview |
| `ChatState.isTyping` | field on `@Immutable ChatState` | CH-04 typing indicator |

---

## Pre-Existing Issues to Fix Before Adding Features

These must be resolved in Wave 0 of the plan. Leaving them causes regressions.

### Issue 1: Duplicate ChatState (CRITICAL)

`ChatViewModel.kt` declares its own local `data class ChatState` (line 12) that is NOT `@Immutable` and uses `List<MessageUi>` (unstable). `UiModels.kt` already has a canonical `@Immutable data class ChatState` with `ImmutableList<MessageUi>`.

**Fix:** Delete the local `ChatState` in `ChatViewModel.kt`. Import `run.koto.domain.model.ChatState`. Add missing fields (`inputText: String = ""`, `sending: Boolean = false`, `online: Boolean = false`) to the canonical `ChatState` in `UiModels.kt` so the ViewModel compiles.

### Issue 2: LazyColumn Not Reversed (CRITICAL)

The current `LazyColumn` in `ChatScreen.kt` has no `reverseLayout = true`. Messages scroll oldest-first. Auto-scroll calls `listState.animateScrollToItem(state.messages.lastIndex)` instead of `scrollToItem(0)` with reverse layout.

**Fix:** Add `reverseLayout = true`. Switch auto-scroll to `scrollToItem(0)`. Understand the semantics: with `reverseLayout = true`, index 0 = newest = bottom. `firstVisibleItemIndex == 0` means user is already at the bottom.

### Issue 3: List Stability in buildRender() (CRITICAL)

`buildRender()` receives `List<MessageUi>` (unstable). The result is a `List<MsgRender>` (also unstable). Both are passed through `remember(state.messages)`. This works for memoization but the intermediate list is still unstable for LazyColumn items.

**Fix:** Change `buildRender` input/output to `ImmutableList`. Keep `MsgRender` as a `@Immutable data class`. This enables the Compose compiler to skip recomposition on unchanged items.

### Issue 4: Date Formatting in Composition (MAJOR)

`formatMessageTime(ms)` and `formatDateLabel(ms)` are called inside composables, potentially running on every recomposition. `SimpleDateFormat` is not thread-safe.

**Fix:** Pre-compute `formattedTime: String` and `formattedDate: String` in the ViewModel mapping layer or in `buildRender()`. Add these as fields to `MsgRender`.

### Issue 5: Legacy Color Aliases (MAJOR)

`ChatScreen.kt` uses `BgPrimary`, `TextPrimary`, `BubbleGradientStart`, `BubbleReceived`, etc. from `ColorCompat.kt` (a backward-compat shim). Phase 2 must migrate these to `KotoTheme.colors.*`.

**Fix:** Replace all `ColorCompat` aliases in the chat screen with `KotoTheme.colors.*` equivalents.

### Issue 6: AnimatedVisibility with `visible = true` (MINOR)

`MessageRow` wraps every message in `AnimatedVisibility(visible = true, ...)`. This means the enter animation replays whenever the composable recomposes — including during scroll. This is incorrect.

**Fix:** Use `Modifier.animateItem()` on the LazyColumn item instead. This is the correct API for insert/remove animations in a LazyColumn (available in Compose BOM 2026.03.01).

---

## Standard Stack

### Core (already in project — confirmed in libs.versions.toml)

| Library | Version | Purpose | Phase 2 Usage |
|---------|---------|---------|---------------|
| Compose BOM | 2026.03.01 | Version alignment | All Compose APIs |
| Compose Material3 | (from BOM) | Base components | Scaffold, TopAppBar, TextField |
| Compose Animation | (from BOM) | Spring physics, AnimatedContent | CH-03, CH-05, CH-11, CH-12 |
| Compose Foundation | (from BOM) | LazyColumn, gestures, AnchoredDraggable | CH-05, CH-09 |
| Coil 3 | 3.1.0 | Image loading, crossfade | CH-07 |
| kotlinx-collections-immutable | 0.4.0 | `ImmutableList<T>` | CH-09 stability |

### Needs to be Added

| Library | Version | Purpose | Justification |
|---------|---------|---------|---------------|
| BlurHash (inline impl) | ~100 lines Kotlin | Decode blurhash string → Bitmap placeholder | `MessageUi.blurHash` field exists; no external dep needed |

BlurHash is ~100 lines of pure Kotlin math. Do not add `io.github.nichenqin:blur-hash-compose` as an external dependency — implement inline in `ui/screens/chat/BlurHashDecoder.kt`. The algorithm is public domain (MIT).

**No other new dependencies are needed for Phase 2.** Lottie (for typing indicator) is NOT required — the wave animation is achievable with `InfiniteTransition` + `Canvas`. Telephoto (for pinch-zoom) is out of scope for Phase 2 (full-screen viewer is Phase 3+).

---

## Architecture Patterns

### Recommended File Structure for Phase 2

```
android/app/src/main/java/run/koto/
├── domain/model/
│   └── UiModels.kt               // Add inputText, sending, online to ChatState;
│                                 // Add ChatItem sealed interface; expand MsgRender
├── ui/screens/chat/
│   ├── ChatScreen.kt             // Full rewrite — all CH-0x composables here
│   ├── ChatViewModel.kt          // Delete local ChatState; use canonical; add reply/FAB state
│   ├── ChatItemMapper.kt         // buildChatItems() → ImmutableList<ChatItem> — pure function
│   └── BlurHashDecoder.kt        // ~100 lines: decode blurHash String → ImageBitmap
└── ui/components/
    └── atoms/
        └── TypingWaveIndicator.kt // CH-04 — isolated, reusable
```

### Pattern 1: Sealed ChatItem (CH-02)

Replace `MsgRender` with a proper sealed interface that the LazyColumn can dispatch on via `contentType`. Computed in the ViewModel (NOT in composition).

```kotlin
// In UiModels.kt
@Immutable
sealed interface ChatItem {
    @Immutable
    data class Message(
        val msg         : MessageUi,
        val showTail    : Boolean,
        val showAvatar  : Boolean,
        val formattedTime : String,   // pre-computed in ViewModel
    ) : ChatItem {
        override val key: String get() = msg.id
        val contentType: Int get() = if (msg.mediaUrl != null) 2 else 1
    }

    @Immutable
    data class DateSeparator(
        val label : String,           // "Сегодня", "Вчера", "5 апреля"
    ) : ChatItem {
        override val key: String get() = "date-$label"
        val contentType: Int get() = 0
    }
}
```

```kotlin
// In ChatItemMapper.kt
fun buildChatItems(
    messages: ImmutableList<MessageUi>,
    nowMs: Long,
): ImmutableList<ChatItem> {
    val result = mutableListOf<ChatItem>()
    val groupWindowMs = 60_000L
    for (i in messages.indices) {
        val msg  = messages[i]
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        // Date separator
        if (prev == null || !sameDay(prev.sentAt, msg.sentAt)) {
            result += ChatItem.DateSeparator(formatDateLabel(msg.sentAt, nowMs))
        }
        // Grouping
        val isLast = next == null
            || next.senderId != msg.senderId
            || next.sentAt - msg.sentAt > groupWindowMs
        result += ChatItem.Message(
            msg           = msg,
            showTail      = isLast,
            showAvatar    = !msg.isOutgoing && isLast,
            formattedTime = formatMessageTime(msg.sentAt),
        )
    }
    return result.toImmutableList()
}
```

### Pattern 2: Reversed LazyColumn with correct scroll semantics (CH-09)

```kotlin
// Source: PITFALLS.md C1 + ARCHITECTURE.md ChatMessageList pattern
LazyColumn(
    state         = listState,
    reverseLayout = true,          // index 0 = newest = bottom
    modifier      = Modifier
        .fillMaxSize()
        .imePadding(),             // ON the LazyColumn, NOT on Scaffold content
) {
    items(
        items       = chatItems,
        key         = { it.key },
        contentType = { when (it) {
            is ChatItem.Message       -> if (it.msg.mediaUrl != null) 2 else 1
            is ChatItem.DateSeparator -> 0
        }},
    ) { item ->
        when (item) {
            is ChatItem.Message       -> MessageRow(item, Modifier.animateItem())
            is ChatItem.DateSeparator -> DateSeparator(item.label)
        }
    }
    // Typing indicator at top of reversed list = visually at bottom
    if (isTyping) {
        item(key = "typing", contentType = 99) {
            TypingWaveIndicator(Modifier.animateItem())
        }
    }
}
```

**Critical reversal semantics:** With `reverseLayout = true`:
- `scrollToItem(0)` scrolls to the bottom (newest message). Use for auto-scroll on send.
- `firstVisibleItemIndex == 0` means user is already at the bottom.
- Show scroll-to-bottom FAB when `firstVisibleItemIndex > 2`.
- Only auto-scroll on new incoming message if `firstVisibleItemIndex <= 1`.

### Pattern 3: Keyboard handling — imePadding placement (CH-10)

The correct placement of `imePadding()` is on the root content container (the `LazyColumn`), NOT on the Scaffold's bottomBar. The current skeleton applies `imePadding()` on the `Row` inside the input bar Surface — this is wrong and causes double-padding.

```kotlin
// Source: PITFALLS.md C2
// In Activity.onCreate():
WindowCompat.setDecorFitsSystemWindows(window, false)
// (enableEdgeToEdge() handles this — confirm it's called in MainActivity)

// In ChatScreen layout:
Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
        // NO imePadding here — it belongs on the list
        MessageInputBar(text, onTextChange, onSend, sending)
    }
) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),   // HERE — pushes entire list up with keyboard
    )
}
```

### Pattern 4: Read receipts animation (CH-03)

Use `AnimatedContent` keyed on `MessageStatus`. The morphing uses `CrossFade` within it.

```kotlin
// MessageStatus: SENDING → SENT → DELIVERED → READ
@Composable
private fun ReadReceiptIcon(status: MessageStatus) {
    AnimatedContent(
        targetState  = status,
        transitionSpec = {
            fadeIn(tween(200)) togetherWith fadeOut(tween(100))
        },
        label = "receipt",
    ) { s ->
        when (s) {
            MessageStatus.SENDING   -> Icon(clock icon, tint = White.copy(0.5f), size = 14.dp)
            MessageStatus.SENT      -> Icon(single check, tint = White.copy(0.5f), size = 14.dp)
            MessageStatus.DELIVERED -> Icon(double check, tint = White.copy(0.7f), size = 14.dp)
            MessageStatus.READ      -> Icon(double check filled, tint = KotoTheme.colors.primary, size = 14.dp)
            MessageStatus.FAILED    -> Icon(error icon, tint = KotoTheme.colors.error, size = 14.dp)
        }
    }
}
```

### Pattern 5: Swipe-to-reply (CH-05)

Use `Modifier.pointerInput(detectHorizontalDragGestures)` with an `Animatable<Float>` for the offset. The key is velocity-based direction locking to prevent conflict with the vertical LazyColumn scroll.

```kotlin
// Source: PITFALLS.md A2
@Composable
fun SwipeToReplyContainer(
    onReply  : () -> Unit,
    content  : @Composable () -> Unit,
) {
    val offsetX  = remember { Animatable(0f) }
    val threshold = 48.dp.value * LocalDensity.current.density  // 48dp in px
    var isDragging by remember { mutableStateOf(false) }

    Box(
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { isDragging = true },
                onDragEnd   = {
                    isDragging = false
                    if (offsetX.value >= threshold) onReply()
                    launch { offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                },
                onDragCancel = {
                    isDragging = false
                    launch { offsetX.animateTo(0f) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    // Only consume if clearly horizontal
                    change.consume()
                    val newOffset = (offsetX.value + dragAmount).coerceIn(0f, threshold * 1.5f)
                    launch { offsetX.snapTo(newOffset) }
                },
            )
        }
    ) {
        // Show reply icon affordance at offsetX progress
        ReplyAffordanceIcon(progress = offsetX.value / threshold)
        // Message content offset
        Box(Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}
```

### Pattern 6: Context menu with blur backdrop (CH-06)

`Dialog` must NOT be used — it creates a separate composition tree and breaks `SharedTransitionScope`. Use `Popup` + `Box` overlay drawn directly in the screen composition tree.

```kotlin
// RenderEffect blur (API 31+) with solid fallback (minSdk 26)
val blurModifier = if (Build.VERSION.SDK_INT >= 31) {
    Modifier.graphicsLayer {
        renderEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
} else {
    Modifier.background(Color.Black.copy(alpha = 0.4f))  // fallback
}

// Context menu shown as animated overlay in screen Box
if (contextMenuTarget != null) {
    Box(
        Modifier
            .fillMaxSize()
            .then(blurModifier)
            .clickable { contextMenuTarget = null }
    )
    // Menu card animated with spring scale
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter   = scaleIn(spring(dampingRatio = 0.65f, stiffness = 380f)) + fadeIn(tween(80)),
        exit    = scaleOut(tween(120)) + fadeOut(tween(80)),
    ) {
        ContextMenuCard(target = contextMenuTarget!!, onAction = { ... })
    }
}
```

### Pattern 7: Typing wave indicator (CH-04)

```kotlin
@Composable
fun TypingWaveIndicator() {
    val transition = rememberInfiniteTransition(label = "wave")
    // Three dots, each riding a sine wave with staggered phase
    val phases = listOf(0f, (2 * PI / 3).toFloat(), (4 * PI / 3).toFloat())
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue  = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "time",
    )
    // Scope InfiniteTransition to this composable — disposed when it leaves LazyColumn
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        phases.forEach { phase ->
            val y = sin(time + phase) * 4.dp.value  // 4dp amplitude
            Box(
                Modifier
                    .size(6.dp)
                    .offset(y = y.dp)
                    .clip(CircleShape)
                    .background(KotoTheme.colors.onSurfaceLow)
            )
        }
    }
}
```

Important: wrap in `AnimatedVisibility` in the LazyColumn item so the `InfiniteTransition` is disposed when typing stops (pitfall A4 prevention).

### Pattern 8: Message send animation (CH-11)

```kotlin
// On new outgoing message inserted, composable triggers via LaunchedEffect on message id
val scale   = remember { Animatable(0.9f) }
val alpha   = remember { Animatable(0f) }
LaunchedEffect(msg.id) {
    // Parallel: alpha 0→1 over 100ms
    launch { alpha.animateTo(1f, tween(100)) }
    // Sequential: scale 0.9 → 1.02 → 1.0 (spring settle)
    scale.animateTo(1.02f, spring(dampingRatio = 0.6f, stiffness = 400f))
    scale.animateTo(1.0f,  spring(dampingRatio = 0.8f, stiffness = 300f))
}
Box(Modifier.scale(scale.value).alpha(alpha.value)) { BubbleContent(msg) }
```

### Pattern 9: Scroll-to-bottom FAB (CH-08)

```kotlin
// derivedStateOf prevents recomposition on every scroll pixel (PITFALLS.md P7)
val showFab by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 2 }
}

AnimatedVisibility(
    visible = showFab,
    enter   = scaleIn(KotoTheme.motion.springBouncy) + fadeIn(tween(80)),
    exit    = scaleOut(tween(120)) + fadeOut(tween(80)),
) {
    FloatingActionButton(
        onClick = { scope.launch { listState.animateScrollToItem(0) } },  // 0 = bottom in reversed
        modifier = Modifier.size(48.dp),
    ) {
        // Badge with unread count
        if (unreadBelowFold > 0) Badge { Text("$unreadBelowFold") }
        Icon(Icons.Default.KeyboardArrowDown, ...)
    }
}
```

### Pattern 10: Image preview with BlurHash (CH-07)

```kotlin
// Coil 3 with BlurHash placeholder
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(msg.mediaUrl)
        .crossfade(300)
        .placeholder(
            if (msg.blurHash != null)
                BitmapPainter(decodeBlurHash(msg.blurHash, 32, 32).asImageBitmap())
            else
                ColorPainter(KotoTheme.colors.surfaceVariant)
        )
        .build(),
    contentDescription = null,
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 280.dp)
        .clip(RoundedCornerShape(12.dp)),
    contentScale = ContentScale.Crop,
)
```

### Anti-Patterns to Avoid

- **`AnimatedVisibility(visible = true)` on every item** — replays enter animation on recomposition. Use `Modifier.animateItem()` instead for LazyColumn items.
- **`Dialog` for context menu** — breaks SharedTransitionScope. Use `Popup` or overlay `Box` in the screen composition tree.
- **`imePadding()` on both Scaffold bottomBar AND LazyColumn** — causes double-padding. Apply only on the scrollable content.
- **`SimpleDateFormat` in composition** — not thread-safe, runs per-frame. Pre-compute in ViewModel.
- **`firstVisibleItemIndex == messages.lastIndex`** — wrong when `reverseLayout = true`. The semantics are inverted; 0 = bottom.
- **Hoisting `InfiniteTransition` to screen level** — keeps running when typing indicator is hidden. Scope it to the typing indicator composable and wrap in `AnimatedVisibility`.
- **Inline lambdas in LazyColumn items** — `onClick = { viewModel.onAction(msg.id) }` allocates per-recomposition. Extract as `remember { viewModel::onAction }` (PITFALLS.md P2).
- **`RenderEffect` without API check** — crashes on API < 31. Always gate on `Build.VERSION.SDK_INT >= 31`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image loading + caching + crossfade | Custom HTTP + Bitmap cache | `AsyncImage` (Coil 3.1.0) | Memory cache, disk cache, correct lifecycle cancellation |
| Swipe offset animation on gesture release | Custom `Animatable` loop | `Animatable.animateTo()` with spring spec | Velocity-preserving interruption is built in |
| LazyColumn item appear/disappear animations | `AnimatedVisibility` wrapper on each item | `Modifier.animateItem()` | Correct semantics — item key tracking, no replay on recompose |
| Item recomposition minimization | `equals()` overrides, manual diffing | `@Immutable` + `ImmutableList` + compiler reports | The Compose compiler skips recomposition automatically |
| Wave animation math | Canvas shader / custom drawing library | `Canvas` + `sin()` with `InfiniteTransition` | No deps; performant; fully controllable |
| BlurHash decode | External blurhash lib dependency | 100-line inline `BlurHashDecoder.kt` | Zero dep risk; algorithm is well-specified and stable |
| Scroll position management on keyboard open | `adjustResize` window soft input mode | `WindowCompat.setDecorFitsSystemWindows(false)` + `imePadding()` | The correct, modern approach; adjustResize is deprecated |

**Key insight:** 95% of the visual premium in Phase 2 comes from correct use of built-in Compose animation APIs + token adherence. No new animation library is needed.

---

## Common Pitfalls

### Pitfall 1: Auto-scroll on new message without checking position

**What goes wrong:** ViewModel calls `scrollToItem(0)` whenever `messages.size` changes. User was reading old history — they get teleported to the bottom.
**Why it happens:** Naive `LaunchedEffect(messages.size)` with unconditional scroll.
**How to avoid:** Only auto-scroll if `listState.firstVisibleItemIndex <= 1` (user already at bottom or one message above). Use `snapshotFlow { listState.firstVisibleItemIndex }` to track position reactively.
**Warning signs:** Users report losing their read position when messages arrive.

### Pitfall 2: Double imePadding causing jump

**What goes wrong:** `imePadding()` applied both in Scaffold bottomBar AND on LazyColumn. Keyboard open shifts content twice.
**Why it happens:** Compose propagates `WindowInsets.ime` down the tree; applying it twice consumes it twice.
**How to avoid:** Apply `consumeWindowInsets(WindowInsets.ime)` on Scaffold if it consumes bottom insets, then apply `imePadding()` only on the LazyColumn. Or apply only once on the LazyColumn and set Scaffold's `contentWindowInsets = WindowInsets(0)`.
**Warning signs:** Chat list visually shifts twice when keyboard opens/closes.

### Pitfall 3: Typing indicator InfiniteTransition running when hidden

**What goes wrong:** `TypingWaveIndicator` with `rememberInfiniteTransition` is added to the LazyColumn but the `isTyping` condition hides it visually without removing it from composition. The animation continues consuming CPU.
**Why it happens:** Wrapping in `if (isTyping) { ... }` inside the LazyColumn `item {}` block works correctly — it removes the composable from composition entirely. But if the indicator is always in the tree with `alpha = 0`, the transition runs.
**How to avoid:** Use conditional item insertion: `if (isTyping) { item(key = "typing") { ... } }`. The item is physically absent from the list when not typing.
**Warning signs:** CPU profiler shows typing animation thread active when no typing event in 10+ seconds.

### Pitfall 4: Swipe-to-reply consuming vertical scroll events

**What goes wrong:** `detectHorizontalDragGestures` on message row intercepts vertical scroll events during diagonal swipes, making the list sluggish.
**Why it happens:** Gesture detectors compete; without direction locking, horizontal detector wins on ambiguous moves.
**How to avoid:** In `onHorizontalDrag`, only call `change.consume()` after the horizontal displacement exceeds `touchSlop` in the horizontal direction significantly more than vertical. The `detectHorizontalDragGestures` API already does some of this, but diagonal flings need extra care.
**Warning signs:** Fast diagonal scrolling feels "sticky"; scroll velocity drops near messages.

### Pitfall 5: RenderEffect blur crashing on API < 31

**What goes wrong:** `RenderEffect.createBlurEffect()` throws `NoSuchMethodError` or doesn't compile on minSdk 26.
**Why it happens:** `RenderEffect` was added in API 31. Compose's `Modifier.blur()` is also API 31+.
**How to avoid:** Gate all blur usage: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { ... }`. Provide a fallback: `Modifier.background(Color.Black.copy(alpha = 0.4f))`.
**Warning signs:** App crashes on Android 8-11 devices during long-press.

### Pitfall 6: State flow granularity — typing/input updates recompose message list

**What goes wrong:** A single `StateFlow<ChatState>` containing `inputText`, `sending`, `isTyping`, and `messages` causes the `LazyColumn` to recompose every time the user types a character.
**Why it happens:** `collectAsState()` on a single flow — any field change = full recompose of the collecting composable.
**How to avoid:** Split into separate derived flows or use `distinctUntilChanged { old, new -> old.messages == new.messages }` for the message list sub-state. Or collect separate `StateFlow<ImmutableList<MessageUi>>` for the message list and `StateFlow<String>` for input text.
**Warning signs:** Layout Inspector shows message list recomposition count incrementing on every keypress.

### Pitfall 7: buildChatItems() called in composition

**What goes wrong:** `val items = remember(messages) { buildChatItems(messages) }` appears in `ChatScreen` composable directly. On initial load with 200 messages, this blocks the main thread.
**Why it happens:** `remember {}` runs synchronously on the composition thread.
**How to avoid:** Run `buildChatItems()` in the ViewModel's `viewModelScope` on a background dispatcher: `flowOf(messages).map { buildChatItems(it) }.flowOn(Dispatchers.Default)`. Expose the result as `StateFlow<ImmutableList<ChatItem>>`.
**Warning signs:** Jank on initial chat open; especially visible on messages with many date separators.

---

## Code Examples

### Stability-correct LazyColumn (CH-09)

```kotlin
// Source: ARCHITECTURE.md ChatMessageList pattern + PITFALLS.md P1, P4
@Composable
fun ChatMessageList(
    items     : ImmutableList<ChatItem>,    // ImmutableList — stable
    listState : LazyListState,
    isTyping  : Boolean,
    modifier  : Modifier = Modifier,
) {
    LazyColumn(
        state         = listState,
        reverseLayout = true,
        modifier      = modifier,
    ) {
        if (isTyping) {
            item(key = "typing-indicator", contentType = 99) {
                TypingWaveIndicator(Modifier.padding(start = 8.dp, bottom = 4.dp).animateItem())
            }
        }
        items(
            items       = items,
            key         = { it.key },
            contentType = { when (it) {
                is ChatItem.Message       -> it.contentType
                is ChatItem.DateSeparator -> 0
            }},
        ) { item ->
            when (item) {
                is ChatItem.Message       -> MessageRow(item, Modifier.animateItem())
                is ChatItem.DateSeparator -> DateSeparatorRow(item.label)
            }
        }
    }
}
```

### Lambda stability in list items (PITFALLS.md P2)

```kotlin
// BAD — new lambda every recomposition
MessageRow(item, onLongPress = { viewModel.onLongPress(item.msg.id) })

// GOOD — stable reference
val onLongPress = remember { viewModel::onLongPress }
MessageRow(item, onLongPress = { onLongPress(item.msg.id) })
```

### Deferred state read for FAB (PITFALLS.md P3, P7)

```kotlin
// BAD — reads state in composition, triggers recompose every scroll pixel
val show = listState.firstVisibleItemIndex > 2

// GOOD — derivedStateOf batches to only trigger when boolean changes
val show by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact for Phase 2 |
|--------------|------------------|--------------|---------------------|
| `RecyclerView` chat list | `LazyColumn` with `reverseLayout = true` | Compose 1.0 | Use LazyColumn exclusively; no RecyclerView interop |
| Manual item animations | `Modifier.animateItem()` | Compose 1.7 / BOM 2026.03.01 | Available — replace `AnimatedVisibility(visible=true)` wrappers |
| `AnimatedNavHost` (Accompanist) | Native `NavHost` animation + `SharedTransitionLayout` | Nav Compose 2.7+ | SharedTransitionLayout for avatar morph is Phase 4; not needed in Phase 2 |
| Accompanist placeholder | Custom shimmer or BlurHash | 2024 | BlurHash for media, shimmer is optional |
| `adjustResize` soft input mode | `WindowCompat.setDecorFitsSystemWindows(false)` + `imePadding()` | 2022 | The correct approach — verify MainActivity uses `enableEdgeToEdge()` |
| `SubcomposeAsyncImage` | `AsyncImage` with fixed dimensions | Coil 3.x | `AsyncImage` is faster; avoid `SubcomposeAsyncImage` for list items |

---

## Open Questions

1. **Does `MainActivity` call `enableEdgeToEdge()` or `WindowCompat.setDecorFitsSystemWindows(window, false)`?**
   - What we know: The current `MessageInputBar` applies `imePadding()` on its inner `Row`. This only works correctly if the window is in edge-to-edge mode.
   - What's unclear: `MainActivity.kt` was not read during research — it may or may not have `enableEdgeToEdge()`.
   - Recommendation: Wave 0 task should read `MainActivity.kt` and add `enableEdgeToEdge()` if absent. If already present, no action needed.

2. **Does `ChatViewModel` need a separate state flow for the message list vs. input state?**
   - What we know: A single `StateFlow<ChatState>` with 6+ fields will recompose the entire screen on every keystroke.
   - What's unclear: Whether the Compose compiler's strong-skipping mode (Kotlin 2.1.20 K2) handles this automatically when `ChatState` is `@Immutable`.
   - Recommendation: Split into `StateFlow<ImmutableList<ChatItem>>` for the list and `StateFlow<InputState>` for text/sending. The split is cheap to do in Wave 0 and avoids the risk.

3. **BlurHash field population in the backend**
   - What we know: `MessageUi.blurHash: String?` exists and is nullable. `MessageEntity` is the Room source.
   - What's unclear: Does `MessageEntity` have a `blurHash` column? Does the backend media service return blurHash in message payloads?
   - Recommendation: Read `MessageEntity.kt` and `ChatApi.kt` DTO definitions in Wave 0 before implementing CH-07. If `blurHash` is not stored in Room, add a migration or fallback to `ColorPainter` placeholder.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 2 is entirely Android Kotlin/Compose code changes. No new external tools, databases, or CLI utilities are introduced. All build tooling (Gradle, KSP, Cargo for uniffi) was already audited in Phase 1.

---

## Validation Architecture

> `workflow.nyquist_validation` not explicitly set to false — validation section included.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None detected (no `test/` directory found in android/app/src/test; no instrumented tests) |
| Config file | None — Wave 0 must create test infrastructure if validation is desired |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "run.koto.ui.screens.chat.*" -x lint` |
| Full suite command | `./gradlew :app:testDebugUnitTest` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CH-02 | `buildChatItems()` grouping + date separators | unit | `./gradlew :app:testDebugUnitTest --tests "*.ChatItemMapperTest"` | ❌ Wave 0 |
| CH-03 | `MessageStatus` transitions in ViewModel | unit | `./gradlew :app:testDebugUnitTest --tests "*.ChatViewModelTest"` | ❌ Wave 0 |
| CH-09 | `ImmutableList` stability — compiler report shows 0 unstable in chat path | build | `./gradlew :app:assembleDebug` + inspect `build/compose_compiler/` reports | ❌ requires composeCompiler config |
| CH-10 | No double-padding — unit check imePadding placement | manual | Device / emulator: open keyboard, verify no gap/jump | manual only |
| CH-01, CH-04, CH-05, CH-06, CH-07, CH-08, CH-11, CH-12 | Visual / animation | manual | Device / emulator | manual only |

### Sampling Rate

- Per task commit: `./gradlew :app:assembleDebug` (compilation gate)
- Per wave merge: `./gradlew :app:testDebugUnitTest` (unit tests for mapping logic)
- Phase gate: Full build green + manual device verification of CH-01 through CH-12

### Wave 0 Gaps

- [ ] `android/app/src/test/java/run/koto/ui/screens/chat/ChatItemMapperTest.kt` — covers CH-02 grouping logic
- [ ] `android/app/src/test/java/run/koto/ui/screens/chat/ChatViewModelTest.kt` — covers CH-03 state transitions
- [ ] Compose compiler report config in `android/app/build.gradle.kts`:
  ```kotlin
  composeCompiler {
      reportsDestination = layout.buildDirectory.dir("compose_compiler")
      metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
  ```
  Add this to enable stability verification for CH-09 (PF-02 compliance).

---

## Sources

### Primary (HIGH confidence)

- `.planning/research/PITFALLS.md` — all pitfall patterns; P1, P2, P3, P4, P7, C1, C2, C4, A2, A4, A6, S2 directly referenced
- `.planning/research/ARCHITECTURE.md` — ChatMessageList pattern, reverseLayout semantics, sealed ChatItem pattern
- `.planning/research/FEATURES.md` — exact pixel/dp specs: bubble corners 18dp/4dp, group window 60s, reply threshold 48dp, typing timeout 8s, FAB threshold 300dp, BlurHash 11x11, context menu scrim 40% black blur 12px
- `.planning/research/STACK.md` — library versions, BlurHash inline recommendation, Coil 3 usage pattern
- `android/app/src/main/java/run/koto/ui/theme/Colors.kt` — confirmed `bubbleGradient`, `bubbleIn`, token names
- `android/app/src/main/java/run/koto/ui/theme/Motion.kt` — confirmed spring/tween spec names
- `android/app/src/main/java/run/koto/domain/model/UiModels.kt` — confirmed `@Immutable ChatState`, `MessageStatus`, `MessageUi.replyTo`, `MessageUi.blurHash`
- `android/app/src/main/java/run/koto/ui/screens/chat/ChatScreen.kt` — confirmed current skeleton, identified 6 pre-existing issues
- `android/app/src/main/java/run/koto/ui/screens/chat/ChatViewModel.kt` — confirmed duplicate `ChatState` conflict
- `android/gradle/libs.versions.toml` — confirmed BOM 2026.03.01, Coil 3.1.0, kotlinx-collections-immutable 0.4.0
- `.planning/phases/01-design-system-foundation/01-VERIFICATION.md` — confirmed Phase 1 delivered artifacts

### Secondary (MEDIUM confidence)

- `CLAUDE.md` — project constraints, naming conventions, stack constraints

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all libraries confirmed in `libs.versions.toml`; no new dependencies except inline BlurHashDecoder
- Architecture: HIGH — patterns taken directly from project-authored ARCHITECTURE.md and PITFALLS.md, cross-verified against actual source files
- Pitfalls: HIGH — all 7 pitfalls traced to specific source files (ChatScreen.kt issues 1-6) or PITFALLS.md rated CRITICAL/MAJOR
- Code examples: MEDIUM — patterns are correct but exact imports and final API shapes should be verified against Compose BOM 2026.03.01 at implementation time

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable stack; Compose BOM quarterly release cycle)
