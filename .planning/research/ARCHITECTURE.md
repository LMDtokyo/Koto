# Architecture Patterns: Premium Compose Design System

**Domain:** Android messenger premium UI/UX (Jetpack Compose)
**Researched:** 2026-04-05 (updated with Navigation 3, animation deep-dive, stability/performance details)
**Overall Confidence:** HIGH (all patterns verified against official Android documentation)

---

## Recommended Architecture

The system is a layered UI architecture where each layer depends only on layers below it.
No layer reaches upward. Build proceeds strictly bottom-up.

```
┌─────────────────────────────────────────────────────────┐
│  SCREENS (ChatScreen, ConversationsScreen, etc.)        │  Layer 5
│  Compose screens, ViewModels, state hoisting            │
├─────────────────────────────────────────────────────────┤
│  ORGANISMS (ChatBubbleRow, ConversationItem, AppBar)    │  Layer 4
│  Feature-level composables — combine molecules          │
├─────────────────────────────────────────────────────────┤
│  MOLECULES (MessageBubble, AvatarWithBadge, InputBar)   │  Layer 3
│  Reusable combinations — no domain knowledge           │
├─────────────────────────────────────────────────────────┤
│  ATOMS (KotoButton, KotoTextField, Avatar, Badge, FAB)  │  Layer 2
│  Single-purpose, stateless, purely parameterized        │
├─────────────────────────────────────────────────────────┤
│  DESIGN TOKENS (KotoTheme)                              │  Layer 1
│  Colors, typography, spacing, shapes, motion specs      │
│  Delivered via CompositionLocal, never hardcoded        │
└─────────────────────────────────────────────────────────┘
```

**Build order:** Tokens → Atoms → Molecules → Organisms → Screens → Animations → Navigation

---

## Layer 1: Design Token System

### Philosophy

Tokens are the single source of truth for every visual value. Nothing in components
or screens references raw color values, raw sp/dp values, or raw durations.
Accessing a value like `Color(0xFF1A1A2E)` anywhere outside the token file is a bug.

### Token Categories

| Category | Kotlin Type | Access Pattern |
|----------|-------------|----------------|
| Colors | `KotoColors` | `KotoTheme.colors.primary` |
| Typography | `KotoTypography` | `KotoTheme.typography.bodyLarge` |
| Spacing | `KotoSpacing` | `KotoTheme.spacing.md` |
| Shapes | `KotoShapes` | `KotoTheme.shapes.bubble` |
| Elevation | `KotoElevation` | `KotoTheme.elevation.card` |
| Motion | `KotoMotion` | `KotoTheme.motion.springSnappy` |

### Color Token Structure

Colors are structured in two tiers: semantic tokens (role-based) over raw palette values.
Raw palette values are `internal` and never accessed directly.

```kotlin
// internal — raw palette, not exposed
internal object KotoPalette {
    val inkBlack    = Color(0xFF0D0D0D)
    val inkDark     = Color(0xFF1A1A2E)
    val violet500   = Color(0xFF7B61FF)
    val violet400   = Color(0xFF9D8FFF)
    val surface0    = Color(0xFF141416)
    val surface1    = Color(0xFF1E1E22)
    val surface2    = Color(0xFF28282E)
    val ghostWhite  = Color(0xFFF5F5FA)
    val messageOut  = Color(0xFF2B2040)
    val messageIn   = Color(0xFF1E1E26)
}

// public — semantic tokens
@Immutable
data class KotoColors(
    // Brand
    val primary       : Color,
    val onPrimary     : Color,
    // Surfaces
    val background    : Color,
    val surface       : Color,
    val surfaceVariant: Color,
    // Content
    val onBackground  : Color,
    val onSurface     : Color,
    val onSurfaceLow  : Color,   // secondary text
    val onSurfaceMuted: Color,   // hints, timestamps
    // Chat-specific semantic tokens
    val bubbleOut     : Color,   // outgoing message bubble
    val bubbleIn      : Color,   // incoming message bubble
    val onBubbleOut   : Color,
    val onBubbleIn    : Color,
    // Utility
    val divider       : Color,
    val error         : Color,
    val online        : Color,   // presence indicator green
)

val DarkKotoColors = KotoColors(
    primary        = KotoPalette.violet500,
    onPrimary      = Color.White,
    background     = KotoPalette.inkBlack,
    surface        = KotoPalette.surface1,
    surfaceVariant = KotoPalette.surface2,
    onBackground   = KotoPalette.ghostWhite,
    onSurface      = KotoPalette.ghostWhite,
    onSurfaceLow   = KotoPalette.ghostWhite.copy(alpha = 0.60f),
    onSurfaceMuted = KotoPalette.ghostWhite.copy(alpha = 0.38f),
    bubbleOut      = KotoPalette.messageOut,
    bubbleIn       = KotoPalette.messageIn,
    onBubbleOut    = KotoPalette.ghostWhite,
    onBubbleIn     = KotoPalette.ghostWhite,
    divider        = Color.White.copy(alpha = 0.08f),
    error          = Color(0xFFFF5252),
    online         = Color(0xFF4CAF50),
)
```

### Typography Token Structure

```kotlin
@Immutable
data class KotoTypography(
    // Display
    val displayLarge  : TextStyle,
    val displayMedium : TextStyle,
    // Headlines
    val headlineLarge : TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall : TextStyle,
    // Body
    val bodyLarge     : TextStyle,   // message text
    val bodyMedium    : TextStyle,   // conversation preview
    val bodySmall     : TextStyle,   // timestamps, metadata
    // Labels
    val labelLarge    : TextStyle,   // buttons
    val labelMedium   : TextStyle,   // chips, tabs
    val labelSmall    : TextStyle,   // badge count
    // Messenger-specific
    val chatBubble    : TextStyle,   // message body in bubble
    val chatTimestamp  : TextStyle,   // small timestamp below bubble
    val inputField    : TextStyle,   // text input
)

val DefaultKotoTypography = KotoTypography(
    displayLarge   = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold,
                               lineHeight = 40.sp, letterSpacing = 0.sp),
    displayMedium  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,
                               lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineLarge  = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                               lineHeight = 28.sp),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                               lineHeight = 24.sp),
    headlineSmall  = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                               lineHeight = 22.sp),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 20.sp),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 16.sp),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,
                               lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,
                               lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium,
                               lineHeight = 14.sp, letterSpacing = 0.5.sp),
    chatBubble     = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 21.sp),
    chatTimestamp   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 14.sp),
    inputField     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,
                               lineHeight = 22.sp),
)
```

### Elevation Token Structure

```kotlin
@Immutable
data class KotoElevation(
    val none    : Dp = 0.dp,
    val low     : Dp = 1.dp,
    val card    : Dp = 2.dp,
    val appBar  : Dp = 4.dp,
    val fab     : Dp = 6.dp,
    val dialog  : Dp = 8.dp,
    val pressed : Dp = 8.dp,   // interactive press elevation
)
```

### Spacing Token Structure

```kotlin
@Immutable
data class KotoSpacing(
    val xxs  : Dp =  2.dp,
    val xs   : Dp =  4.dp,
    val sm   : Dp =  8.dp,
    val md   : Dp = 12.dp,
    val lg   : Dp = 16.dp,
    val xl   : Dp = 20.dp,
    val xxl  : Dp = 24.dp,
    val xxxl : Dp = 32.dp,
    // Named semantic tokens for common use
    val bubblePaddingH  : Dp = 14.dp,
    val bubblePaddingV  : Dp = 10.dp,
    val listItemPadding : Dp = 16.dp,
    val inputBarPadding : Dp = 12.dp,
)
```

### Shape Token Structure

```kotlin
@Immutable
data class KotoShapes(
    val bubbleOut     : Shape = RoundedCornerShape(18.dp, 18.dp,  4.dp, 18.dp),
    val bubbleIn      : Shape = RoundedCornerShape(18.dp, 18.dp, 18.dp,  4.dp),
    val avatar        : Shape = CircleShape,
    val card          : Shape = RoundedCornerShape(16.dp),
    val bottomSheet   : Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    val inputField    : Shape = RoundedCornerShape(24.dp),
    val pill          : Shape = CircleShape,
)
```

### Motion Token Structure

```kotlin
@Immutable
data class KotoMotion(
    // Spring specs (physics-based — prefer for gestures, responses)
    val springSnappy     : SpringSpec<Float>,
    val springBouncy     : SpringSpec<Float>,
    val springGentle     : SpringSpec<Float>,
    // Tween specs (duration-based — prefer for explicit UI transitions)
    val tweenFast        : TweenSpec<Float>,   // 150ms FastOutSlowIn
    val tweenMedium      : TweenSpec<Float>,   // 300ms FastOutSlowIn
    val tweenSlow        : TweenSpec<Float>,   // 450ms LinearOutSlowIn
)

val DefaultKotoMotion = KotoMotion(
    springSnappy  = spring(stiffness = Spring.StiffnessMediumLow,
                           dampingRatio = Spring.DampingRatioNoBouncy),
    springBouncy  = spring(stiffness = Spring.StiffnessMedium,
                           dampingRatio = Spring.DampingRatioLowBouncy),
    springGentle  = spring(stiffness = Spring.StiffnessLow,
                           dampingRatio = Spring.DampingRatioNoBouncy),
    tweenFast     = tween(durationMillis = 150, easing = FastOutSlowInEasing),
    tweenMedium   = tween(durationMillis = 300, easing = FastOutSlowInEasing),
    tweenSlow     = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
)
```

---

## Layer 1: Theme Provider Pattern

### CompositionLocal Setup

Use `staticCompositionLocalOf` for values that rarely change (colors, shapes, spacing).
Use `compositionLocalOf` only for values that change frequently within the tree (rare).

```kotlin
// Each token category has its own CompositionLocal
val LocalKotoColors    = staticCompositionLocalOf { DarkKotoColors }
val LocalKotoTypography= staticCompositionLocalOf { DefaultKotoTypography }
val LocalKotoSpacing   = staticCompositionLocalOf { KotoSpacing() }
val LocalKotoShapes    = staticCompositionLocalOf { KotoShapes() }
val LocalKotoElevation = staticCompositionLocalOf { KotoElevation() }
val LocalKotoMotion    = staticCompositionLocalOf { DefaultKotoMotion }

// Singleton access object — no @Composable overhead at call site
object KotoTheme {
    val colors    : KotoColors     @Composable get() = LocalKotoColors.current
    val typography: KotoTypography @Composable get() = LocalKotoTypography.current
    val spacing   : KotoSpacing    @Composable get() = LocalKotoSpacing.current
    val shapes    : KotoShapes     @Composable get() = LocalKotoShapes.current
    val elevation : KotoElevation  @Composable get() = LocalKotoElevation.current
    val motion    : KotoMotion     @Composable get() = LocalKotoMotion.current
}
```

### Theme Provider Composable

```kotlin
@Composable
fun KotoTheme(
    darkTheme  : Boolean = isSystemInDarkTheme(),
    content    : @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkKotoColors else LightKotoColors

    // Animated color transition on theme switch (dark ↔ light)
    val animatedColors = animateKotoColors(colors)

    CompositionLocalProvider(
        LocalKotoColors     provides animatedColors,
        LocalKotoTypography provides DefaultKotoTypography,
        LocalKotoSpacing    provides KotoSpacing(),
        LocalKotoShapes     provides KotoShapes(),
        LocalKotoElevation  provides KotoElevation(),
        LocalKotoMotion     provides DefaultKotoMotion,
    ) {
        // Still wrap MaterialTheme so Material components (needed for
        // ModalBottomSheet, Snackbar, etc.) receive correct values
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
```

### Animated Theme Transition (Dark ↔ Light)

Animate each color individually via `animateColorAsState`:

```kotlin
@Composable
private fun animateKotoColors(target: KotoColors): KotoColors {
    val spec = tween<Color>(durationMillis = 300, easing = FastOutSlowInEasing)
    return KotoColors(
        primary        = animateColorAsState(target.primary,        spec, "primary").value,
        background     = animateColorAsState(target.background,     spec, "background").value,
        surface        = animateColorAsState(target.surface,        spec, "surface").value,
        bubbleOut      = animateColorAsState(target.bubbleOut,      spec, "bubbleOut").value,
        bubbleIn       = animateColorAsState(target.bubbleIn,       spec, "bubbleIn").value,
        // ... all other colors
    )
}
```

This is the recommended approach from the official docs (option 2 in the custom design
system guide). It keeps colors stable across the composition tree and allows smooth
animated transitions without rebuilding the entire theme object.

---

## Layer 2: Atom Components

Atoms are single-purpose composables that accept only token-referenced values.
They have no business logic, no ViewModels, no side effects.

### Component Boundary Rules

- Accept only primitive types + lambdas (no domain model objects)
- Accept `Modifier` as first non-required parameter after content params
- Default every parameter that has a sensible default
- Read tokens via `KotoTheme.*` — never hardcode
- Mark all `data class` parameters with `@Immutable` (see stability section)

### Key Atoms

```
ui/components/atoms/
├── KotoButton.kt          // Primary, secondary, text, icon variants
├── KotoTextField.kt       // Chat input, search field
├── Avatar.kt              // Circular image with presence ring
├── Badge.kt               // Unread count pill, typing dot
├── StatusDot.kt           // Online/offline/away indicator
├── KotoIconButton.kt      // Icon with ripple bounded to shape
├── KotoChip.kt            // Tag, filter, reaction chip
└── Shimmer.kt             // Skeleton loading placeholder
```

### Example: Avatar Atom

```kotlin
@Composable
fun Avatar(
    imageUrl   : String?,
    initials   : String,
    size       : Dp     = 40.dp,
    isOnline   : Boolean = false,
    modifier   : Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        AsyncImage(
            model            = imageUrl,
            contentDescription = null,
            contentScale     = ContentScale.Crop,
            modifier         = Modifier
                .fillMaxSize()
                .clip(KotoTheme.shapes.avatar),
            // Fallback: initials placeholder handled via error painter
        )
        if (isOnline) {
            StatusDot(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
            )
        }
    }
}
```

---

## Layer 3: Molecule Components

Molecules combine atoms with layout logic. Still stateless — they receive state, emit events.

```
ui/components/molecules/
├── MessageBubble.kt        // Bubble + text + timestamp + status
├── AvatarWithBadge.kt      // Avatar + unread Badge overlay
├── ConversationRow.kt      // Avatar + name/preview + timestamp
├── TypingIndicator.kt      // Three animated dots
├── ReactionRow.kt          // Emoji reaction pills
├── InputBar.kt             // TextField + send button + attachment
└── SearchBar.kt            // Search field + clear
```

### State Hoisting Pattern for Molecules

Molecules own no state. They receive lambdas for every interaction.

```kotlin
@Composable
fun InputBar(
    value          : String,
    onValueChange  : (String) -> Unit,
    onSend         : () -> Unit,
    onAttachment   : () -> Unit,
    isSending      : Boolean     = false,
    modifier       : Modifier    = Modifier,
)
```

The screen-level ViewModel holds `inputText` in its `StateFlow<ScreenState>`.
`InputBar` reads and emits — it never remembers.

---

## Layer 4: Organism Components

Organisms are feature-level. They may receive `List<MessageUi>` (domain model) because
they are the boundary where domain UI models meet visual components. They still do not
hold `ViewModel` references.

```
ui/components/organisms/
├── ChatMessageList.kt      // LazyColumn of messages + date separators
├── ConversationList.kt     // LazyColumn of conversations
├── AppTopBar.kt            // Custom top bar with back, title, actions
├── KotoBottomSheet.kt      // Custom bottom sheet with drag handle
├── KotoNavigationBar.kt    // Custom bottom nav with animated indicator
└── ChatAppBar.kt           // Chat-specific top bar (avatar, name, call btn)
```

### ChatMessageList Key Architecture Decisions

```kotlin
@Composable
fun ChatMessageList(
    messages          : ImmutableList<MessageUi>,  // ImmutableList for stability
    scrollState       : LazyListState,
    modifier          : Modifier = Modifier,
) {
    LazyColumn(
        state         = scrollState,
        reverseLayout = true,                       // newest at bottom
        modifier      = modifier,
    ) {
        items(
            items = messages,
            key   = { it.id },                      // stable key — prevents recomposition on scroll
            contentType = { it.contentType },       // helps Compose reuse item layouts
        ) { message ->
            MessageBubble(
                message  = message,
                modifier = Modifier.animateItem()   // built-in item animation for insert/remove
            )
        }
    }
}
```

`ImmutableList<MessageUi>` from `kotlinx.collections.immutable` is mandatory here.
Standard `List<MessageUi>` is unstable and causes full-list recomposition on any update.

---

## Layer 5: Screens and Navigation

### Navigation Architecture: Compose Navigation with Type Safety

**Decision:** Use Jetpack `navigation-compose` 2.8+ with type-safe `@Serializable` routes.
**Rationale:** Official recommended approach, integrates SharedTransitionScope natively,
handles deep links, no additional dependency. Decompose/Voyager add complexity without
benefit at this project scale.

**Navigation 3 (Future Path):** Google announced Navigation 3 (2025) as a Compose-first
navigation library with full back stack control, multi-destination adaptive layouts, and
simpler APIs via `NavDisplay`. It is in prerelease. When stable, migrate to it for:
- Native `NavDisplay` composable instead of `NavHost`
- `SceneStrategy` for adaptive layouts (list+detail without navigation)
- Direct back stack manipulation without `NavController`
- Built-in animation between destinations

For now, Navigation Compose 2.9+ is the stable choice. The architecture below is designed
so that migration to Navigation 3 requires changing only `KotoNavGraph.kt`.

```kotlin
@Serializable object ConversationsRoute
@Serializable data class ChatRoute(val convId: String)
@Serializable object SettingsRoute
@Serializable object OnboardingRoute
@Serializable object LockRoute

@Composable
fun KotoNavGraph(
    navController : NavHostController = rememberNavController(),
) {
    // SharedTransitionLayout wraps NavHost — enables shared element transitions
    // between ANY two destinations without manual scope passing
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
        ) {
            NavHost(
                navController    = navController,
                startDestination = ConversationsRoute,
            ) {
                composable<ConversationsRoute> {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        ConversationsScreen(
                            onOpenChat = { convId ->
                                navController.navigate(ChatRoute(convId))
                            }
                        )
                    }
                }
                composable<ChatRoute> { backStack ->
                    val route = backStack.toRoute<ChatRoute>()
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        ChatScreen(
                            convId = route.convId,
                            onBack = navController::popBackStack,
                        )
                    }
                }
            }
        }
    }
}

// CompositionLocals for scope propagation — avoids passing scopes through every layer
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
```

### Screen Structure

Each screen follows the existing MVI/UDF convention (preserving current CONVENTIONS.md):

```kotlin
@Composable
fun ConversationsScreen(
    onOpenChat : (String) -> Unit,
    viewModel  : ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Screen owns scroll state and other UI-only state
    val listState = rememberLazyListState()

    // Hoist: all state mutations go through ViewModel
    ConversationsContent(
        state      = state,
        listState  = listState,
        onOpenChat = onOpenChat,
        onRefresh  = viewModel::refresh,
    )
}

// Separate content composable for previews and testing
@Composable
private fun ConversationsContent(
    state      : ConversationsState,
    listState  : LazyListState,
    onOpenChat : (String) -> Unit,
    onRefresh  : () -> Unit,
)
```

---

## Animation Architecture

### Animation API Decision Tree

Choose the right API based on context:

| Scenario | API | Why |
|----------|-----|-----|
| Show/hide a composable | `AnimatedVisibility` | Handles enter/exit transitions |
| Switch between different content | `AnimatedContent` | Transitions with `ContentTransform` |
| Animate a single value (alpha, size) | `animate*AsState` | Simplest value animation |
| Animate multiple values together | `updateTransition` | Coordinates multiple animations |
| Infinite loop (typing dots, pulse) | `rememberInfiniteTransition` | No allocation per frame |
| Gesture-driven (swipe, drag) | `Animatable` + coroutines | Full control, interruptible |
| Shared hero element | `sharedElement` / `sharedBounds` | Cross-destination morphing |

### AnimationSpec Types Reference

```kotlin
// SPRING — physics-based, interruptible, best for gestures and responses
spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,  // 1.0 = no bounce
    stiffness = Spring.StiffnessMedium           // higher = faster
)

// TWEEN — duration-based with easing curve
tween<Float>(
    durationMillis = 300,
    delayMillis = 0,
    easing = FastOutSlowInEasing  // standard Material easing
)

// KEYFRAMES — snapshot values at specific timestamps
keyframes<Float> {
    durationMillis = 375
    0.0f at 0 using LinearOutSlowInEasing
    0.2f at 15 using FastOutLinearInEasing
    0.4f at 75
    0.4f at 225    // hold at 0.4 for 150ms
}

// KEYFRAMES WITH SPLINES — smooth Bezier curves between points (Compose 1.7+)
keyframesWithSpline<Offset> {
    durationMillis = 600
    Offset(0f, 0f) at 0
    Offset(150f, 200f) atFraction 0.5f
    Offset(0f, 100f) atFraction 0.7f
}

// REPEATABLE — finite loop
repeatable<Float>(
    iterations = 3,
    animation = tween(300),
    repeatMode = RepeatMode.Reverse
)

// INFINITE REPEATABLE — infinite loop
infiniteRepeatable<Float>(
    animation = tween(300),
    repeatMode = RepeatMode.Reverse
)

// SNAP — instant jump (optionally delayed)
snap<Float>(delayMillis = 50)
```

**Built-in Easing Functions:**
- `FastOutSlowInEasing` — standard (Material default)
- `LinearOutSlowInEasing` — entering elements
- `FastOutLinearInEasing` — exiting elements
- `LinearEasing` — constant rate
- `CubicBezierEasing(a, b, c, d)` — custom cubic bezier

**Custom Easing:**
```kotlin
val CustomEasing = Easing { fraction -> fraction * fraction }  // quadratic
```

**Spring is preferred for messenger UI** because:
1. Interruption-safe: if a new message arrives mid-animation, spring naturally adjusts velocity
2. No fixed duration: animation duration is emergent from physics, feels natural
3. Touch-responsive: gesture velocity feeds directly into spring initial velocity

### Animation Spec Registry

All animation constants live in one file. Components import the spec by name.
No component defines its own duration or easing values.

```kotlin
// ui/theme/KotoMotion.kt
object KotoMotionSpec {
    // Navigation transitions
    val navEnter  = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(220)) { it / 12 }
    val navExit   = fadeOut(tween(90,  easing = FastOutLinearEasing))

    // Item animations
    val itemAppear  = spring<Float>(stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy)
    val itemBounce  = spring<Float>(stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioLowBouncy)

    // Micro-interactions
    val pressScale  = spring<Float>(stiffness = Spring.StiffnessHigh,
                                    dampingRatio = Spring.DampingRatioNoBouncy)

    // Content transitions
    val fadeThrough = fadeIn(tween(300)) togetherWith fadeOut(tween(150))
}
```

### Gesture → Animation Pipeline

For gesture-driven animations (swipe-to-reply, swipe-to-dismiss), use `AnchoredDraggableState`
with a spring spec for settling, and read the `offset` directly in draw/layout phases
via lambda-form modifiers to bypass composition entirely:

```kotlin
// Defer state read to layout phase — no recomposition during drag
Modifier.offset { IntOffset(x = draggableState.offset.roundToInt(), y = 0) }
// vs. (causes recomposition on every pixel):
Modifier.offset(x = draggableState.offset.roundToInt().dp)
```

### Shared Element Transitions (Conversation → Chat)

**Key concepts:**
- `sharedElement()` — same visual content on both sides (e.g., avatar image). Only target renders during transition.
- `sharedBounds()` — visually different content sharing the same area (e.g., compact card → expanded detail). Both sides visible during transition. Accepts `enter`/`exit` parameters.

**Best practice: Use data class keys, not strings:**
```kotlin
data class SharedElementKey(
    val convId: String,
    val type: SharedElementType
)

enum class SharedElementType {
    Avatar, Title, Background
}
```

**Modifier ordering rule:** Size modifiers go BEFORE `sharedElement`/`sharedBounds`.
Inconsistent ordering causes visual jumps during transition.

Avatar and conversation name animate from list item to chat app bar using `sharedElement`:

```kotlin
// In ConversationRow (source)
val sharedTransition = LocalSharedTransitionScope.current
val visibilityScope  = LocalNavAnimatedVisibilityScope.current

if (sharedTransition != null && visibilityScope != null) {
    with(sharedTransition) {
        Avatar(
            modifier = Modifier.sharedElement(
                state = rememberSharedContentState(key = "avatar-${conv.id}"),
                animatedVisibilityScope = visibilityScope,
            )
        )
    }
}
```

```kotlin
// In ChatAppBar (destination)
with(LocalSharedTransitionScope.current!!) {
    Avatar(
        modifier = Modifier.sharedElement(
            state = rememberSharedContentState(key = "avatar-${convId}"),
            animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current!!,
        )
    )
}
```

**Limitation (verified):** Shared element transitions do not work inside `Dialog` or
`ModalBottomSheet`. Plan around this — do not build flows where a shared element
needs to fly into a bottom sheet.

### Typing Indicator Animation

```kotlin
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue  = -6f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(400, delayMillis = index * 120,
                                       easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .size(6.dp)
                    .background(KotoTheme.colors.onSurfaceLow, CircleShape)
            )
        }
    }
}
```

---

## Performance Architecture

### Stability Discipline

**Rule:** Every data class that is a composable parameter must be stable.

| Type | Status | Action |
|------|--------|--------|
| `data class` with only `val` primitives | Stable (inferred) | No annotation needed |
| `data class` with only `val` Kotlin types | Stable (inferred) | No annotation needed |
| `data class` with `List<T>` field | UNSTABLE | Use `ImmutableList<T>` |
| Class from external module (e.g. Room Entity) | UNSTABLE | Never pass to composable; use UI model |
| ViewModel `StateFlow` value | Stable (snapshot-backed) | Fine as-is |

The existing `MessageUi`, `ConversationUi` domain UI models must only contain primitive
types and immutable collections.

```kotlin
// android/domain/model/MessageUi.kt
@Immutable
data class MessageUi(
    val id          : String,
    val text        : String,
    val senderId    : String,
    val sentAt      : Long,
    val isMine      : Boolean,
    val status      : MessageStatus,  // enum — stable
)
```

### Compiler Reports

Enable in `android/build.gradle.kts`:

```kotlin
composeCompiler {
    reportsDestination  = layout.buildDirectory.dir("compose_compiler")
    metricsDestination  = layout.buildDirectory.dir("compose_compiler")
}
```

Run `./gradlew :android:assembleRelease` then inspect
`android/build/compose_compiler/android-composables.txt`.
Look for `restartable skippable` (ideal) vs `restartable` alone (will always recompose).

### LazyColumn Performance Checklist

```
[x] items { key = { it.id } }          — prevents recomposition on reorder
[x] items { contentType = { ... } }    — enables layout node recycling  
[x] ImmutableList<T> as parameter      — stable list reference
[x] animateItem() on each item         — built-in insert/remove animation
[x] derivedStateOf for scroll state    — show/hide FAB without per-scroll recomposition
[x] Lambda-form offset modifier        — defer state read to layout phase
[x] remember {} for expensive calcs    — cache sorted/filtered lists
[x] Set default size on async images   — prevents 0-pixel items composing all at once
[x] Never nest same-direction scrolls  — no LazyColumn inside Column(verticalScroll)
```

### contentType for Composition Recycling

Compose reuses compositions only between items of the **same type**. Without `contentType`,
a date separator and a message bubble share the same composition pool, causing inefficient
recomposition when types differ:

```kotlin
LazyColumn {
    items(
        items = messages,
        key = { it.id },
        contentType = { msg ->
            when (msg) {
                is MessageUi.Text    -> "text"
                is MessageUi.Image   -> "image"
                is MessageUi.System  -> "system"
                is DateSeparator     -> "date"
            }
        }
    ) { item -> /* ... */ }
}
```

### derivedStateOf for Scroll-Dependent UI

Prevents recomposition on every scroll pixel — only triggers when the derived boolean changes:

```kotlin
val listState = rememberLazyListState()

val showScrollToBottom by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex > 3
    }
}

// showScrollToBottom changes only when crossing the threshold,
// NOT on every pixel scrolled
if (showScrollToBottom) {
    ScrollToBottomFab(onClick = { /* scroll to 0 */ })
}
```

### Deferred State Reads — Composition vs Layout vs Draw

```kotlin
// BAD — state read in composition phase, triggers full recomposition
@Composable fun Title(scroll: Int) {
    Column(Modifier.offset(y = scroll.dp)) { /* ... */ }
}

// GOOD — state read deferred to layout phase via lambda modifier
@Composable fun Title(scrollProvider: () -> Int) {
    Column(Modifier.offset { IntOffset(0, scrollProvider()) }) { /* ... */ }
}

// BEST for color — state read deferred to draw phase
val color by animateColorAsState(targetColor)
Box(Modifier.drawBehind { drawRect(color) })  // skips composition AND layout
```

### Paging Integration for Large Chat History

For chats with thousands of messages, integrate the Paging library:

```kotlin
@Composable
fun ChatMessageList(
    pager: Pager<Int, MessageEntity>,
    modifier: Modifier = Modifier,
) {
    val lazyPagingItems = pager.flow
        .map { pagingData -> pagingData.map { it.toUi() } }
        .collectAsLazyPagingItems()

    LazyColumn(
        reverseLayout = true,
        modifier = modifier,
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id },
            contentType = lazyPagingItems.itemContentType { it.contentType },
        ) { index ->
            val message = lazyPagingItems[index]
            if (message != null) {
                MessageBubble(message)
            } else {
                MessagePlaceholder()  // shimmer while loading
            }
        }
    }
}
```

### Baseline Profile

Generate for startup and the two critical user journeys:

```kotlin
class KotoBaselineProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect("run.koto") {
        startActivityAndWait()
    }

    @Test fun openChat() = rule.collect("run.koto") {
        startActivityAndWait()
        // navigate to conversation → chat
        device.findObject(By.res("conversation_list")).children[0].click()
        waitForAsyncCallback()
    }

    @Test fun scrollMessages() = rule.collect("run.koto") {
        startActivityAndWait()
        device.findObject(By.res("conversation_list")).children[0].click()
        waitForAsyncCallback()
        device.findObject(By.res("message_list"))
            .scroll(Direction.UP, 5f)
    }
}
```

Expected improvement: 10–20% startup time reduction, significantly smoother first-scroll
in the chat list.

---

## Asset Management Architecture

### Icon System

**Decision:** Custom `ImageVector` icons via a single Kotlin icon object, not an icon font.
**Rationale:** Icon fonts require runtime glyph rendering, cannot be easily animated,
and have larger APK overhead when only a subset is used. Custom SVGs compiled to
`ImageVector` are AOT-compiled, tree-shaken by R8, and can be animated via `Animatable`.

```kotlin
object KotoIcons {
    val Send       : ImageVector = ...  // compiled from SVG via IconPack plugin
    val Attachment : ImageVector = ...
    val Lock       : ImageVector = ...
    val Check      : ImageVector = ...
    val DoubleCheck: ImageVector = ...
}
```

Use `compose-icons` Gradle plugin or Android Studio SVG import for compilation.
Store source SVGs in `android/src/main/res/svg/` (not bundled in APK).

### Image Caching (Coil 3)

```kotlin
// AppModule.kt — singleton ImageLoader
@Provides @Singleton
fun provideImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(0.20)     // 20% of available memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100 * 1024 * 1024)  // 100 MB
                .build()
        }
        .build()
```

Inject into all `AsyncImage` calls via `LocalImageLoader` CompositionLocal or
a SingletonImageLoader (Coil 3 singleton pattern).

### Lottie / Animated Stickers

```kotlin
// Pre-cache sticker specs on conversation open
val composition by rememberLottieComposition(
    spec = LottieCompositionSpec.Asset("stickers/thumbsup.json")
)
val progress by animateLottieCompositionAsState(
    composition = composition,
    iterations  = LottieConstants.IterateForever,
)
LottieAnimation(composition, { progress })
```

For sticker packs: download JSON to internal storage, load from file path.
Do not embed stickers in APK — lazy download per pack.

---

## Component Hierarchy (Dependency Graph)

```
KotoTheme
    ↓
KotoColors / KotoTypography / KotoSpacing / KotoShapes / KotoMotion
    ↓
Atoms: Avatar, Badge, KotoButton, KotoTextField, StatusDot, Shimmer
    ↓
Molecules: MessageBubble, AvatarWithBadge, InputBar, ConversationRow, TypingIndicator
    ↓
Organisms: ChatMessageList, ConversationList, KotoNavigationBar, KotoBottomSheet
    ↓
Screens: ChatScreen, ConversationsScreen, OnboardingScreen, SettingsScreen, LockScreen
    ↑
ViewModels: ChatViewModel, ConversationsViewModel (read from Repository, emit via StateFlow)
    ↑
Repositories: ChatRepository, UserRepository (existing — do not break)
```

**Navigation** wraps Screens in `KotoNavGraph`, which is a peer of the screen layer.
**SharedTransitionLayout** wraps `NavHost` to provide shared element scope.

---

## Build Order Implications

| Phase | What to Build | Why This Order |
|-------|---------------|----------------|
| 1 | Token system (`KotoColors`, `KotoTypography`, `KotoSpacing`, `KotoShapes`, `KotoMotion`) | Everything else depends on tokens. Tokens have zero dependencies. |
| 2 | `KotoTheme` provider + `animateKotoColors` | Theme must exist before any component can reference `KotoTheme.*`. |
| 3 | Atoms (Avatar, Badge, KotoButton, etc.) | Stateless, fast to build, unit-testable in isolation via Compose preview. |
| 4 | Molecules (MessageBubble, InputBar, etc.) | Depend on atoms. Still stateless — easy screenshot tests. |
| 5 | Organisms (ChatMessageList, KotoNavigationBar, etc.) | Integrate domain UI models. Require stable `ImmutableList` types. |
| 6 | Navigation architecture (`KotoNavGraph` + `SharedTransitionLayout`) | Must be built before screens are wired together. |
| 7 | Screens (refactor existing to consume tokens + new components) | Connect ViewModels to organisms. Break no existing ViewModel logic. |
| 8 | Animations + shared element transitions | Added on top of built screens. Avoids blocking screen delivery. |
| 9 | Baseline Profile generation | Last: requires complete app flows for meaningful profiling. |

---

## Adaptive Layout Strategy

```kotlin
@Composable
fun KotoAdaptiveLayout(navController: NavHostController) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    NavigationSuiteScaffold(
        navigationSuiteItems = { /* nav items */ },
        layoutType = when {
            windowSizeClass.windowWidthSizeClass >= WindowWidthSizeClass.MEDIUM ->
                NavigationSuiteType.NavigationRail
            else ->
                NavigationSuiteType.NavigationBar
        }
    ) {
        KotoNavGraph(navController = navController)
    }
}
```

Use `ListDetailPaneScaffold` for the conversations list on tablets: conversation list
in the primary pane, active chat in the detail pane, no navigation between screens.

---

## Data Flow for UI State

```
Room DB (MessageEntity) ──toUi()──> MessageUi
                                        ↓
                              ChatViewModel.state: StateFlow<ChatState>
                              ChatState { messages: ImmutableList<MessageUi> }
                                        ↓
                              ChatScreen collects via collectAsState()
                                        ↓
                              ChatMessageList(messages = state.messages)
                                        ↓
                              MessageBubble per item (keyed, stable)
```

**Rule:** Room entities never enter the composition tree. They are mapped to `@Immutable`
UI models at the repository boundary. ViewModels hold `ImmutableList<MessageUi>` in state,
not `List<MessageEntity>` or `List<MessageDto>`.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Hardcoded Visual Values in Components

**What goes wrong:** `Color(0xFF7B61FF)` in `MessageBubble.kt`
**Consequence:** Theme changes require grep-and-replace across entire codebase
**Instead:** `KotoTheme.colors.primary`

### Anti-Pattern 2: Passing ViewModel to Child Composables

**What goes wrong:** `MessageBubble(viewModel = chatViewModel)`
**Consequence:** Untestable, unpreviews, tightly coupled
**Instead:** Pass only the data the component needs + lambdas for events

### Anti-Pattern 3: Standard `List<T>` as Composable Parameter

**What goes wrong:** `fun ChatMessageList(messages: List<MessageUi>)` — unstable, always recomposes
**Consequence:** Every new message causes full list recomposition
**Instead:** `messages: ImmutableList<MessageUi>` from `kotlinx.collections.immutable`

### Anti-Pattern 4: Reading Scroll State in Composition Phase

**What goes wrong:** `val offset = scrollState.value.dp` used directly in `Modifier.offset(offset)`
**Consequence:** Every scroll pixel triggers a full recomposition of the composable
**Instead:** `Modifier.offset { IntOffset(0, scrollState.value) }` — deferred to layout phase

### Anti-Pattern 5: Shared Element into ModalBottomSheet

**What goes wrong:** Trying to animate a shared element (avatar) into a bottom sheet
**Consequence:** Compose shared elements do not support Dialog/ModalBottomSheet scope — visual jump
**Instead:** Use a `AnimatedContent` or `AnimatedVisibility` custom overlay instead of `ModalBottomSheet` for this transition

### Anti-Pattern 6: Per-Component Animation Constants

**What goes wrong:** `tween(durationMillis = 250)` inline in `MessageBubble.kt`
**Consequence:** Animation feel is inconsistent across the app, impossible to tune globally
**Instead:** `KotoTheme.motion.tweenMedium` or `KotoMotionSpec.itemAppear`

---

## Sources

- Android Developers — Custom design systems in Compose: https://developer.android.com/develop/ui/compose/designsystems/custom
- Android Developers — Compose animation introduction: https://developer.android.com/develop/ui/compose/animation/introduction
- Android Developers — Compose animation customization: https://developer.android.com/develop/ui/compose/animation/customize
- Android Developers — Shared element transitions: https://developer.android.com/develop/ui/compose/animation/shared-elements
- Android Developers — Navigation Compose: https://developer.android.com/develop/ui/compose/navigation
- Android Developers — Navigation 3 (prerelease): https://developer.android.com/guide/navigation/navigation-3
- Android Developers — Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android Developers — Stability and recomposition: https://developer.android.com/develop/ui/compose/performance/stability
- Android Developers — Lists and grids (LazyColumn): https://developer.android.com/develop/ui/compose/lists
- Android Developers — Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- Android Developers — Adaptive layouts: https://developer.android.com/develop/ui/compose/layouts/adaptive
- Navigation 3 recipes: https://github.com/android/nav3-recipes
