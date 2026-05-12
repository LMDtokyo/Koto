# Features Research: Premium Messenger UI/UX (2025-2026)

> Research compiled for Koto Messenger. Based on design patterns from leading
> messengers (Telegram, WhatsApp, Signal, iMessage, Line, KakaoTalk) and
> premium app design trends observed through 2025.

---

## Table Stakes (Must Have)

These are baseline expectations. Without every single item below, users will
perceive the app as unfinished.

### Chat Screen

#### Message Bubbles
- Rounded chat bubbles with a **tail/arrow indicator** on the leading edge
  (left tail for received, right tail for sent). Tail radius ~6dp, bubble
  corner radius **18dp** (outer corners), **4dp** on the tail-adjacent corner.
- Sent bubbles right-aligned, received bubbles left-aligned, with **max width
  of 78%** of the screen to prevent full-width stretch on tablets.
- **Message grouping by sender + time proximity**: consecutive messages from
  the same sender within a 60-second window are grouped. Only the last bubble
  in a group shows the tail. Internal bubble spacing is **2dp**, between
  groups **8dp**.
- Inline **sender name** (colored by hash of user ID) in group chats, shown
  only on the first bubble of a group.

#### Read Receipts
- **Single gray check** = sent to server
- **Double gray check** = delivered to recipient device
- **Double colored check** (Primary color, #6C5CE7 light / #8B7CF7 dark) =
  read by recipient
- Check icon size: **14dp**, positioned bottom-right of the bubble, with 4dp
  padding from bubble edge.
- Transition between states: 200ms crossfade.

#### Typing Indicator
- Three animated dots inside a received-style bubble. Dots are **6dp**
  diameter, spaced **4dp** apart.
- Animation: sequential vertical bounce, 150ms stagger between dots, 600ms
  full cycle. Easing: ease-in-out.
- Appears at the bottom of the message list, pushes content up if user is
  scrolled to bottom.
- Timeout: disappears after **8 seconds** of no typing event from server.

#### Reply / Quote
- **Swipe right** on any message to trigger reply. Threshold: **48dp** of
  horizontal travel.
- Quoted message appears above the compose bar with a **3dp** colored left
  border (Primary color) and a **dismiss X** button on the right.
- Tapping the quoted preview in the message list scrolls to and **highlights**
  the original message (200ms yellow flash, then fade over 1000ms).

#### Context Menu (Long Press)
- Long press (300ms threshold) triggers a context menu above/below the
  message with haptic feedback (medium impact).
- Actions: **Reply**, **Copy**, **Forward**, **Pin**, **Delete**, **Select**.
- Menu style: elevated surface card (elevation 8dp), rounded corners 12dp,
  with a row of quick-reaction emojis above the action list.
- Background dims to 40% black with blur (radius 12px).

#### Media in Chat
- Images: rounded corners **12dp**, max height **280dp**, tap to open
  full-screen viewer with pinch-to-zoom.
- Image loading: blurhash placeholder (11x11 components) while downloading.
- Video: thumbnail with centered play button (48dp circle, semi-transparent),
  duration badge in bottom-right.
- Voice message: inline waveform visualizer, playback speed toggle (1x, 1.5x,
  2x), duration label.
- File attachments: card with file icon, name (ellipsized at 24 chars),
  size, and download arrow.

#### Timestamps
- Shown on each **message group**, not every message. Position: centered
  between groups as a chip with 8dp vertical margin.
- Format: "HH:mm" for today, "Yesterday HH:mm", "Mon HH:mm" for the week,
  "DD MMM, HH:mm" for older.
- Text style: Label Small (11sp), On Surface Variant color.

#### Scroll-to-Bottom FAB
- Appears when user scrolls **>300dp** above the bottom of the list.
- Circular, **48dp** diameter, positioned **16dp** from the right edge and
  **16dp** above the compose bar.
- Shows unread count badge (top-right, min 20dp diameter, Primary background,
  white text, Label Small).
- Tap animates smooth scroll to bottom (duration based on distance, max
  500ms).

#### Keyboard Handling
- **WindowInsets-based resize** (not adjustPan). Chat list scrolls to keep
  the last visible message in place when keyboard opens.
- Compose bar sits directly above the keyboard with no gap.
- On iOS: interactive keyboard dismissal (drag down to dismiss, follows
  finger).

#### Compose Bar
- Minimum height: **48dp**, expands with multiline text up to **120dp** (then
  scrolls internally).
- Left: attachment button (24dp icon). Right: send/mic button. Center: text
  input with hint "Message..." in On Surface Variant color.
- Attach menu: bottom sheet with grid of options (Camera, Gallery, File,
  Location, Contact), icon size 48dp, 3 columns.

---

### Conversation List

#### List Item Layout
- Height: **72dp**. Left: avatar (**48dp** circle). Right of avatar (12dp
  gap): name (Title Medium, single line, ellipsis) + last message preview
  (Body Small, max 2 lines, On Surface Variant) stacked. Far right: timestamp
  (Label Small) above, unread badge below.

#### Unread Badge
- Circular if count < 10, stadium-shape if >= 10. Min size: **20dp** height,
  **20dp** min width, 8dp horizontal padding.
- Background: Primary (#6C5CE7). Text: white, Label Small (11sp), center
  aligned.
- Muted conversations: badge background is Surface Variant instead.

#### Online / Last Seen
- Online: **10dp** green (#10B981) dot with **2dp** white border, overlaid
  on the avatar bottom-right.
- Last seen: shown below the name in label style ("last seen 5 min ago").
  Not shown if user has hidden it.

#### Swipe Actions
- **Swipe right**: archive (teal background, archive icon).
- **Swipe left**: pin (amber), mute (gray), delete (red) -- revealed
  sequentially as swipe extends.
- Swipe threshold: 72dp for first action, 144dp for full reveal.
- Haptic feedback at each threshold crossing.

#### Pinned Conversations
- Pinned items stay at the top of the list with a small **pin icon** (12dp)
  next to the timestamp.
- Max 5 pinned conversations.
- Divider line (1dp, Surface Variant) between pinned and unpinned sections.

#### Search
- Search bar at the top, collapses into the toolbar on scroll-down, expands
  on scroll-up.
- Real-time filtering as user types (debounce 200ms).
- Matches highlighted in **Primary** color within results.
- Recent searches shown as chips below the search bar when empty.

---

### Navigation

#### Bottom Navigation Bar
- 4 tabs: **Chats**, **Contacts**, **Calls**, **Settings**.
- Icon size: **24dp**. Label: Label Small (11sp).
- Active tab: Primary color icon + label. Inactive: On Surface Variant.
- Bar height: **64dp** + safe area inset on bottom.
- Badge on Chats tab: total unread count.

#### Edge-to-Edge Design
- Content draws behind the system status bar and navigation bar.
- Status bar: transparent, icons adapt to light/dark content.
- Navigation bar (Android): transparent or matches surface color, gesture
  nav preferred.
- Safe area insets respected on all sides.

#### Predictive Back Gesture (Android 14+)
- Opt-in to predictive back via `android:enableOnBackInvokedCallback="true"`.
- Custom back transition: shrink + fade to the previous screen (shared
  element where applicable).
- Cross-activity and cross-fragment transitions supported.

#### Screen Transitions
- Default transition: **300ms**, ease-in-out curve.
- Forward navigation: new screen slides in from right + fades in.
- Backward navigation: current screen slides out to right + fades out.
- Shared element: avatar in conversation list morphs into header avatar in
  chat screen (see Differentiators below).

---

### General

#### Dark Mode + Light Mode
- Follows system setting by default, with manual override in settings.
- Smooth transition between modes: 400ms crossfade on the root surface.
- All colors defined as semantic tokens, not hard-coded.

#### Splash Screen
- Android 12+ SplashScreen API: branded icon (Koto logo, 192dp), background
  matches theme, exit animation is a circular reveal expanding from icon
  center (500ms).
- Below Android 12: custom splash activity with the same visual treatment.

#### Pull-to-Refresh
- On conversation list: custom animation (see Differentiators for spring
  variant).
- Standard behavior: overscroll indicator then spinner then content refreshes.
- Spinner color: Primary.

#### Empty States
- Custom illustrations for each empty state:
  - No conversations: illustration of two speech bubbles + "Start a
    conversation" CTA button.
  - No search results: magnifying glass illustration + "No results for
    '{query}'" text.
  - No contacts: people illustration + "Invite friends" CTA.
- Illustration size: 200dp x 200dp max, centered.
- Text: Body Large, On Surface Variant. CTA: Primary filled button.

---

## Differentiators (Makes Koto Unique)

These are the features that make Koto feel **premium** and unlike any other
messenger on the market. Every detail below should be implemented faithfully.

### Visual Identity

#### Gradient Chat Bubbles
- Sent message bubbles use a **linear gradient at 135 degrees**:
  - Light theme: `#6C5CE7` (top-left) to `#8B7CF7` (bottom-right)
  - Dark theme: `#6C5CE7` (top-left) to `#7C6CF7` (bottom-right)
- Gradient is **relative to each bubble**, not the screen. Each bubble
  independently renders its own gradient.
- Received bubbles remain **flat** Surface Variant color for contrast.
- Text on sent bubbles: `#FFFFFF` at 100% opacity. Timestamps on sent
  bubbles: `#FFFFFF` at 70% opacity.

#### Glassmorphism Elements
- **Bottom navigation bar**: frosted glass effect. Background: Surface color
  at 60% opacity + backdrop blur (radius **24px**, saturation **1.8x**).
  A subtle 1px top border at `#FFFFFF` 10% opacity (light) / `#FFFFFF` 5%
  (dark).
- **Compose bar background**: same frosted glass treatment.
- **Context menu overlay**: frosted glass card with blur radius 16px.
- **Profile modal / bottom sheets**: frosted glass header area.
- Performance note: use `RenderEffect` on Android 12+, fallback to solid
  color with higher opacity on older devices.

#### Spring-Based Animations
- Replace ALL linear and ease-in-out animations with **spring physics**:
  - Stiffness: **300** (medium), Damping ratio: **0.7** (slightly bouncy)
  - For heavier elements (sheets, modals): Stiffness 200, Damping 0.8
  - For light elements (badges, dots): Stiffness 400, Damping 0.6
- Framework: Android `SpringAnimation` / `spring()` spec in Compose, iOS
  `UISpringTimingParameters` or SwiftUI `.spring()`.
- Every list insertion, removal, and reorder should use spring-based layout
  animation.

#### Animated Transitions
- **Conversation list to chat screen**: shared element transition.
  1. Avatar in the list item morphs into the avatar in the chat toolbar.
  2. Name text animates from list position to toolbar position.
  3. Chat content fades in from 0% opacity over 200ms (staggered 100ms after
     transition starts).
  4. Total transition: 350ms, spring curve.
- **Chat screen back to list**: reverse of above, avatar shrinks back.
- **New conversation**: screen slides up from bottom with spring (400ms).

#### Custom Send Button Morphing
- **State 1 (empty input)**: microphone icon (24dp), for voice messages.
- **State 2 (text entered)**: morphs into **send arrow** icon. Morph
  animation: mic icon rotates 90 degrees and fades while arrow scales up from
  center. Duration: 200ms, spring.
- **State 3 (sending)**: arrow icon shrinks and transforms into a circular
  progress indicator (2dp stroke, Primary color).
- **State 4 (sent)**: progress completes and morphs into a **checkmark**
  icon, which holds for 800ms and then morphs back to mic (if input is now
  empty) or stays as arrow (if input has text).
- All transitions use spring physics, not linear interpolation.

#### Particle Effects
- **First message in a new conversation**: when the first message bubble
  appears, emit **24 small particles** (3dp circles) in randomized Primary
  and Secondary colors from the center of the bubble. Particles travel
  outward 48-96dp with gravity pulling them down. Duration: 800ms, then
  fade out over 200ms.
- **Reaction added**: 6-8 particles of the reaction emoji color burst from
  the reaction badge.
- Implementation: custom Canvas/SurfaceView drawing, NOT Lottie (for perf
  control). Particle count should be halved on low-end devices (detected
  via `ActivityManager.isLowRamDevice()`).

#### Ambient Background
- The chat screen background has a **subtle animated gradient** that shifts
  slowly over time.
- Two gradient colors (very low saturation versions of Primary):
  - Light: `#F0EDFF` and `#E8F4FF` alternating positions
  - Dark: `#0E0D18` and `#0D1015` alternating positions
- Animation: gradient center point moves in a slow figure-8 pattern, cycle
  time **30 seconds**. Very subtle, should be barely perceptible.
- Implementation: shader-based (AGSL on Android 13+, OpenGL fallback).
  Must not impact battery -- use `Choreographer` frame callbacks and skip
  frames on low battery.

---

### Micro-Interactions

These are the small details that make the app feel alive and responsive.

#### Message Send Animation
1. On tap of send button:
   - Bubble appears at its final position but at **90% scale** and **0%
     opacity**.
   - Spring animation to **102% scale** (slight overshoot) then settles at
     **100%**. Duration: ~250ms.
   - Opacity animates from 0% to 100% over first 100ms (linear).
2. Simultaneously: send button plays its morph animation (arrow to progress).
3. The chat list scrolls to bottom if not already there (spring, 200ms).

#### Message Receive Animation
1. New bubble slides in from **-24dp** left of its final position.
2. Spring animation to final position (stiffness 350, damping 0.65).
3. Opacity from 0% to 100% over first 80ms.
4. If the user is scrolled to bottom, list scrolls down to show new message.
5. If user is NOT at bottom, increment the scroll-to-bottom FAB badge
   instead -- do NOT auto-scroll (this interrupts reading).

#### Double-Tap to React
- Double-tap anywhere on a message bubble triggers a **heart reaction**.
- Heart emoji (24dp) scales up from 0% at the tap point, overshoots to
  120%, settles at 100% via spring. Then floats up 24dp while fading out
  over 600ms.
- A small heart badge appears on the message (persistent reaction).
- Haptic: light impact on the double-tap, medium impact when the heart
  appears.

#### Pull-to-Refresh (Custom)
- Instead of standard circular spinner, use a **liquid/spring animation**:
  - As user pulls down, a blob of Primary color stretches from the top edge.
  - At threshold (72dp pull), blob detaches and forms a circle (refresh
    indicator). Spring bounce on detach.
  - During loading: circle pulses gently (scale 1.0 to 1.05, 800ms cycle).
  - On complete: circle shrinks to 0 with spring.

#### Swipe Haptics
- At each swipe action threshold (72dp, 144dp), trigger a **selection
  haptic** (`HapticFeedbackConstants.CONFIRM` on Android 13+, fallback
  to medium impact).
- When the swipe action is committed (finger lifted past threshold),
  trigger a **success haptic**.

#### Avatar Long-Press Profile Card
- Long press (400ms) on any avatar triggers a **popup profile card**:
  - Card appears centered on the avatar with spring scale-up (from 0%).
  - Background: frosted glass blur (radius 20px) on the entire screen.
  - Card contents: large avatar (80dp), display name (Title Large), status
    text (Body Medium), and quick action buttons (Message, Call, Profile).
  - Card size: 280dp wide, height wraps content.
  - Dismissal: tap outside then spring scale-down to 0%.

#### Typing Indicator (Custom Wave)
- Instead of three bouncing dots, use a **wave animation**:
  - Three dots (6dp each) connected by a subtle sine-wave line.
  - The wave oscillates vertically (amplitude 4dp, frequency 1.5Hz).
  - Dots ride the wave peaks, creating a fluid motion.
  - Color: On Surface Variant at 60% opacity.

#### Unread Count Badge Animation
- When unread count changes: badge scales to 120% then settles at 100%
  (spring, 200ms).
- When count goes from 0 to 1: badge scales up from 0% with spring +
  a tiny burst of 4 particles in Primary color.
- When count goes to 0: badge shrinks to 0% (spring, 150ms).

---

### Color Palette -- Koto Brand

These are the definitive color tokens. Every component must reference these
tokens, never raw hex values.

#### Light Theme

| Token              | Hex                           | Usage                                  |
|--------------------|-------------------------------|----------------------------------------|
| Primary            | `#6C5CE7`                     | Buttons, links, active states          |
| Primary Variant    | `#8B7CF7`                     | Gradient end, hover states             |
| Primary Container  | `#EDE9FF`                     | Chip backgrounds, soft highlights      |
| Secondary          | `#00D2FF`                     | Accents, online indicator, badges      |
| Secondary Variant  | `#00B8E6`                     | Pressed state of secondary elements    |
| Background         | `#FAFBFF`                     | App background                         |
| Surface            | `#FFFFFF`                     | Cards, sheets, compose bar             |
| Surface Variant    | `#F0F1F8`                     | Received bubbles, input fields         |
| Surface Elevated   | `#FFFFFF` + elevation 2dp     | Floating cards, FAB                    |
| Sent Bubble        | `gradient(#6C5CE7, #8B7CF7)`  | User's sent messages                   |
| Received Bubble    | `#F0F1F8`                     | Others' messages                       |
| On Primary         | `#FFFFFF`                     | Text on primary                        |
| On Secondary       | `#FFFFFF`                     | Text on secondary                      |
| On Surface         | `#1A1A2E`                     | Primary text                           |
| On Surface Variant | `#6B7280`                     | Secondary text, timestamps             |
| On Sent Bubble     | `#FFFFFF`                     | Text on sent messages                  |
| On Received Bubble | `#1A1A2E`                     | Text on received messages              |
| Outline            | `#E5E7EB`                     | Dividers, borders                      |
| Outline Variant    | `#D1D5DB`                     | Stronger dividers                      |
| Error              | `#EF4444`                     | Errors, delete actions                 |
| Error Container    | `#FEE2E2`                     | Error background                       |
| Success            | `#10B981`                     | Online, delivered, success             |
| Success Container  | `#D1FAE5`                     | Success background                     |
| Warning            | `#F59E0B`                     | Warnings, attention                    |
| Scrim              | `#000000` at 40%              | Overlay/dim behind modals              |

#### Dark Theme

| Token              | Hex                           | Usage                                  |
|--------------------|-------------------------------|----------------------------------------|
| Primary            | `#8B7CF7`                     | Lighter violet for dark bg             |
| Primary Variant    | `#A78BFA`                     | Gradient end                           |
| Primary Container  | `#2D2754`                     | Chip backgrounds, soft highlights      |
| Secondary          | `#22D3EE`                     | Cyan, slightly brighter                |
| Secondary Variant  | `#06B6D4`                     | Pressed state                          |
| Background         | `#0A0A12`                     | Deep dark background                   |
| Surface            | `#14141F`                     | Cards, sheets                          |
| Surface Variant    | `#1E1E2E`                     | Received bubbles, input                |
| Surface Elevated   | `#1A1A28`                     | Floating cards, FAB                    |
| Sent Bubble        | `gradient(#6C5CE7, #7C6CF7)`  | Slightly muted gradient                |
| Received Bubble    | `#1E1E2E`                     | Dark received                          |
| On Primary         | `#FFFFFF`                     | Text on primary                        |
| On Secondary       | `#0A0A12`                     | Text on secondary                      |
| On Surface         | `#E5E7EB`                     | Primary text                           |
| On Surface Variant | `#9CA3AF`                     | Secondary text                         |
| On Sent Bubble     | `#FFFFFF`                     | Text on sent messages                  |
| On Received Bubble | `#E5E7EB`                     | Text on received messages              |
| Outline            | `#2D2D3D`                     | Dividers, borders                      |
| Outline Variant    | `#3D3D50`                     | Stronger dividers                      |
| Error              | `#F87171`                     | Errors (brighter for dark bg)          |
| Error Container    | `#450A0A`                     | Error background                       |
| Success            | `#34D399`                     | Online, delivered                      |
| Success Container  | `#064E3B`                     | Success background                     |
| Warning            | `#FBBF24`                     | Warnings                               |
| Scrim              | `#000000` at 60%              | Overlay/dim behind modals              |

---

### Typography

Font stack: **Inter** (primary), **system sans-serif** (fallback).
Inter is chosen for its excellent legibility at small sizes, wide language
support, and variable font capability for fine-tuned weight control.

| Style          | Size  | Weight    | Letter Spacing | Line Height | Usage                            |
|----------------|-------|-----------|----------------|-------------|----------------------------------|
| Display Large  | 28sp  | SemiBold  | -0.5sp         | 36sp        | Onboarding titles                |
| Display Medium | 24sp  | SemiBold  | -0.25sp        | 32sp        | Section headers                  |
| Title Large    | 22sp  | SemiBold  | 0sp            | 28sp        | Chat screen name in toolbar      |
| Title Medium   | 18sp  | Medium    | 0sp            | 24sp        | Conversation name in list        |
| Title Small    | 16sp  | Medium    | 0.1sp          | 22sp        | Dialog titles                    |
| Body Large     | 16sp  | Regular   | 0.15sp         | 24sp        | Conversation list preview        |
| Body Medium    | 15sp  | Regular   | 0.1sp          | 22sp        | Chat messages                    |
| Body Small     | 13sp  | Regular   | 0.2sp          | 18sp        | Secondary text                   |
| Label Large    | 14sp  | Medium    | 0.1sp          | 20sp        | Button text                      |
| Label Medium   | 12sp  | Medium    | 0.5sp          | 16sp        | Timestamps, badges               |
| Label Small    | 11sp  | Medium    | 0.5sp          | 14sp        | Typing indicator, status         |

---

### Spacing Scale

Consistent spacing creates visual rhythm. Every margin and padding in the
app must use one of these values.

| Token | Value | Common Usage                                         |
|-------|-------|------------------------------------------------------|
| xxs   | 2dp   | Between check marks, between dot indicators          |
| xs    | 4dp   | Inline icon-to-text gap, bubble internal padding adj |
| sm    | 8dp   | Between grouped bubbles, chip padding                |
| md    | 12dp  | Avatar-to-text gap, section internal padding         |
| lg    | 16dp  | Screen horizontal padding, between sections          |
| xl    | 24dp  | Between major UI groups                              |
| xxl   | 32dp  | Top/bottom padding on screens                        |
| xxxl  | 48dp  | Empty state vertical centering offset                |

#### Elevation Scale

| Level | Elevation | Usage                                |
|-------|-----------|--------------------------------------|
| 0     | 0dp       | Flat surfaces, backgrounds           |
| 1     | 1dp       | Cards in lists                       |
| 2     | 2dp       | Compose bar, navigation bar          |
| 3     | 4dp       | FAB, floating elements               |
| 4     | 8dp       | Context menus, dropdowns             |
| 5     | 16dp      | Modals, bottom sheets (expanded)     |

---

### Corner Radius Scale

| Token             | Radius | Usage                                   |
|-------------------|--------|-----------------------------------------|
| radius-xs         | 4dp    | Tail-adjacent bubble corner             |
| radius-sm         | 8dp    | Input fields, chips                     |
| radius-md         | 12dp   | Media thumbnails, context menus, cards  |
| radius-lg         | 18dp   | Chat bubbles (outer corners)            |
| radius-xl         | 24dp   | Bottom sheets (top corners)             |
| radius-full       | 9999dp | Avatars, badges, FAB                    |

---

### Icon System

- Icon set: **Custom outline icons** (2dp stroke) for primary navigation,
  filled variants for active states.
- Size: **24dp** standard, **20dp** inline/small, **32dp** for empty states.
- Color: inherits from text color token of parent context.
- Touch target: minimum **48dp x 48dp** regardless of visual icon size.

---

### Motion System Summary

| Animation Type       | Duration  | Curve                              |
|----------------------|-----------|------------------------------------|
| Micro (icon morph)   | 150-200ms | Spring (stiffness 400, damping 0.7)|
| Standard (screen)    | 300-350ms | Spring (stiffness 300, damping 0.7)|
| Emphasized (modal)   | 400-500ms | Spring (stiffness 200, damping 0.8)|
| Background (ambient) | 30000ms   | Linear (looping)                   |
| Fade in/out          | 150ms     | Linear                             |
| Stagger delay        | 30ms      | Per item                           |

---

## Anti-Features (Do NOT Copy)

These are common patterns in existing messengers that Koto must explicitly
avoid to maintain its premium positioning.

### Visual Anti-Patterns
- **WhatsApp green** (`#25D366`, `#075E54`) -- overused, immediately makes
  the app look like a WhatsApp clone. Koto uses violet as its primary to
  stand apart.
- **Telegram's flat blue bubbles** -- too simple, no depth or gradient. The
  single flat `#EFFDDE` sent bubble is functional but not premium.
- **Signal's plain UI** -- intentionally utilitarian, which signals (no pun
  intended) "privacy tool" rather than "delightful communication app."
  Privacy should be invisible, not the aesthetic.
- **Line/KakaoTalk emoji overload** -- cute stickers everywhere dilute the
  premium feel. Koto supports stickers but does not push them in the UI
  chrome itself.

### Interaction Anti-Patterns
- **Center-bottom FAB** (classic Material Design) -- dated Material cliche.
  The "new chat" action should be in the toolbar or integrated into the
  empty state, not a floating circle.
- **Standard Material3 bottom navigation without customization** -- the
  default M3 `NavigationBar` looks generic. Koto must customize with
  glassmorphism, custom indicator shape, and spring animations on tab
  switch.
- **Generic hint text**: "Send message" or "Type a message" -- boring.
  Use "Message..." (short, clean) or contextual hints like the contact's
  first name ("Message Alex...").
- **Static splash screen** -- a splash that is just a static image feels
  like a loading screen. Must use the animated circular reveal exit
  transition at minimum.
- **Abrupt screen transitions** -- instant cuts between screens (no
  animation) feel broken. Even a simple 200ms fade is better than nothing,
  but Koto should use the full shared-element transition system.

### Architectural Anti-Patterns
- **ViewPager for chat tabs** -- creates unnecessary complexity and
  horizontal swiping conflicts with message swipe-to-reply. Use simple
  fragment/composable navigation.
- **WebView-based chat rendering** -- terrible performance for message
  lists. Use native RecyclerView (Android) / LazyColumn (Compose) /
  UICollectionView (iOS).
- **Polling for new messages** -- use persistent WebSocket connection with
  NATS-based pub/sub (already in Koto's architecture).
- **Storing images as base64 in the database** -- use object storage (MinIO,
  already in Koto's infra) with CDN-friendly URLs.

### UX Anti-Patterns
- **Requiring phone number for registration** -- privacy-hostile, blocks
  adoption. Koto should support username-based registration.
- **Read receipts with no way to disable** -- users must be able to turn
  off read receipts for privacy.
- **"Last seen" with no granularity** -- must offer: Everyone, Contacts
  Only, Nobody.
- **No message editing** -- modern expectation (even WhatsApp added it in
  2023). Allow editing within 15 minutes, show "edited" label.
- **Notification sounds that all sound the same** -- provide a curated set
  of 8-10 notification tones, all short (<1s) and distinctive.

---

## Appendix A: Component Specifications Quick Reference

### Chat Bubble Dimensions
```
Outer corner radius:  18dp
Inner corner radius:   4dp (tail side)
Horizontal padding:   12dp
Vertical padding:      8dp
Max width:            78% of screen
Min width:            48dp (for very short messages)
Tail width:           10dp
Tail height:           8dp
Group spacing:         2dp (same sender)
Between groups:        8dp
```

### Compose Bar Dimensions
```
Min height:           48dp
Max height:          120dp (then internal scroll)
Horizontal padding:   12dp
Icon button size:     40dp (touch target 48dp)
Text input padding:    8dp vertical, 12dp horizontal
Corner radius:        24dp (pill shape)
```

### Conversation List Item
```
Total height:         72dp
Avatar size:          48dp
Avatar left margin:   16dp
Text left margin:     12dp (from avatar)
Right margin:         16dp
Name + preview gap:    2dp
Badge min size:       20dp
Badge corner:         9999dp (circle/stadium)
```

### Bottom Navigation Bar
```
Height:               64dp + bottom inset
Icon size:            24dp
Label size:           11sp
Active indicator:     64dp x 32dp stadium, Primary Container fill
Glass blur radius:    24px
Glass bg opacity:     60%
```

---

## Appendix B: Accessibility Requirements

Premium UI must also be accessible UI. These are non-negotiable.

- **Minimum touch target**: 48dp x 48dp for all interactive elements.
- **Color contrast**: WCAG AA minimum (4.5:1 for normal text, 3:1 for large
  text). Verified for both themes.
- **Screen reader support**: all custom views must have content descriptions.
  Chat bubbles announce: sender, message text, timestamp, read status.
- **Reduce motion**: respect `prefers-reduced-motion` / Android "Remove
  animations" setting. Fall back to simple fades (150ms) instead of springs.
  Disable particle effects entirely.
- **Dynamic type**: all text must scale with system font size preference,
  up to 200%. Layout must not break at large sizes.
- **Keyboard navigation**: for desktop/tablet, all actions reachable via
  Tab + Enter. Focus indicators visible (2dp Primary outline).

---

## Appendix C: Performance Budgets

A premium app that stutters is not premium.

| Metric                        | Target         |
|-------------------------------|----------------|
| Cold start to interactive     | < 800ms        |
| Frame render (P95)            | < 12ms (83fps) |
| Message list scroll jank      | 0 dropped frames |
| Keyboard open-to-ready        | < 200ms        |
| Image placeholder to loaded   | < 500ms (WiFi) |
| Animation frame budget        | 8ms per frame  |
| APK size (Android)            | < 25MB         |
| RAM usage (idle, 1000 msgs)   | < 120MB        |
| WebSocket reconnect           | < 2s           |
| Background battery drain/hr   | < 1%           |

---

*Last updated: 2026-04-05*
*For: Koto Messenger project*
