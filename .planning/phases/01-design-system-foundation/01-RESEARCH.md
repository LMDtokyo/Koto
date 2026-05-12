# Phase 1: Design System Foundation - Research

**Researched:** 2026-04-05
**Domain:** Jetpack Compose custom theme system, design tokens, typography, animation
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DS-01 | Custom KotoTheme с semantic color tokens (primary, surface, onSurface, etc.) для light/dark | CompositionLocal pattern verified in ARCHITECTURE.md; animateColorAsState for animated switching |
| DS-02 | Цветовая палитра: deep violet primary (#7B61FF), gradient sent bubbles (#7C3AED→#5B21B6) | Exact palette defined in FEATURES.md; existing Color.kt already uses violet; DS-02 specifies override to #7B61FF primary per REQUIREMENTS.md |
| DS-03 | Typography scale на базе Inter font (display, title, body, label — 8 стилей) | Inter variable font bundled as TTF; existing Type.kt extended to 11 styles; FEATURES.md defines exact scale |
| DS-04 | Spacing scale (xxs 2dp → xxxl 48dp) как design tokens | KotoSpacing data class with Dp fields; ARCHITECTURE.md has full structure including messenger-semantic named tokens |
| DS-05 | Shape system (rounded corners: sm 8dp, md 12dp, lg 16dp, xl 20dp, bubble 18dp) | KotoShapes data class; bubble shapes have asymmetric radius (out/in tail variants) |
| DS-06 | Animated theme switching (dark ↔ light) с circular reveal или crossfade | animateColorAsState per-color approach verified; ARCHITECTURE.md has working code; crossfade approach also viable |
| DS-07 | @Immutable UI model classes для всех data objects (zero unnecessary recompositions) | @Immutable annotation on all token data classes + UI model data classes; ImmutableList for collections |
</phase_requirements>

---

## Summary

Phase 1 creates KotoTheme — the single source of truth for every visual value in the app. The foundation consists of six token categories (colors, typography, spacing, shapes, elevation, motion) delivered via `CompositionLocal` and accessed through a singleton `KotoTheme` object. No composable outside the `ui/theme/` package should reference raw hex values, raw dp values, or raw durations.

The current codebase already has a stub `Theme.kt`, `Color.kt`, and `Type.kt` in `ui/theme/`. These files are incomplete: they cover dark mode only, have no semantic separation between palette and semantic layers, no spacing tokens, no shape tokens, no motion specs, no light theme, and no animated switching. Phase 1 replaces them entirely with the full system described in ARCHITECTURE.md.

A critical blocker documented in STATE.md must be the first action: upgrade the Compose BOM from `2025.03.01` to `2026.03.01` in `libs.versions.toml`. This unlocks stable Material3 1.4.0, Compose Animation 1.10.6, and `animateItem()` needed in later phases. Without this upgrade, writing any composable against the new APIs is blocked.

**Primary recommendation:** Replace the three stub theme files with the six-file KotoTheme system (Colors.kt, Palette.kt, Typography.kt, Spacing.kt, Shapes.kt, Motion.kt, Theme.kt) plus add `UiModels.kt` for `@Immutable` data classes. BOM upgrade must land first.

---

## Project Constraints (from CLAUDE.md)

| Constraint | Directive |
|------------|-----------|
| Platform | Android only — minSdk 26, Kotlin + Jetpack Compose |
| Performance | 120fps target, <100ms touch response, <16ms frame time |
| Existing code | Do NOT break crypto, networking, or Room DB |
| Libraries | Prefer custom/modern — not stock Material Design out of the box |
| XML layouts | Compose only, no legacy XML |
| Third-party UI kits | Custom design system, no ready-made component kits |
| GSD workflow | All file changes must go through a GSD command |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Compose BOM | **2026.03.01** (upgrade from 2025.03.01) | Aligns all Compose library versions | STATE.md mandates this upgrade before any UI work |
| compose-material3 | 1.4.0 (via BOM) | Base ColorScheme, Typography, MaterialTheme wrapper | Material components needed for dialogs, sheets, snackbars |
| compose-animation | 1.10.6 (via BOM) | animateColorAsState, spring(), tween(), Crossfade | Animated theme switching |
| compose-foundation | 1.10.6 (via BOM) | Shapes, dp/sp types, Modifier primitives | Foundation of all token types |
| compose-runtime | 1.10.6 (via BOM) | CompositionLocal, @Composable, @Immutable, @Stable | Theme delivery mechanism |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-collections-immutable | 0.4.0 | `ImmutableList<T>` for stable Compose parameters | Every list property in `@Immutable` UI model classes |
| compose-runtime-annotation | (bundled in compose-runtime) | `@Immutable`, `@Stable`, `@StableMarker` | Annotating all token data classes and UI models |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `staticCompositionLocalOf` for token delivery | `compositionLocalOf` | `staticCompositionLocalOf` has better read performance for values that change only on theme switch; invalidates whole subtree but that is acceptable since theme switches are rare — use `staticCompositionLocalOf` |
| `animateColorAsState` per-color animated transitions | `Crossfade` wrapping entire content | Per-color approach keeps color objects stable in the composition tree; `Crossfade` tears down and rebuilds the entire subtree — use `animateColorAsState` |
| Bundle Inter TTF in APK | Downloadable fonts via Google Fonts | Variable fonts are NOT supported via downloadable fonts API; bundling guarantees immediate availability from first frame at ~300KB cost — bundle |

**Installation (new dependency only):**
```bash
# In libs.versions.toml
kotlinxCollectionsImmutable = "0.4.0"

# In [libraries] block
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }
```

**BOM upgrade in libs.versions.toml:**
```toml
composeBom = "2026.03.01"
```

**Version verification:** Confirmed via official Android BOM mapping page (developer.android.com/develop/ui/compose/bom/bom-mapping) — BOM 2026.03.01 maps to compose 1.10.6 + material3 1.4.0. `kotlinx-collections-immutable` 0.4.0 confirmed on Maven Central.

---

## Architecture Patterns

### Recommended Project Structure

The current `ui/theme/` directory has three files. Replace with:

```
android/app/src/main/java/run/koto/ui/theme/
├── Palette.kt          # internal object KotoPalette — raw Color values, never accessed outside this package
├── Colors.kt           # @Immutable data class KotoColors + lightKotoColors + darkKotoColors
├── Typography.kt       # @Immutable data class KotoTypography + interFontFamily + defaultTypography
├── Spacing.kt          # @Immutable data class KotoSpacing (single instance, no light/dark variant)
├── Shapes.kt           # @Immutable data class KotoShapes (single instance, no light/dark variant)
├── Motion.kt           # @Immutable data class KotoMotion + defaultKotoMotion
├── Elevation.kt        # @Immutable data class KotoElevation (single instance)
└── Theme.kt            # KotoTheme composable, CompositionLocals, KotoTheme object

android/app/src/main/java/run/koto/domain/model/
└── UiModels.kt         # @Immutable data classes: MessageUi, ConversationUi, etc.
```

### Pattern 1: Two-Tier Color System (Palette → Semantic)

Raw hex values live only in `Palette.kt` as `internal` constants. `KotoColors` exposes only semantic names. This enforces the rule that no downstream composable references raw colors.

```kotlin
// Source: .planning/research/ARCHITECTURE.md
// Palette.kt — internal, never imported by composables
internal object KotoPalette {
    val violet500  = Color(0xFF7B61FF)  // DS-02: primary
    val violet700  = Color(0xFF7C3AED)  // DS-02: bubble gradient start
    val violet800  = Color(0xFF5B21B6)  // DS-02: bubble gradient end
    val inkBlack   = Color(0xFF0D0D0D)
    val surface0   = Color(0xFF141416)
    val surface1   = Color(0xFF1E1E22)
    val surface2   = Color(0xFF28282E)
    val ghostWhite = Color(0xFFF5F5FA)
    // light mode equivalents...
}

// Colors.kt — public semantic tokens
@Immutable
data class KotoColors(
    val primary        : Color,
    val onPrimary      : Color,
    val background     : Color,
    val surface        : Color,
    val surfaceVariant : Color,
    val onBackground   : Color,
    val onSurface      : Color,
    val onSurfaceLow   : Color,     // secondary text (60% opacity)
    val onSurfaceMuted : Color,     // timestamps, hints (38% opacity)
    val bubbleOut      : Color,     // outgoing bubble bg (flat; gradient is a Brush, separate)
    val bubbleIn       : Color,
    val onBubbleOut    : Color,
    val onBubbleIn     : Color,
    val bubbleGradient : Brush,     // sent bubble gradient (DS-02)
    val divider        : Color,
    val error          : Color,
    val online         : Color,
    val isLight        : Boolean,
)
```

**DS-02 color values (exact per REQUIREMENTS.md):**
- Primary: `#7B61FF` (deep violet)
- Bubble gradient: `#7C3AED` → `#5B21B6` at 135 degrees

Note: Existing `Color.kt` uses `#7C3AED` as AccentPrimary and `#5B21B6` as AccentDark — the Phase 1 palette replaces and renames these with the exact DS-02 specification. The existing file is a complete rewrite target.

### Pattern 2: CompositionLocal Delivery + KotoTheme Accessor Object

```kotlin
// Source: .planning/research/ARCHITECTURE.md
// Use staticCompositionLocalOf — read performance optimal for rarely-changing theme values
val LocalKotoColors     = staticCompositionLocalOf { darkKotoColors }
val LocalKotoTypography = staticCompositionLocalOf { defaultKotoTypography }
val LocalKotoSpacing    = staticCompositionLocalOf { KotoSpacing() }
val LocalKotoShapes     = staticCompositionLocalOf { KotoShapes() }
val LocalKotoElevation  = staticCompositionLocalOf { KotoElevation() }
val LocalKotoMotion     = staticCompositionLocalOf { defaultKotoMotion }

// Singleton accessor — callsites: KotoTheme.colors.primary (no parentheses)
object KotoTheme {
    val colors     : KotoColors     @Composable get() = LocalKotoColors.current
    val typography : KotoTypography @Composable get() = LocalKotoTypography.current
    val spacing    : KotoSpacing    @Composable get() = LocalKotoSpacing.current
    val shapes     : KotoShapes     @Composable get() = LocalKotoShapes.current
    val elevation  : KotoElevation  @Composable get() = LocalKotoElevation.current
    val motion     : KotoMotion     @Composable get() = LocalKotoMotion.current
}
```

### Pattern 3: Animated Theme Switching (DS-06)

Animate each color individually. Do NOT use `Crossfade` (it destroys and rebuilds the subtree).

```kotlin
// Source: .planning/research/ARCHITECTURE.md
@Composable
private fun animateKotoColors(target: KotoColors): KotoColors {
    val spec = tween<Color>(durationMillis = 300, easing = FastOutSlowInEasing)
    return KotoColors(
        primary        = animateColorAsState(target.primary,        spec, label = "primary").value,
        background     = animateColorAsState(target.background,     spec, label = "bg").value,
        surface        = animateColorAsState(target.surface,        spec, label = "surface").value,
        bubbleOut      = animateColorAsState(target.bubbleOut,      spec, label = "bubbleOut").value,
        bubbleIn       = animateColorAsState(target.bubbleIn,       spec, label = "bubbleIn").value,
        onSurface      = animateColorAsState(target.onSurface,      spec, label = "onSurface").value,
        // ... all Color fields
        bubbleGradient = target.bubbleGradient,   // Brush cannot be animated; swap directly
        isLight        = target.isLight,
    )
}
```

`bubbleGradient` is a `Brush` — Compose has no `animateBrushAsState`. Swap it directly on theme change (instant switch of gradient; the surrounding surface colors will animate and mask the abruptness visually).

### Pattern 4: @Immutable UI Model Classes (DS-07)

All data classes that flow into composables must be annotated `@Immutable` (strong contract: no property ever changes after construction — callers replace the object via `copy()` to update state). Collections must use `ImmutableList` from `kotlinx-collections-immutable`.

```kotlin
// Source: official Android stability docs + STACK.md
@Immutable
data class MessageUi(
    val id          : String,
    val senderId    : String,
    val text        : String,
    val sentAt      : Long,
    val status      : MessageStatus,
    val isOutgoing  : Boolean,
    val replyTo     : MessageUi? = null,
)

@Immutable
data class ConversationUi(
    val id          : String,
    val displayName : String,
    val avatarUrl   : String?,
    val lastMessage : String,
    val unreadCount : Int,
    val isOnline    : Boolean,
    val isPinned    : Boolean,
    val isMuted     : Boolean,
    val updatedAt   : Long,
)

// For ViewModel state objects — use ImmutableList
@Immutable
data class ChatState(
    val messages   : ImmutableList<MessageUi> = persistentListOf(),
    val isLoading  : Boolean = false,
    val isTyping   : Boolean = false,
)
```

**Warning:** `@Immutable` is a contract with the compiler. If any property is mutable or the object contains a mutable type (e.g., standard `List`), the annotation is violated and Compose may skip recomposition when it should not. Enforce code review on all `@Immutable` classes.

### Pattern 5: Inter Font Loading

Variable fonts cannot be loaded via the downloadable fonts API. Bundle the Inter Variable TTF file in `res/font/`.

```kotlin
// Source: official Android Compose font docs
// File: res/font/inter_variable.ttf  (Inter-Variable.ttf renamed to lowercase)
val interFontFamily = FontFamily(
    Font(
        resId = R.font.inter_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
        ),
    ),
    Font(
        resId = R.font.inter_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
        ),
    ),
    Font(
        resId = R.font.inter_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
        ),
    ),
    Font(
        resId = R.font.inter_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
        ),
    ),
)
```

**APK size impact:** ~300KB for Inter Variable (single file, all weights). Acceptable for a premium messenger targeting quality over APK minimalism.

### Anti-Patterns to Avoid

- **Raw Color() in composables:** Any `Color(0xFF...)` outside `Palette.kt` is a bug. Always reference `KotoTheme.colors.*`.
- **Raw dp values in layout code:** Any `16.dp` literal not from `KotoTheme.spacing.*` skips the design system and makes systematic spacing changes impossible.
- **Using `compositionLocalOf` for tokens:** Causes recomposition of every consumer on every theme switch even if the specific value did not change. Use `staticCompositionLocalOf` — the performance advantage is significant for a deeply nested UI.
- **`@Immutable` on classes with `List<T>` properties:** `List<T>` is considered unstable by the compiler; replace with `ImmutableList<T>`.
- **Animating `Brush` with `animateBrushAsState`:** This API does not exist. Gradient brushes cannot be interpolated this way — swap them directly.
- **Keeping existing Theme.kt, Color.kt, Type.kt:** These files are stubs, dark-only, and structurally incompatible with the KotoTheme system. They must be replaced (not extended).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Color animation on theme switch | Custom `ValueAnimator` or frame-by-frame lerp | `animateColorAsState` from compose-animation | Built-in, API-stable, hardware-accelerated color interpolation |
| Stable collections in Compose state | Custom wrapper around MutableList | `kotlinx-collections-immutable` `ImmutableList` / `persistentListOf()` | Proven stable type — Compose compiler recognizes it as stable without annotation |
| Font family with multiple weights | Separate TTF files per weight (400, 500, 600, 700) | Inter Variable TTF with `FontVariation.Settings` | Single ~300KB file vs. multiple ~100KB files each; variable fonts give continuous weight axis |
| Spacing constants | `object Dimens { val paddingMd = 12.dp }` | `@Immutable data class KotoSpacing` delivered via `CompositionLocal` | Design tokens via CompositionLocal allow per-screen or per-component overrides via `CompositionLocalProvider`; object constants cannot be overridden locally |

**Key insight:** In Compose, delivering tokens via `CompositionLocal` rather than global objects enables local overrides (e.g., a compact density mode) without changing the global singleton.

---

## Common Pitfalls

### Pitfall 1: `@Immutable` Violated by Mutable Property Types
**What goes wrong:** Annotating a data class `@Immutable` that contains a `List<T>` field. The compiler treats `List<T>` as unstable regardless of the annotation, causing missed recomposition skipping.
**Why it happens:** The `@Immutable` annotation is a contract the developer makes with the compiler, but the compiler also analyses each field's type. Standard Kotlin `List` is an interface with mutable implementations — the compiler cannot guarantee immutability.
**How to avoid:** Replace all `List<T>` properties in `@Immutable` classes with `ImmutableList<T>` from `kotlinx-collections-immutable`. Run Compose compiler reports (`./gradlew assembleDebug` with `reportsDestination` set) and check that all DS-01 data classes appear as "stable" in the report.
**Warning signs:** Compose compiler report shows `unstable` next to a class that has `@Immutable`.

### Pitfall 2: `Brush` Cannot Be Animated with `animateColorAsState`
**What goes wrong:** Attempting to animate `bubbleGradient: Brush` the same way as `Color` fields — there is no `animateBrushAsState` API.
**Why it happens:** `Brush` is not a simple interpolatable value; it is a complex object with a rendering strategy.
**How to avoid:** In `animateKotoColors()`, pass `bubbleGradient = target.bubbleGradient` directly (no animation). The visual abruptness of an instant gradient swap is masked by the 300ms color animations on the surrounding surfaces.

### Pitfall 3: `staticCompositionLocalOf` Triggers Full Subtree Recomposition
**What goes wrong:** On theme switch, the entire UI tree recomposes at once, causing a visible frame drop if theme switching is triggered during interaction.
**Why it happens:** `staticCompositionLocalOf` invalidates every reader when the provided value changes, which is every composable in the app.
**How to avoid:** Wrap the theme toggle call in `LaunchedEffect` or a `viewModelScope.launch` to ensure the switch happens on an idle frame. The 300ms color animation from Pattern 3 distributes the recomposition over multiple frames, masking any single-frame spike.

### Pitfall 4: BOM Upgrade Breaking Existing Composables
**What goes wrong:** After upgrading from BOM 2025.03.01 to 2026.03.01, existing composables that use APIs removed or renamed between Material3 1.3.x and 1.4.0 fail to compile.
**Why it happens:** Material3 1.4.0 introduced breaking API changes in some composables (notably some `TopAppBar` variants and `NavigationBar` slots).
**How to avoid:** The BOM upgrade must happen as the first commit of Phase 1, in isolation, before any new theme code is written. Verify that the existing screens still compile. Fix any breakages before proceeding to theme implementation.
**Warning signs:** Unresolved reference errors on `TopAppBar`, `NavigationBar`, or any Material3 composable after the BOM bump.

### Pitfall 5: Inter Font File Naming
**What goes wrong:** Placing `Inter-Variable.ttf` in `res/font/` with hyphens or capitals causes a resource ID compile error.
**Why it happens:** Android resource names must be lowercase alphanumeric with underscores only.
**How to avoid:** Rename to `inter_variable.ttf` before placing in `res/font/`. Reference as `R.font.inter_variable`.

### Pitfall 6: Existing Theme Files Not Fully Replaced
**What goes wrong:** New theme code is added alongside existing `Theme.kt`, `Color.kt`, `Type.kt` stubs, causing two competing theme systems (the old `KotoTheme` composable from `Theme.kt` and the new one).
**Why it happens:** Incremental editing rather than full file replacement.
**How to avoid:** Delete the three existing files as the first step of theme implementation. Then create the new 7-file system from scratch. The existing files have no content worth preserving — they are stubs with no semantic structure.

---

## Code Examples

Verified patterns from ARCHITECTURE.md (project's own research, HIGH confidence):

### Spacing Scale (DS-04)
```kotlin
// Source: .planning/research/ARCHITECTURE.md
@Immutable
data class KotoSpacing(
    val xxs  : Dp =  2.dp,
    val xs   : Dp =  4.dp,
    val sm   : Dp =  8.dp,
    val md   : Dp = 12.dp,
    val lg   : Dp = 16.dp,
    val xl   : Dp = 20.dp,
    val xxl  : Dp = 24.dp,
    val xxxl : Dp = 48.dp,
    // Messenger-semantic convenience tokens
    val bubblePaddingH  : Dp = 14.dp,
    val bubblePaddingV  : Dp = 10.dp,
    val listItemPadding : Dp = 16.dp,
    val inputBarPadding : Dp = 12.dp,
)
```

Note: REQUIREMENTS.md DS-04 specifies `xxxl = 48dp`. ARCHITECTURE.md has `xxxl = 32dp`. FEATURES.md specifies `xxxl = 48dp` (empty state offset). Use 48dp per REQUIREMENTS.md — this is the locked requirement.

### Shape System (DS-05)
```kotlin
// Source: .planning/research/ARCHITECTURE.md + FEATURES.md
@Immutable
data class KotoShapes(
    // Chat bubbles — asymmetric: tail corner is 4dp, outer corners are 18dp
    val bubbleOut  : Shape = RoundedCornerShape(18.dp, 18.dp,  4.dp, 18.dp), // topStart, topEnd, bottomEnd, bottomStart
    val bubbleIn   : Shape = RoundedCornerShape(18.dp, 18.dp, 18.dp,  4.dp),
    // Standard shapes matching DS-05 spec
    val sm         : Shape = RoundedCornerShape(8.dp),
    val md         : Shape = RoundedCornerShape(12.dp),
    val lg         : Shape = RoundedCornerShape(16.dp),
    val xl         : Shape = RoundedCornerShape(20.dp),
    val bubble     : Shape = RoundedCornerShape(18.dp),   // bubble outer corners per DS-05
    val bottomSheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val inputField : Shape = RoundedCornerShape(24.dp),
    val pill       : Shape = CircleShape,
    val card       : Shape = RoundedCornerShape(16.dp),
)
```

### Motion Specs
```kotlin
// Source: .planning/research/ARCHITECTURE.md + FEATURES.md
@Immutable
data class KotoMotion(
    val springSnappy : SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow,  dampingRatio = Spring.DampingRatioNoBouncy),
    val springBouncy : SpringSpec<Float> = spring(stiffness = Spring.StiffnessMedium,     dampingRatio = Spring.DampingRatioLowBouncy),
    val springGentle : SpringSpec<Float> = spring(stiffness = Spring.StiffnessLow,        dampingRatio = Spring.DampingRatioNoBouncy),
    val tweenFast    : TweenSpec<Float>  = tween(durationMillis = 150, easing = FastOutSlowInEasing),
    val tweenMedium  : TweenSpec<Float>  = tween(durationMillis = 300, easing = FastOutSlowInEasing),
    val tweenSlow    : TweenSpec<Float>  = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
)
```

### Typography Scale (DS-03) — 8+ styles with Inter
The existing `Type.kt` already has 9 `TextStyle` entries without Inter font family assignment. Phase 1 adds:
1. The `interFontFamily` declaration (from Pattern 5 above)
2. `fontFamily = interFontFamily` to every `TextStyle`
3. Two messenger-specific styles (`chatBubble`, `chatTimestamp`) per ARCHITECTURE.md
4. Alignment with the exact size/weight spec from FEATURES.md

```kotlin
// Source: .planning/research/FEATURES.md typography table
// 11 styles total: displayLarge, displayMedium, titleLarge, titleMedium, titleSmall,
// bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall
// + 2 messenger-semantic: chatBubble (15sp/Regular), chatTimestamp (11sp/Normal)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Accompanist SystemUI | `enableEdgeToEdge()` from Activity | Compose 1.6+ / Activity 1.9+ | No Accompanist dependency needed |
| Separate font TTF per weight | Inter Variable font with `FontVariation.Settings` | Android API 26+ (minSdk is 26) | Single file, continuous weight axis |
| `compositionLocalOf` for theme colors | `staticCompositionLocalOf` + `animateColorAsState` per field | Established pattern circa Compose 1.5 | Better performance; no subtree invalidation cascade |
| Strong Skipping Mode off by default | Default ON with Compose Compiler 2.0+ (K2) | Compose Compiler 2.0 (2024) | More composables are auto-skippable, but explicit `@Immutable` remains best practice |
| `ComposeBom = "2025.03.01"` | Must upgrade to `"2026.03.01"` | April 2026 | Material3 1.4.0, stable `animateItem()`, latest animation APIs |

**Deprecated / outdated in this phase:**
- The existing `Theme.kt` dark-only `KotoDarkColorScheme` + `KotoTheme` composable: replace entirely.
- The existing `Color.kt` raw color exports (`AccentPrimary`, `BgBase`, etc.): replace with two-tier palette/semantic system.
- The existing `Type.kt` without font family: replace with Inter-bound typography.

---

## Open Questions

1. **Light theme design values**
   - What we know: FEATURES.md and ARCHITECTURE.md document partial light theme palettes (FEATURES.md has full light/dark color tables).
   - What's unclear: The existing codebase is dark-only. The light theme colors in FEATURES.md use a different violet shade (`#6C5CE7`) as Primary vs. DS-02's `#7B61FF`. REQUIREMENTS.md DS-02 is the authoritative spec.
   - Recommendation: Use REQUIREMENTS.md DS-02 values (`#7B61FF` primary) for both light and dark primary. For light mode surface/background values, use FEATURES.md light theme table (FAFBFF background, FFFFFF surface). Raise with user if the exact light bubble gradient is unclear — DS-02 specifies only the dark gradient.

2. **`xxxl` spacing value: 32dp vs 48dp**
   - What we know: ARCHITECTURE.md `KotoSpacing` has `xxxl = 32.dp`; FEATURES.md spacing table and REQUIREMENTS.md DS-04 both specify `xxxl = 48dp`.
   - What's unclear: Which is authoritative.
   - Recommendation: Use 48dp per REQUIREMENTS.md (requirements file is the specification source).

3. **Wave 0 test infrastructure**
   - What we know: No test files exist for the theme system. The existing build has no Compose UI test dependency.
   - Recommendation: Theme tokens are pure Kotlin data objects — unit tests can assert exact token values without an emulator. Add `compose-ui-test-junit4` and `screenshot testing` or simple value assertions in Wave 0 if validation is enabled.

---

## Environment Availability

Step 2.6: This phase is code/config changes only (Kotlin files + one font resource file + Gradle version bump). No external services, databases, or CLI tools beyond the standard Android/Gradle toolchain are required.

The only environment action is the BOM version string change in `libs.versions.toml` — no new tool installs needed.

---

## Validation Architecture

Test framework: The project has no Compose UI test infrastructure yet. Phase 1 design tokens are pure Kotlin data classes — they can be covered with standard JUnit4 unit tests asserting token values, no emulator required.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DS-01 | `KotoTheme.colors` resolves to correct light/dark values | Unit | `./gradlew :app:testDebugUnitTest --tests "*.KotoThemeTest"` | No — Wave 0 |
| DS-02 | Primary color is `#7B61FF`; gradient start/end are `#7C3AED`/`#5B21B6` | Unit | same | No — Wave 0 |
| DS-03 | Typography has ≥8 named styles; Inter font family assigned | Unit | same | No — Wave 0 |
| DS-04 | Spacing `xxs=2dp`, `xxxl=48dp`, all tokens present | Unit | same | No — Wave 0 |
| DS-05 | Shape system has `sm=8dp`, `bubble=18dp`, etc. | Unit | same | No — Wave 0 |
| DS-06 | `animateKotoColors()` returns all fields animated; Brush passed through | Unit | same | No — Wave 0 |
| DS-07 | All `@Immutable` classes have no mutable-typed fields | Compose compiler report | `./gradlew assembleDebug` then check `build/compose_compiler/` | No — Wave 0 |

### Sampling Rate
- Per task commit: `./gradlew :app:testDebugUnitTest --tests "*.KotoThemeTest"`
- Per wave merge: `./gradlew :app:testDebugUnitTest`
- Phase gate: Full suite green + Compose compiler report shows no unstable classes in theme package

### Wave 0 Gaps
- [ ] `android/app/src/test/java/run/koto/ui/theme/KotoThemeTest.kt` — covers DS-01 through DS-06
- [ ] Compose compiler report configuration in `build.gradle.kts`:
  ```kotlin
  composeCompiler {
      reportsDestination = layout.buildDirectory.dir("compose_compiler")
      metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
  ```

---

## Sources

### Primary (HIGH confidence)
- `.planning/research/ARCHITECTURE.md` — full Layer 1 token system with code examples, CompositionLocal patterns, animated theme switching implementation
- `.planning/research/FEATURES.md` — definitive color palette tables (light and dark), typography scale, spacing scale, corner radius scale
- `.planning/research/STACK.md` — library recommendations, KotoTheme pattern, font strategy
- `android/gradle/libs.versions.toml` — verified current BOM version (2025.03.01 → must upgrade)
- `android/app/src/main/java/run/koto/ui/theme/` — existing stub files (Color.kt, Theme.kt, Type.kt)
- `developer.android.com/develop/ui/compose/bom/bom-mapping` — confirmed BOM 2026.03.01 → Compose 1.10.6, Material3 1.4.0

### Secondary (MEDIUM confidence)
- Maven Central `org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0` — confirmed latest stable version
- `developer.android.com/develop/ui/compose/designsystems/custom` — custom design system CompositionLocal patterns
- `developer.android.com/develop/ui/compose/performance/stability` — `@Immutable`/`@Stable` stability guidance

### Tertiary (LOW confidence)
- Medium articles on animated color theme switching (multiple sources agree on `animateColorAsState` per-field approach) — corroborated by ARCHITECTURE.md which already has the code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — BOM version verified against official mapping page; Compose APIs verified in existing research docs
- Architecture: HIGH — token patterns, CompositionLocal setup, and animated switching all documented in ARCHITECTURE.md with working Kotlin code
- Pitfalls: HIGH — most pitfalls derived from existing codebase analysis (stub files, missing light theme) or well-known Compose stability rules

**Research date:** 2026-04-05
**Valid until:** 2026-07-05 (stable Compose release cycle; BOM version should be re-checked before executing if more than 30 days pass)
