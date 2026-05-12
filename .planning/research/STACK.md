# Stack Research: Premium Messenger UI on Android Compose

> Last updated: 2026-04-05
> Scope: UI libraries, tooling, and patterns for building a high-end messenger app with Jetpack Compose.

---

## 1. Animation Libraries

### Built-in Compose Animation APIs

Compose ships a layered animation system that covers 95% of messenger UI needs without any third-party code.

**High-level (declarative):**
- `animateAsState` family (`animateDpAsState`, `animateColorAsState`, `animateFloatAsState`) -- one-liner state-driven animations. Ideal for bubble selection highlights, read receipt color transitions, typing indicator dot pulsing.
- `AnimatedVisibility` -- enter/exit transitions with built-in `fadeIn`, `slideInVertically`, `expandIn`, etc. Perfect for showing/hiding chat bars, floating action buttons, notification banners.
- `AnimatedContent` -- animates between two different composables keyed by state. Use for switching between voice-record UI and text-input UI, or toggling between camera and gallery.
- `Crossfade` -- simple crossfade between composables. Good for avatar placeholder-to-loaded transitions.

**Mid-level (spring physics and specs):**
- `spring()` with custom `stiffness` and `dampingRatio` -- the secret to making UI feel alive. Recommended defaults for messenger:
  - Message bubble appear: `spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)`
  - Swipe-to-reply snap: `spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)`
  - FAB bounce: `spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh)`
- `tween()` for linear/eased animations where physics feel wrong (progress bars, timers).
- `keyframes {}` for multi-step choreographed animations (onboarding sequences).

**Low-level (imperative):**
- `Animatable` -- full control over animation lifecycle. Essential for gesture-driven animations like swipe-to-reply, drag-to-reorder, pull-to-refresh. Can be driven by `snapTo()` during gesture and `animateTo()` on release.
- `Transition` and `updateTransition` -- orchestrate multiple animations that share lifecycle. Use for complex state machines (e.g., message status: sending -> sent -> delivered -> read, each with coordinated icon + color change).
- `InfiniteTransition` -- for perpetual animations like typing indicator dots, recording pulse, live location sharing pulse.

### Shared Element Transitions (Compose 1.7+)

`SharedTransitionLayout` and `SharedTransitionScope` landed in Compose 1.7. This enables:
- Tapping a chat avatar in the list and having it animate smoothly into the chat header.
- Tapping an image thumbnail in chat and expanding it to fullscreen viewer.
- Profile picture transitions between conversation list and contact detail.

Usage pattern:
```kotlin
SharedTransitionLayout {
    // source
    Image(
        modifier = Modifier.sharedElement(
            rememberSharedContentState(key = "avatar-$userId"),
            animatedVisibilityScope = this@AnimatedContent
        )
    )
}
```

This is a game-changer for perceived polish. Previously required hacky workarounds or Accompanist Navigation Animation.

### Lottie Compose

**Dependency:** `com.airbnb.android:lottie-compose:6.4.0`

Use cases in a messenger:
- Animated stickers (Lottie JSON or converted TGS)
- Emoji reactions with particle effects
- Empty state illustrations (no messages, no internet)
- Onboarding animations
- Sending/delivered/read status animations
- Voice message waveform playback animation

Lottie is mature, well-maintained, and the Compose wrapper is first-class. The JSON format is much smaller than GIF/APNG and renders at native quality.

```kotlin
val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sending))
LottieAnimation(
    composition = composition,
    iterations = LottieConstants.IterateForever,
    modifier = Modifier.size(24.dp)
)
```

### Recommendation

Use built-in Compose animation APIs for all standard transitions and micro-interactions. Add Lottie only for complex, designer-authored animations (stickers, onboarding, elaborate status indicators). Do not introduce any other animation library -- the built-in system is comprehensive and performant.

---

## 2. Custom UI Components

### Accompanist (google/accompanist)

**Status as of 2025:** Most useful APIs have graduated into Compose or AndroidX proper.

- **System UI Controller** -- graduated to `enableEdgeToEdge()` in Activity. No longer needed.
- **Permissions** -- graduated to `rememberLauncherForActivityResult` + `accompanist-permissions` still useful for multi-permission flows, but can be replaced.
- **Pager** -- graduated to `HorizontalPager` / `VerticalPager` in Compose Foundation.
- **Navigation Animation** -- graduated to Compose Navigation 2.7+.
- **Placeholder (shimmer)** -- still useful, but consider building custom shimmer for brand consistency.

**Verdict:** Do not add Accompanist as a dependency. Everything needed has graduated or is better built custom.

### Material3 as a Base (Heavily Customized)

Use `androidx.compose.material3` as the component foundation but override aggressively:
- Custom `TextField` for message input (Material3 TextField has too much Chrome for a messenger).
- Custom `TopAppBar` with blur backdrop and gradient support.
- Custom `NavigationBar` with animated indicators.
- Custom `Card` shapes for message bubbles (different corner radii for sent vs. received, tail shapes).

Do NOT use Material3 components as-is for user-facing chat UI. They look generic. Use them for settings screens, dialogs, and other secondary surfaces where brand differentiation matters less.

### Custom Bottom Sheets

`BottomSheetScaffold` and `ModalBottomSheet` from Material3 are decent starting points but need customization for:
- Attachment picker (photos, files, location, contact)
- Message reactions panel
- User profile preview
- Forwarding/sharing sheet

Key customization points:
- Custom drag handle appearance
- Snap points (partially expanded, fully expanded)
- Nested scrolling within sheet content
- Blur backdrop behind sheet
- Edge-to-edge content behind sheet

Consider using `AnchoredDraggable` (Compose Foundation 1.6+) for fully custom sheet behavior when Material3 sheets are too restrictive.

### Pull-to-Refresh

Compose Material3 1.4+ includes `pullToRefresh` modifier and `PullToRefreshBox`. Customize the indicator to match Koto branding -- the default Material indicator is recognizable and generic.

### Blur and Glassmorphism

- `Modifier.blur(radius)` -- works on API 31+ (Android 12+). For a 2025/2026 app with minSdk 26, need fallback.
- `RenderEffect.createBlurEffect()` -- available via `Modifier.graphicsLayer { renderEffect = ... }`. Same API 31+ limitation.
- Fallback for older APIs: pre-blurred bitmap via RenderScript or AGSL (Android Graphics Shading Language, API 33+).
- Practical approach: use blur where available, fall back to semi-transparent dark overlay on older devices. Most flagship devices are on Android 12+ by now.

Glassmorphism recipe:
```kotlin
Modifier
    .clip(RoundedCornerShape(16.dp))
    .blur(20.dp)
    .background(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp)
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.White.copy(alpha = 0.1f)
            )
        ),
        shape = RoundedCornerShape(16.dp)
    )
```

### Recommendation

Build all chat-facing components from scratch using Compose primitives (`Box`, `Row`, `Column`, `Canvas`, `Layout`). Use Material3 components only for utilitarian screens (settings, search, dialogs). This is how Telegram, Signal, and WhatsApp achieve their distinctive feel -- none of them use stock Material components for the core chat experience.

---

## 3. Performance

### Baseline Profiles

**Dependency:** `androidx.benchmark:benchmark-macro-junit4:1.3.x`

Baseline Profiles pre-compile hot code paths using ART profiles. Impact on a Compose app:
- 30-50% faster cold start (measured across industry)
- Smoother first-frame rendering of chat lists
- Faster initial scroll performance

Setup requires a `benchmark` module with `BaselineProfileGenerator` test that exercises critical user journeys (open app -> chat list -> open conversation -> scroll -> send message).

Compose libraries ship their own baseline profiles (since Compose 1.6), but app-specific profiles add the app's own hot paths.

### Compose Compiler Reports

Add to `build.gradle.kts`:
```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

This generates reports showing:
- Which composables are skippable vs. restartable
- Which classes are stable vs. unstable (unstable = recomposes every time)
- Groups and their stability

**Critical for messenger performance:** A chat list with 1000+ conversations and a message list with hundreds of messages will lag badly if composables are not skippable. Mark data classes as `@Stable` or `@Immutable`, use `ImmutableList` from `kotlinx.collections.immutable`, and review compiler reports regularly.

### Strong Skipping Mode

Compose Compiler 2.0+ (K2 compiler) defaults to strong skipping mode. This makes more composables automatically skippable even with unstable parameters. Still, explicit stability annotations are best practice for hot-path composables like message items.

### R8 Full Mode

Enable in `gradle.properties`:
```
android.enableR8.fullMode=true
```

Combined with proper ProGuard/R8 rules, this aggressively optimizes and shrinks. Compose apps benefit significantly because R8 can inline and remove unused composable code.

### Lazy Layout Best Practices

For chat message lists (`LazyColumn`):
- Always provide stable `key` for each item (message ID)
- Use `contentType` to distinguish message types (text, image, video, system) -- enables view recycling
- Avoid heavy computation in item composables -- precompute formatted timestamps, measured text, etc.
- Use `LazyListState.requestScrollToItem()` for programmatic scroll (new messages)
- Consider `reverseLayout = true` for chat (newest at bottom)
- Use `Modifier.animateItem()` (Compose 1.7+) for insert/delete animations

### Layout Inspector

Android Studio's Layout Inspector shows:
- Recomposition counts per composable (highlights excessive recomposition in red)
- Component tree
- Modifier chains

Use during development to catch recomposition issues early.

### Recommendation

Enable Compose compiler reports from day one. Treat unexpected recompositions as bugs. Add baseline profiles before the first public release. Use `kotlinx.collections.immutable` for all list data flowing into composables.

---

## 4. Color and Theming

### Custom KotoTheme

Do NOT rely solely on `MaterialTheme`. Create a `KotoTheme` that wraps `MaterialTheme` and adds messenger-specific design tokens via `CompositionLocal`:

```kotlin
data class KotoColors(
    val sentBubble: Brush,
    val receivedBubble: Color,
    val sentText: Color,
    val receivedText: Color,
    val timestamp: Color,
    val linkColor: Color,
    val onlineIndicator: Color,
    val typingIndicator: Color,
    // ... etc
)

val LocalKotoColors = staticCompositionLocalOf { lightKotoColors }

@Composable
fun KotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkKotoColors else lightKotoColors
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme else lightColorScheme,
        typography = KotoTypography
    ) {
        CompositionLocalProvider(
            LocalKotoColors provides colors
        ) {
            content()
        }
    }
}
```

### Recommended Koto Color Palette

**Primary: #6C5CE7 (Electric Violet)**
- Distinctive -- not used by WhatsApp (green), Telegram (blue), Signal (blue), Messenger (blue/purple gradient). Koto's violet sits in a unique space.
- Works in both light and dark themes.
- Excellent contrast ratios with white text.

**Secondary/Accent: #00D2FF (Cyan)**
- High-energy accent for interactive elements, links, online indicators.
- Complements violet without clashing.

**Surface Colors:**
- Light mode surface: #F8F9FE (very slight blue-violet tint, not pure white)
- Dark mode surface: #0F0F14 (deep blue-black, not pure black -- easier on eyes for AMOLED)
- Light mode background: #FFFFFF
- Dark mode background: #000000 (true black for AMOLED power savings)

**Chat Bubble Colors:**
- Sent bubble: `Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF8B7CF7)))` -- subtle gradient, signature Koto look.
- Received bubble (light): #F0F0F5 (soft gray with violet undertone)
- Received bubble (dark): #1C1C24 (elevated dark surface)
- Sent text: #FFFFFF
- Received text: adaptive to theme

**Status Colors:**
- Online: #2ED573 (green)
- Away/Idle: #FFA502 (amber)
- Error/Failed: #FF4757 (red)
- Unread badge: #FF6B6B (soft red, not aggressive)

### Dynamic Color (Material You)

Support Material You dynamic color as an opt-in theme option. When enabled, extract the primary color from the user's wallpaper using `DynamicColors.applyToActivitiesIfAvailable()`. Keep the sent bubble gradient as the Koto signature even in dynamic color mode -- only adapt secondary surfaces.

### Gradient Utilities

Built into Compose:
- `Brush.linearGradient()` -- for sent bubbles, headers, accent surfaces
- `Brush.radialGradient()` -- for glow effects, focus indicators
- `Brush.sweepGradient()` -- for circular progress (voice message recording timer)
- `Brush.verticalGradient()` / `Brush.horizontalGradient()` -- convenience shortcuts

These are all you need. No third-party gradient library required.

### Recommendation

Build `KotoTheme` with `CompositionLocal`-based custom tokens from day one. Use the violet/cyan palette as the default. Support Material You as an optional theme. Always provide both light and dark variants.

---

## 5. Typography

### Recommended Font: Inter

**Why Inter:**
- Designed specifically for screen readability at small sizes -- critical for chat text at 15sp.
- Extensive weight range (100-900) allows fine typographic hierarchy.
- Excellent language support (Latin, Cyrillic, Greek, Vietnamese, etc.).
- Free via Google Fonts, open source (SIL Open Font License).
- Used by major tech products (GitHub, Figma, Linear) -- signals quality without being generic.
- Variable font support means a single font file covers all weights (~300KB).

**Alternatives considered:**
- **Plus Jakarta Sans** -- more personality with its geometric forms. Good if Koto wants a warmer, friendlier feel. Slightly less readable at very small sizes.
- **Satoshi** -- geometric modern typeface with distinctive character. Premium feel but less language coverage.
- **SF Pro** (iOS) / **Roboto** (Android) -- system defaults. Using them means Koto looks like a system app, which is fine but not distinctive.
- **Manrope** -- geometric sans with good readability. Slightly quirky lowercase 'a' adds character.

### Typography Scale for Messenger

```kotlin
val KotoTypography = Typography(
    // Screen titles (chat name in toolbar, settings sections)
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    // Section headers (date separators, group headers)
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // Chat bubble text
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // Secondary text (last message preview in chat list)
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    // Timestamps, metadata
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    ),
    // Buttons, action labels
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
```

### Font Loading Strategy

Two options:
1. **Bundle TTF/OTF** -- adds ~200-300KB to APK. Guarantees immediate availability. Recommended for Inter since it is a core brand element.
2. **Downloadable Fonts via Google Fonts provider** -- zero APK cost but requires network. Falls back to system font if unavailable. Not recommended for the primary font.

**Recommendation:** Bundle Inter Variable (single file, ~300KB). This guarantees consistent typography from first frame.

---

## 6. Image and Media

### Coil 3

**Dependency:** `io.coil-kt.coil3:coil-compose:3.0.4`

Why Coil 3 over alternatives:
- **Compose-first API** -- `AsyncImage` composable with full Compose integration.
- **Coroutine-based** -- cancels loads when composable leaves composition. No lifecycle leaks.
- **Memory + disk cache** out of the box.
- **Kotlin Multiplatform** ready (Coil 3 is KMP, useful if Koto considers iOS Compose).
- **Smaller than Glide** (~1500 methods vs ~4000).
- **Transformations** -- circle crop, rounded corners, blur, all composable-friendly.

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(message.imageUrl)
        .crossfade(300)
        .placeholder(blurHashDrawable)
        .error(R.drawable.image_error)
        .build(),
    contentDescription = null,
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp)),
    contentScale = ContentScale.Crop
)
```

### BlurHash for Placeholder Thumbnails

BlurHash encodes an image as a short string (~20-30 chars) that decodes into a blurred placeholder. The server generates the hash when an image is uploaded, and the client renders it instantly while the full image loads.

**Library:** `io.github.nichenqin:blur-hash-compose:1.0.0` or implement the algorithm directly (it is ~100 lines of Kotlin).

Effect: Instead of a gray/empty rectangle while an image loads, the user sees a colorful blurred preview that resolves into the actual image. This is what Apple Messages and Instagram use.

### Telephoto (saket/telephoto)

**Dependency:** `me.saket.telephoto:zoomable-coil3:0.14.x`

Provides:
- Pinch-to-zoom on images with proper gesture handling
- Double-tap to zoom
- Fling-to-dismiss (swipe down to close image viewer)
- Sub-sampling for large images (does not load full resolution into memory)
- Integrates directly with Coil 3

This is essential for the image viewer screen. Building pinch-to-zoom with proper edge detection, fling physics, and sub-sampling from scratch would take weeks.

### Video

- **ExoPlayer / Media3** (`androidx.media3:media3-exoplayer:1.5.x`) -- for video message playback.
- **Media3 UI** (`androidx.media3:media3-ui-compose:1.5.x`) -- Compose wrappers for player views. Still maturing; may need `AndroidView` interop for now.
- Inline video playback in chat with auto-play on visible (using `LazyListState` visibility detection).

### Voice Messages

- **Waveform visualization:** Use `Canvas` composable to draw audio waveform from amplitude data. Pre-compute amplitudes on send, store with message.
- **Playback:** Media3 or Android `MediaPlayer` for simple audio playback.
- **Recording:** `MediaRecorder` or `AudioRecord` for raw PCM (needed for waveform computation during recording).

### Recommendation

Coil 3 + BlurHash + Telephoto is the optimal image stack. For video, Media3 with Compose interop. Build voice message waveform rendering with `Canvas` composable.

---

## 7. Haptics

### Why Haptics Matter

Premium feel comes from multi-sensory feedback. The difference between a "cheap" and "premium" app often comes down to haptic feedback at the right moments. Apple has known this for years (Taptic Engine); Android caught up with Android 11+.

### Compose Haptic API (Basic)

```kotlin
val haptic = LocalHapticFeedback.current
haptic.performHapticFeedback(HapticFeedbackType.LongPress)
haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
```

Limited to only a few types. Not sufficient for a premium messenger.

### View-Based Haptic API (Full Control)

```kotlin
val view = LocalView.current
view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) // subtle tick
view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)    // API 30+
view.performHapticFeedback(HapticFeedbackConstants.REJECT)     // API 30+
view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START) // API 30+
view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)   // API 30+
```

### Android 13+ Rich Haptics

`VibrationEffect.Composition` allows composing custom haptic patterns:
```kotlin
val vibrator = context.getSystemService(Vibrator::class.java)
vibrator.vibrate(
    VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 50)
        .compose()
)
```

Primitives available: CLICK, TICK, LOW_TICK, SLOW_RISE, QUICK_RISE, QUICK_FALL, SPIN, THUD.

### Haptic Moments in a Messenger

| Action | Haptic Type | Notes |
|--------|------------|-------|
| Message sent | CONFIRM or custom rise+click | Satisfying confirmation |
| Long press message | LONG_PRESS | Standard selection feel |
| Swipe-to-reply threshold reached | CONTEXT_CLICK | Snap feedback at threshold |
| Pull-to-refresh trigger | CONFIRM | Indicates refresh started |
| Reaction picker open | CONTEXT_CLICK | Accompanies popup animation |
| Reaction selected | CLOCK_TICK | Light confirmation |
| Delete message | REJECT | Warning feel |
| Recording voice message | GESTURE_START | Mark recording start |
| Stop recording | GESTURE_END | Mark recording end |
| Switch between tabs | CLOCK_TICK | Subtle navigation feedback |

### Recommendation

Use `View.performHapticFeedback()` for broad compatibility. Add `VibrationEffect.Composition` for Android 13+ devices as an enhancement. Always provide a user setting to disable haptics. Create a `KotoHaptics` utility object that centralizes all haptic patterns.

---

## 8. Key Dependencies Summary

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2024.12.01 | UI framework version alignment |
| Compose Material3 | 1.3.x | Base design system (customized) |
| Compose Animation | 1.7.x | Spring physics, shared element transitions |
| Compose Foundation | 1.7.x | Lazy layouts, gestures, pager |
| Lottie Compose | 6.4.0 | Complex animations, animated stickers |
| Coil 3 | 3.0.4 | Image loading, caching, transformations |
| Telephoto | 0.14.x | Zoomable image viewer with gestures |
| Navigation Compose | 2.8.x | Type-safe navigation with shared elements |
| Hilt | 2.52 | Dependency injection |
| Hilt Navigation Compose | 1.2.0 | ViewModel injection in navigation |
| Room | 2.6.x | Local message and conversation database |
| DataStore | 1.1.x | Preferences and settings storage |
| Media3 ExoPlayer | 1.5.x | Video and audio playback |
| kotlinx-collections-immutable | 0.3.8 | Stable collections for Compose performance |
| kotlinx-serialization | 1.7.x | JSON and Protobuf serialization |
| Ktor Client | 3.0.x | HTTP networking (or Retrofit if preferred) |

### Version Pinning Strategy

Use the Compose BOM to align all Compose library versions. Pin other libraries explicitly. Update quarterly, not chasing every patch release. Always test on:
- Pixel 6 or equivalent (mid-range baseline)
- Samsung Galaxy S-series (custom skin, different behaviors)
- Low-end device with 3GB RAM (stress test)

---

## 9. What NOT to Use

### Deprecated or Graduated Libraries
- **Accompanist** -- all critical APIs graduated to Compose itself. Adding it creates confusion about which API to use.
- **Accompanist Navigation Animation** -- `AnimatedNavHost` is in Compose Navigation 2.7+ natively.
- **Accompanist SystemUI Controller** -- use `enableEdgeToEdge()` from Activity 1.9+.
- **Accompanist Pager** -- use `HorizontalPager` / `VerticalPager` from Foundation.

### Wrong Tool for the Job
- **Glide / Picasso** -- XML-era image loaders. Coil is Compose-native, coroutine-based, and KMP-ready. No reason to use the older libraries.
- **XML View Interop for basic components** -- every `AndroidView` in Compose is a performance cliff. Use pure Compose for everything possible.
- **Third-party theme libraries** -- they add indirection and fight with Material3. Build `KotoTheme` directly.
- **Jetpack Compose UI libraries like Composables-core, ZzzCompose, etc.** -- small community libraries with uncertain maintenance. Build what you need.

### Architecture Anti-Patterns
- **Navigation with string routes** -- use type-safe navigation (Compose Navigation 2.8+ with `@Serializable` routes).
- **`mutableStateOf` in ViewModels for complex state** -- use `StateFlow` + `collectAsStateWithLifecycle()`. It is lifecycle-aware and testable.
- **Global singletons for state** -- use Hilt scoped dependencies.
- **Storing UI state in Room** -- Room is for persistent data. UI state belongs in `SavedStateHandle` or ViewModel.

---

## 10. Architecture Notes for Compose Messenger

### Recommended Patterns

- **Unidirectional Data Flow (UDF):** ViewModel exposes `StateFlow<ScreenState>`, composables only emit events upward. No two-way data binding.
- **Immutable UI State:** All state classes are `data class` with `val` properties. Use `copy()` for updates.
- **Compose Stability:** Annotate state models with `@Immutable` or `@Stable`. Use `kotlinx.collections.immutable.ImmutableList` instead of `List` in state objects.
- **Navigation:** Single `NavHost` with type-safe routes. Deep link support for notification -> specific chat.
- **Theming:** `KotoTheme` wrapping `MaterialTheme` with custom `CompositionLocal` providers.
- **Preview:** Every screen composable should have `@Preview` variants for light/dark, empty/loaded/error states.

### Minimum SDK

**minSdk 26 (Android 8.0)** -- covers 97%+ of active devices as of 2025. Allows:
- Java 8+ desugaring
- Notification channels
- Autofill framework
- Picture-in-picture (for video calls)

### Target SDK

**targetSdk 35 (Android 15)** -- required for Play Store submission in 2025/2026. Brings:
- Edge-to-edge enforcement
- Predictive back gesture support
- Granular media permissions

---

## Summary

The Compose ecosystem in 2025/2026 is mature enough to build a premium messenger without heavy third-party dependencies. The key insight is that **most of the "premium feel" comes from proper use of built-in Compose animation APIs, thoughtful haptic feedback, and a distinctive custom theme** -- not from stacking libraries.

Core external dependencies are limited to:
1. **Lottie** for complex authored animations
2. **Coil 3** for image loading
3. **Telephoto** for image zoom
4. **Media3** for audio/video

Everything else -- theming, animations, gestures, components -- should be built with Compose primitives for maximum control and minimum dependency risk.
