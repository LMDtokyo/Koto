# Pitfalls Research: Compose Messenger UI

> Comprehensive catalog of common pitfalls when building premium messenger UI
> with Jetpack Compose. Each pitfall is rated CRITICAL / MAJOR / MINOR based on
> user-visible impact and difficulty of late-stage remediation.

---

## 1. Compose Performance Pitfalls

### P1: Unstable List Parameters in LazyColumn (CRITICAL)

- **Problem**: Passing `List<Message>` (a mutable Kotlin stdlib type) as a
  composable parameter makes Compose treat it as *unstable*. The compiler cannot
  prove the list has not changed, so every recomposition of the parent triggers a
  full recomposition of every visible item -- even if the data is identical.
- **Warning signs**: Layout Inspector's recomposition counter increments on every
  frame while the chat is idle. Profiler shows `equals()` never called because
  the runtime skips stability checks entirely for unstable types.
- **Prevention**:
  1. Mark data classes with `@Immutable` or `@Stable`.
  2. Use `kotlinx.collections.immutable.ImmutableList` for collection parameters.
  3. Enable the Compose compiler stability report (`-P plugin:...stabilityConfigurationPath`) to
     catch regressions in CI.
- **Code example**:
  ```kotlin
  @Immutable
  data class MessageUi(val id: String, val text: String, val timestamp: Long)

  @Composable
  fun ChatMessages(messages: ImmutableList<MessageUi>) {
      LazyColumn {
          items(messages, key = { it.id }) { msg ->
              MessageBubble(msg)
          }
      }
  }
  ```

### P2: Lambda Captures Causing Recomposition (CRITICAL)

- **Problem**: Inline lambdas like `onClick = { viewModel.doSomething(item.id) }`
  allocate a new lambda instance on every recomposition. Compose sees a new
  reference and recomposes the child even though behavior is identical.
- **Warning signs**: Layout Inspector shows recomposition counts climbing on
  items that have not changed visually.
- **Prevention**:
  1. Wrap with `remember`: `val onClick = remember(item.id) { { viewModel.doSomething(item.id) } }`
  2. Extract to a stable method reference when the receiver is stable.
  3. For item callbacks in lists, pass the id as a parameter to a single
     remembered lambda rather than capturing it in a closure.
- **Code example**:
  ```kotlin
  // BAD -- new lambda every recomposition
  MessageBubble(onClick = { viewModel.onMessageClick(msg.id) })

  // GOOD -- stable reference
  val onMessageClick = remember { viewModel::onMessageClick }
  MessageBubble(onClick = { onMessageClick(msg.id) })
  ```

### P3: Reading State in Wrong Scope (MAJOR)

- **Problem**: Accessing `.value` of a state object during composition
  (`Modifier.offset(x = scrollState.value.dp)`) forces recomposition on every
  pixel of scroll. This turns a layout-phase operation into a composition-phase
  operation, dropping frames badly.
- **Warning signs**: `recomposition` trace spans appear at 60 Hz in systrace
  during scroll.
- **Prevention**: Use the lambda-based overloads that defer reads to layout or
  draw phase:
  ```kotlin
  // BAD -- reads in composition
  Modifier.offset(x = scrollState.value.dp, y = 0.dp)

  // GOOD -- reads in layout phase only
  Modifier.offset { IntOffset(scrollState.value, 0) }
  ```
- **Same principle applies to**: `Modifier.graphicsLayer { }` (draw phase),
  `Modifier.drawBehind { }`, and `Canvas { }`.

### P4: Missing Keys in LazyColumn (MAJOR)

- **Problem**: Without explicit `key`, Compose uses positional index. When
  messages are inserted at the top (new incoming messages), every item shifts
  index, causing full recomposition of visible items. Animations (appear,
  disappear, reorder) break or show incorrect items.
- **Warning signs**: Item animations play on wrong rows after insert; shimmer
  placeholders restart after new message arrives.
- **Prevention**: Always provide a stable, unique key:
  ```kotlin
  items(messages, key = { it.id }) { msg -> ... }
  ```
  Also provide `contentType` for heterogeneous lists (bubbles vs. date headers)
  to enable ViewHolder-style recycling.

### P5: Heavy Computation in Composition (MAJOR)

- **Problem**: Date formatting (`SimpleDateFormat`), Markdown parsing, or
  relative-time computation (`"2 min ago"`) performed inside `@Composable`
  functions runs on every recomposition, including during scroll.
- **Warning signs**: Systrace shows `java.text.SimpleDateFormat.format` inside
  composition spans; janky scroll on mid-range devices.
- **Prevention**:
  1. Pre-compute display strings in the ViewModel or mapping layer.
  2. If computation must live in Compose, wrap in `remember(key)`.
  3. For time-relative strings, use a `LaunchedEffect` ticker that updates a
     `mutableStateOf` at 30-second intervals rather than recomputing per frame.

### P6: Unnecessary Recomposition from ViewModel StateFlow (MAJOR)

- **Problem**: A single `StateFlow<ChatScreenState>` with many fields causes
  recomposition of the entire screen when *any* field changes (e.g., typing
  indicator updates trigger message list recomposition).
- **Warning signs**: Typing indicator blink causes message list recomposition
  counts to increase.
- **Prevention**:
  1. Split state into independent `StateFlow` instances per UI section.
  2. Use `derivedStateOf` or `snapshotFlow` to create fine-grained observables.
  3. At minimum, use `distinctUntilChanged()` on mapped subsets.

### P7: Forgetting `derivedStateOf` for Computed UI State (MINOR)

- **Problem**: Computing a boolean like `val showScrollToBottom = listState.firstVisibleItemIndex > 5`
  inside composition reads the scroll position directly, causing recomposition
  every time the index changes -- even if the boolean result hasn't changed.
- **Prevention**: Wrap in `derivedStateOf`:
  ```kotlin
  val showScrollToBottom by remember {
      derivedStateOf { listState.firstVisibleItemIndex > 5 }
  }
  ```

---

## 2. Chat UI Pitfalls

### C1: Reversed LazyColumn Scroll Semantics (CRITICAL)

- **Problem**: `reverseLayout = true` inverts all scroll semantics.
  `scrollToItem(0)` scrolls to the *bottom* (newest message).
  `firstVisibleItemIndex == 0` means the user is at the bottom, not the top.
  New messages arriving while the user is scrolled up cause a jarring jump to
  bottom if you naively call `scrollToItem(0)`.
- **Warning signs**: Users report losing their scroll position when a new
  message arrives; "scroll to bottom" FAB appears at wrong times.
- **Prevention**:
  1. Only auto-scroll if `listState.firstVisibleItemIndex <= 1` (user is near
     bottom).
  2. Use `snapshotFlow { listState.firstVisibleItemIndex }` to track position.
  3. Show "New messages" pill instead of force-scrolling.
- **Additional trap**: `LazyColumn(reverseLayout = true)` combined with
  `Modifier.imePadding()` can double-shift content when the keyboard opens.
  Test extensively on real devices.

### C2: Keyboard Resize Jank (CRITICAL)

- **Problem**: The IME (soft keyboard) opening causes `WindowInsets.ime` to
  change, which triggers a layout pass. Without proper configuration, the
  content jumps instantly rather than animating with the keyboard.
- **Warning signs**: Chat input field teleports to final position; messages
  flash during keyboard open/close.
- **Prevention**:
  1. Set `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onCreate`.
  2. Use `Modifier.imePadding()` on the root container.
  3. Use `Modifier.consumeWindowInsets()` to prevent double-padding.
  4. Test on API 30+ (where `WindowInsetsAnimation` works) and API 29- (where
     fallback behavior differs).
  5. Consider `BringIntoViewRequester` for the text field to ensure it stays
     visible.

### C3: Image Loading Jank During Scroll (MAJOR)

- **Problem**: Loading images (avatars, media thumbnails) synchronously or
  without proper placeholders causes frame drops in LazyColumn. Coil/Glide
  decode on the main thread if misconfigured; items resize when images load,
  causing layout shifts.
- **Warning signs**: Blank white rectangles during fast scroll; visible content
  jump when images load.
- **Prevention**:
  1. Use fixed-size containers (`Modifier.size(48.dp)` for avatars).
  2. Enable memory cache: `ImageRequest.Builder.memoryCachePolicy(CachePolicy.ENABLED)`.
  3. Use BlurHash or solid-color placeholders matching the dominant color.
  4. For media messages, pass `aspectRatio` from the server to pre-size the
     container before the image loads.
  5. Use `SubcomposeAsyncImage` sparingly -- it measures twice and is slower.
     Prefer `AsyncImage` with fixed dimensions.

### C4: Message Grouping and Date Separator Edge Cases (MAJOR)

- **Problem**: Grouping messages by sender + time proximity seems simple but
  breaks on: system messages, date boundaries at midnight, deleted messages,
  edited messages that change timestamp, and rapid messages from different
  senders interleaved.
- **Warning signs**: Avatar appears/disappears unexpectedly; date separator
  shows between messages sent 1 second apart across midnight.
- **Prevention**:
  1. Model item types as a sealed interface:
     ```kotlin
     sealed interface ChatItem {
         data class Message(val msg: MessageUi, val showAvatar: Boolean, val showTail: Boolean) : ChatItem
         data class DateSeparator(val date: LocalDate) : ChatItem
         data class SystemEvent(val text: String) : ChatItem
     }
     ```
  2. Compute the flat list in the ViewModel, not in composition.
  3. Write unit tests for edge cases (midnight boundary, rapid sender switch,
     message deletion).

### C5: Emoji-Only Message Rendering (MINOR)

- **Problem**: Messages containing only 1-3 emoji should render at larger size
  (common in premium messengers). Detection logic must handle emoji ZWJ
  sequences, skin tone modifiers, flag sequences, and keycap sequences -- a
  naive regex on Unicode ranges will miss many.
- **Warning signs**: Some emoji render large, others don't; flag emoji counted
  as 2 characters.
- **Prevention**:
  1. Use `Character.isEmoji()` (API 34+) or a library like `emoji-java`.
  2. Count grapheme clusters, not codepoints.
  3. Define a maximum grapheme count (typically 3) for "emoji-only" mode.

### C6: Text Selection and Copy Behavior (MAJOR)

- **Problem**: Compose `Text` does not support text selection by default.
  `SelectionContainer` enables it but conflicts with long-press gestures for
  message context menus. Users expect to long-press a bubble for options *and*
  to select text -- these are mutually exclusive in default Compose.
- **Warning signs**: Users cannot copy message text; or long-press always
  selects text instead of showing context menu.
- **Prevention**:
  1. Use a short tap for context menu (bottom sheet) and long-press for
     selection, or vice versa -- pick one and be consistent.
  2. Add explicit "Copy" action in the context menu as the primary copy path.
  3. Consider `ClickableText` with custom gesture handling if you need both.

### C7: RTL and Bidirectional Text (MAJOR)

- **Problem**: Chat bubbles must handle mixed LTR/RTL text (e.g., English
  message with Arabic quote). Bubble alignment (sent = end, received = start)
  must also flip in RTL locales. Timestamp placement inside the bubble can
  collide with text in RTL.
- **Warning signs**: Bubbles appear on wrong side in Arabic locale; timestamps
  overlap text.
- **Prevention**:
  1. Use `Arrangement.Start`/`End` instead of `Left`/`Right`.
  2. Test with `ForceRTL` developer option enabled.
  3. Use `Modifier.layoutDirection` overrides only when absolutely necessary.

---

## 3. Animation Pitfalls

### A1: Spring Animation Cancellation Glitch (MAJOR)

- **Problem**: Starting a new `spring` animation on an `Animatable` while a
  previous spring hasn't settled can cause a visible snap or overshoot, because
  the new animation starts from the current velocity of the old one. If the old
  spring was overshooting in the opposite direction, the result is chaotic.
- **Warning signs**: Bubble appear animation jitters when messages arrive in
  rapid succession.
- **Prevention**:
  1. Use `Animatable.animateTo()` which automatically cancels the previous
     animation and uses current velocity as initial velocity.
  2. For message appear animations, use `AnimatedVisibility` with
     `MutableTransitionState` which handles interruption correctly.
  3. Avoid `snapTo()` + `animateTo()` patterns -- the snap creates a visual
     discontinuity.

### A2: Gesture vs Scroll Conflict (CRITICAL)

- **Problem**: Swipe-to-reply (horizontal gesture) on a message item conflicts
  with vertical LazyColumn scroll. Without proper gesture arbitration, the
  scroll steals the pointer or the swipe fires during vertical flings.
- **Warning signs**: Swiping horizontally triggers vertical scroll; or vertical
  scroll becomes sluggish because swipe detector consumes down events.
- **Prevention**:
  1. Implement velocity-based direction detection: accumulate pointer movement
     for ~10dp, then commit to horizontal or vertical.
  2. Use `Modifier.pointerInput` with `detectHorizontalDragGestures` and set a
     `touchSlop`-based threshold before consuming events.
  3. Use `Modifier.nestedScroll` to properly participate in the scroll system.
  4. Test with fast diagonal swipes -- these are the hardest to classify.
- **Code pattern**:
  ```kotlin
  Modifier.pointerInput(Unit) {
      detectHorizontalDragGestures(
          onDragStart = { },
          onHorizontalDrag = { change, dragAmount ->
              if (abs(totalDragX) > touchSlop) {
                  change.consume()
                  offsetX += dragAmount
              }
          }
      )
  }
  ```

### A3: Overdraw from Layered Animations (MAJOR)

- **Problem**: Premium messenger UI often stacks: blurred background + gradient
  overlay + message bubble shadow + bubble background + text. Each transparent
  layer multiplies GPU fill rate. Animated blur or gradient transitions push
  mid-range GPUs past budget.
- **Warning signs**: GPU overdraw visualization shows red (4x+) on chat screen;
  frame times spike during transition animations.
- **Prevention**:
  1. Minimize transparent layers; use opaque backgrounds wherever possible.
  2. Replace animated blur with pre-rendered BlurHash or static blur bitmaps.
  3. Use `Modifier.graphicsLayer { }` with `compositingStrategy =
     CompositingStrategy.Offscreen` only when needed (it disables hardware layer
     optimization).
  4. Profile on low-end devices (Snapdragon 4-series) -- they have 1/4 the GPU
     bandwidth of flagships.

### A4: Infinite Animation Memory Leaks (MAJOR)

- **Problem**: `rememberInfiniteTransition()` creates an animation that runs
  forever. If the composable leaves composition (scrolls off screen) the
  transition is disposed -- but if it's hoisted incorrectly (e.g., at screen
  level) it runs even when no consumer is visible, wasting CPU and battery.
- **Warning signs**: CPU usage stays elevated when chat is idle; battery drain
  reports from users.
- **Prevention**:
  1. Scope infinite transitions to the specific composable that displays them.
  2. Use `LaunchedEffect` with `isActive` checks for manual animations.
  3. For typing indicators, use `AnimatedVisibility` wrapping the infinite
     animation so it only runs when visible.

### A5: Shared Element Transition Limits (MAJOR)

- **Problem**: Compose shared element transitions (introduced in Navigation
  Compose 2.8+) do not work across `Dialog`, `ModalBottomSheet`, or `Popup`
  composables because these create separate composition trees with different
  `SharedTransitionScope` instances.
- **Warning signs**: Image preview animation from chat to fullscreen viewer
  snaps instead of animating; shared element simply disappears.
- **Prevention**:
  1. Use overlay-based fullscreen views instead of Dialogs for media preview.
  2. Implement custom `SharedTransitionScope` that wraps the entire navigation
     host including overlays.
  3. Plan information architecture around this limitation early -- retrofitting
     is expensive.

### A6: Message Send Animation Timing (MINOR)

- **Problem**: The "sent" animation (bubble appears, scrolls to bottom, check
  mark appears) involves coordinating multiple independent animations. If they
  run in parallel without sequencing, the bubble might show the checkmark before
  it finishes appearing.
- **Prevention**:
  1. Use `Animatable` with sequential `animateTo` calls in a single
     `LaunchedEffect`.
  2. Or use `updateTransition` with coordinated `AnimatedContent` states.

### A7: AnimatedContent Size Change Flicker (MAJOR)

- **Problem**: `AnimatedContent` with `SizeTransform` can cause a single frame
  of incorrect size when transitioning between content of different heights
  (e.g., collapsed vs. expanded message). The exit animation and enter animation
  briefly coexist, doubling the measured height.
- **Prevention**:
  1. Set `SizeTransform(clip = true)` to clip during transition.
  2. Use `AnimatedContent` with `ContentTransform` that explicitly handles the
     size: `fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()`.

---

## 4. Design System Pitfalls

### D1: Over-Engineering Tokens (MAJOR)

- **Problem**: Creating 50+ color tokens, 20+ typography variants, and 15+
  elevation levels. Developers cannot remember `surfaceContainerHighest` vs
  `surfaceContainerHigh` vs `surfaceContainer`, so they pick randomly or
  hardcode.
- **Warning signs**: Inconsistent color usage across screens; designers and
  developers disagree on which token to use.
- **Prevention**:
  1. Limit to 12-15 semantic colors, 5-6 text styles, 4 elevation levels.
  2. Name tokens by usage, not by visual property: `chatBubbleSent` not
     `primaryContainer`.
  3. Add Detekt/ktlint custom rules that flag unused or ambiguous tokens.

### D2: Breaking Material3 Accessibility (CRITICAL)

- **Problem**: Custom color schemes that don't meet WCAG AA contrast ratios
  (4.5:1 for normal text, 3:1 for large text). Common failure: light gray text
  on white background for "subtle" look, or low-contrast placeholder text.
- **Warning signs**: Accessibility scanner flags contrast issues; users with
  vision impairments report unreadable text.
- **Prevention**:
  1. Validate every foreground/background pair with a contrast checker.
  2. Use Material3 `ColorScheme` generator which enforces tonal contrast.
  3. Test with Android Accessibility Scanner and TalkBack.
  4. Include contrast validation in design review checklist.

### D3: Hardcoded Colors Instead of Semantic Tokens (MAJOR)

- **Problem**: Developers use `Color(0xFF6C5CE7)` directly in composables.
  Theme changes (dark mode, dynamic color, branded themes) don't propagate.
  Finding all hardcoded colors for a rebrand requires manual search.
- **Warning signs**: Dark mode has white text on white background in some
  screens; dynamic theming partially works.
- **Prevention**:
  1. Define a custom `KotoColors` class with `staticCompositionLocalOf`.
  2. Add a Detekt rule that bans `Color(` constructor calls outside the theme
     package.
  3. Access colors only through `KotoTheme.colors.xxx`.

### D4: Custom Theme Breaks System Dark Mode (MAJOR)

- **Problem**: Custom theme implementation doesn't respond to
  `isSystemInDarkTheme()` or responds but uses wrong palette. StatusBar icons
  remain dark-on-dark or light-on-light. Navigation bar color doesn't match.
- **Warning signs**: Status bar icons invisible in dark mode; system gesture
  navigation bar clashes with app background.
- **Prevention**:
  1. Always fork theme from system setting:
     ```kotlin
     val darkTheme = isSystemInDarkTheme()
     val colors = if (darkTheme) DarkKotoColors else LightKotoColors
     ```
  2. Use `enableEdgeToEdge()` in `onCreate` and control system bar appearance
     via `WindowInsetsControllerCompat`.
  3. Test both modes on every PR -- add screenshot tests for both.

### D5: Inconsistent Spacing (MINOR)

- **Problem**: Mixing 8dp, 10dp, 12dp, 15dp padding across screens without a
  system. Each developer picks "what looks right," resulting in subtle visual
  inconsistency that makes the app feel unpolished.
- **Prevention**:
  1. Define a spacing scale: `4, 8, 12, 16, 24, 32, 48, 64`.
  2. Expose as `KotoTheme.spacing.xs`, `.s`, `.m`, `.l`, `.xl`, `.xxl`.
  3. Lint rule banning `.dp` literals outside the theme definition.

### D6: Custom Font Loading Failures (MAJOR)

- **Problem**: Downloadable fonts (Google Fonts via `FontFamily.Resolver`) can
  fail on first launch without network, leaving fallback system font that breaks
  layout assumptions (different metrics, line height, character width).
- **Warning signs**: Text truncation on first launch; layout shifts when font
  loads asynchronously.
- **Prevention**:
  1. Bundle the primary font in assets -- never rely on downloadable fonts for
     core UI.
  2. If using variable fonts, test weight ranges on API 26+ (variable font
     support) and API 21-25 (static fallback).
  3. Use `FontFamily` with explicit fallback chain.

### D7: Dark Mode Image Treatment (MINOR)

- **Problem**: User-sent images look harsh in dark mode (bright image on dark
  background). Sticker images with transparency show dark-mode background
  bleeding through.
- **Prevention**:
  1. Add subtle scrim overlay on images in dark mode (2-5% black).
  2. For stickers, render on a themed container with rounded corners.
  3. Test with actual user content, not stock photos.

---

## 5. Navigation Pitfalls

### N1: Back Stack Memory Bloat (MAJOR)

- **Problem**: Each navigation destination retains its composition tree and
  associated `ViewModel` in the back stack. Deep navigation (chat list -> chat
  -> profile -> media viewer -> ...) accumulates memory. Image-heavy screens
  (media viewer) compound the problem.
- **Warning signs**: OOM crashes after navigating through multiple chats;
  memory profiler shows retained `Bitmap` objects from previous screens.
- **Prevention**:
  1. Use `popUpTo(route) { inclusive = true }` when navigating to a parallel
     destination (e.g., switching from one chat to another should pop the
     previous chat).
  2. Clear image caches in `onCleared()` of ViewModels holding large bitmaps.
  3. Use `SavedStateHandle` instead of keeping full state in ViewModel memory.

### N2: Deep Link Authentication Race (MAJOR)

- **Problem**: Deep link to `koto://chat/{id}` lands on the chat screen before
  authentication completes. The chat screen tries to load messages, gets a 401,
  shows an error, and then redirects to login -- poor UX.
- **Warning signs**: Flash of error screen before login; crash if chat screen
  assumes non-null user.
- **Prevention**:
  1. Use a `SplashScreen` / auth-check composable as the start destination.
  2. Store the pending deep link, complete auth, then navigate.
  3. Use `navDeepLink` with a `NavController.addOnDestinationChangedListener`
     that checks auth state before allowing navigation.

### N3: Type-Safe Route Arguments (MINOR)

- **Problem**: String-based routes like `"chat/{chatId}"` with manual argument
  parsing break silently when renamed or when types change.
- **Prevention**:
  1. Use Navigation Compose 2.8+ type-safe routes with `@Serializable`:
     ```kotlin
     @Serializable
     data class ChatRoute(val chatId: String)
     ```
  2. Use `toRoute<ChatRoute>()` in the destination to extract arguments safely.

### N4: Keyboard Persistence Across Navigation (MAJOR)

- **Problem**: Navigating from a chat screen (keyboard open) to another screen
  leaves the keyboard open for one frame, then it dismisses -- causing a layout
  flash on the destination screen.
- **Warning signs**: Brief content jump when navigating away from chat.
- **Prevention**:
  1. Explicitly hide keyboard before navigation:
     ```kotlin
     val imm = context.getSystemService(InputMethodManager::class.java)
     imm.hideSoftInputFromWindow(view.windowToken, 0)
     navController.navigate(...)
     ```
  2. Or use `LocalSoftwareKeyboardController.current?.hide()` in Compose.

### N5: Nested NavHost Conflicts (MAJOR)

- **Problem**: Using a nested `NavHost` (e.g., bottom-nav tabs each with their
  own back stack) creates multiple `NavController` instances. Back button
  behavior becomes unpredictable -- sometimes it navigates within the tab,
  sometimes it switches tabs, sometimes it exits the app.
- **Warning signs**: Back button does not return to previous tab; "back" within
  a tab exits the app.
- **Prevention**:
  1. Use the official `NavigationSuiteScaffold` pattern or manual
     `NavBackStackEntry` management per tab.
  2. Explicitly handle back with `BackHandler` in each tab.
  3. Test back button behavior from every reachable screen state.

### N6: Screen State Loss on Configuration Change (MINOR)

- **Problem**: `rememberSaveable` works for primitives but fails for complex
  objects (selected messages, draft reply state) unless a custom `Saver` is
  provided. Configuration change (rotation, locale switch) loses ephemeral UI
  state.
- **Prevention**:
  1. Use `rememberSaveable` with `listSaver` / `mapSaver` for complex state.
  2. Or hoist state to `SavedStateHandle` in the ViewModel.
  3. Test with "Don't keep activities" developer option enabled.

---

## 6. Accessibility Pitfalls

### AC1: Missing Content Descriptions on Chat Elements (CRITICAL)

- **Problem**: Message bubbles, avatars, reaction emoji, and media thumbnails
  lack content descriptions. TalkBack reads raw text without context ("Hello" --
  from whom? when?). Images read as "unlabeled image."
- **Warning signs**: TalkBack testing reveals no sender attribution; media
  messages are completely inaccessible.
- **Prevention**:
  1. Use `Modifier.semantics { contentDescription = "..." }` on message rows
     to provide full context: "$sender said $text at $time".
  2. Group related elements with `Modifier.semantics(mergeDescendants = true)`.
  3. Add content descriptions to all `Image` and `Icon` composables.

### AC2: Touch Target Size Below 48dp (CRITICAL)

- **Problem**: Reaction buttons, reply swipe affordance, and small action icons
  (copy, forward, delete) have touch targets smaller than the 48dp minimum
  recommended by accessibility guidelines.
- **Warning signs**: Accessibility Scanner flags small touch targets; users with
  motor impairments report difficulty tapping.
- **Prevention**:
  1. Use `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.
  2. Use `Modifier.minimumInteractiveComponentSize()` (Material3) which
     automatically pads to meet minimum size.

### AC3: Focus Order in Chat (MAJOR)

- **Problem**: Default focus order in a reversed LazyColumn reads newest message
  first, which is correct visually but confusing for TalkBack users who expect
  chronological order. Navigation between input field, send button, and
  attachment button may not follow visual order.
- **Prevention**:
  1. Set explicit `Modifier.semantics { traversalIndex = ... }` to enforce
     logical reading order.
  2. Test full TalkBack navigation flow: open chat -> read messages -> compose
     -> send.

### AC4: Color-Only Information Encoding (MAJOR)

- **Problem**: Using color alone to indicate message status (blue = delivered,
  green = read) or online status (green dot) excludes color-blind users.
- **Prevention**:
  1. Combine color with shape or icon (single check = sent, double check =
     delivered, filled double check = read).
  2. Add text labels accessible via `contentDescription`.

### AC5: Custom Gesture Accessibility (MINOR)

- **Problem**: Swipe-to-reply and long-press-for-menu are not discoverable by
  TalkBack users. Custom gestures have no accessibility equivalent.
- **Prevention**:
  1. Add `Modifier.semantics { customActions = listOf(...) }` for swipe and
     long-press actions.
  2. Provide alternative paths (context menu with "Reply" option).

---

## 7. State Management Pitfalls

### S1: Stale State After Process Death (CRITICAL)

- **Problem**: `mutableStateOf` and `StateFlow` values are lost on process
  death. Draft message text, scroll position, selected messages, and pending
  uploads disappear. Users return to the app and their half-typed message is
  gone.
- **Warning signs**: Developers never test process death; QA doesn't test with
  "Don't keep activities."
- **Prevention**:
  1. Use `SavedStateHandle` in ViewModel for all user-editable state.
  2. Persist draft text to local DB (Room/SQLDelight) on every change.
  3. Test with Developer Options -> "Don't keep activities" enabled.

### S2: Race Condition Between UI State and DB (MAJOR)

- **Problem**: Optimistic UI update (message appears immediately) followed by
  DB write that triggers `Flow` emission causes a duplicate or flicker: message
  appears from optimistic insert, then disappears briefly, then reappears from
  DB flow.
- **Warning signs**: Messages briefly flash or duplicate on send.
- **Prevention**:
  1. Use single source of truth: insert into DB first, observe DB flow.
  2. If optimistic UI is needed, use a `pending` flag and merge pending +
     persisted lists in the mapping layer.
  3. Use `distinctUntilChanged()` on the message list flow.

### S3: Unhandled Loading/Error States (MAJOR)

- **Problem**: Initial chat load, pagination, and media upload each have
  loading/error/success states. Forgetting to handle `Loading` leaves a blank
  screen; forgetting `Error` shows infinite spinner.
- **Prevention**:
  1. Model all async operations as `sealed class UiState<T> { Loading, Success(data), Error(e) }`.
  2. Handle all three states explicitly in the UI.
  3. Include retry affordance in error states.

### S4: Pagination Scroll Position Jumps (MAJOR)

- **Problem**: Loading older messages (prepend pagination) shifts the scroll
  position because new items are added above the current viewport. The user sees
  a sudden jump.
- **Warning signs**: Scroll position jumps when reaching the top and older
  messages load.
- **Prevention**:
  1. Use Paging 3 with `PagingConfig(enablePlaceholders = true)` and
     `LazyPagingItems` which handle prepend position maintenance.
  2. If manual pagination, use `LazyListState.requestScrollToItem` to maintain
     relative position after prepend.

### S5: Typing Indicator State Conflicts (MINOR)

- **Problem**: Typing indicator from WebSocket arrives after the message itself
  (out-of-order), showing "Alice is typing..." after Alice's message is already
  displayed. Or typing indicator never disappears if the "stopped typing" event
  is lost.
- **Prevention**:
  1. Auto-dismiss typing indicator after timeout (5-10 seconds).
  2. Clear typing indicator when a new message from that user arrives.
  3. Deduplicate with timestamps -- ignore typing events older than the latest
     message from that user.

---

## Summary: Top 10 Most Impactful Pitfalls

| Rank | ID  | Severity | Pitfall                                      |
|------|-----|----------|----------------------------------------------|
| 1    | P1  | CRITICAL | Unstable list parameters in LazyColumn        |
| 2    | C1  | CRITICAL | Reversed LazyColumn scroll semantics          |
| 3    | P2  | CRITICAL | Lambda captures causing recomposition          |
| 4    | C2  | CRITICAL | Keyboard resize jank                           |
| 5    | A2  | CRITICAL | Gesture vs scroll conflict                     |
| 6    | AC1 | CRITICAL | Missing content descriptions                   |
| 7    | S1  | CRITICAL | Stale state after process death                |
| 8    | D2  | CRITICAL | Breaking Material3 accessibility               |
| 9    | P3  | MAJOR    | Reading state in wrong scope                   |
| 10   | S4  | MAJOR    | Pagination scroll position jumps               |
