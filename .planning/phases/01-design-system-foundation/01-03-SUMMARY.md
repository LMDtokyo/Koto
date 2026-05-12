---
phase: "01"
plan: "03"
subsystem: android-typography
tags: [typography, inter-font, design-tokens, DS-03]
dependency_graph:
  requires: [01-01]
  provides: [inter-variable-font, koto-typography-tokens, default-koto-typography, inter-font-family]
  affects:
    - android/app/src/main/res/font/inter_variable.ttf
    - android/app/src/main/java/run/koto/ui/theme/Typography.kt
    - android/app/src/main/java/run/koto/ui/theme/Type.kt
tech_stack:
  added: []
  patterns: [variable-font-resource, font-variation-settings, immutable-data-class]
key_files:
  created:
    - android/app/src/main/res/font/inter_variable.ttf
    - android/app/src/main/java/run/koto/ui/theme/Typography.kt
  modified:
    - android/app/src/main/java/run/koto/ui/theme/Type.kt
decisions:
  - "Inter Variable v4.1 (860KB full TTF) bundled in APK — Downloadable Fonts API does not support variable fonts, bundling is mandatory"
  - "FontVariation.Settings with 4 weight variants (400/500/600/700) from single variable TTF — one file, continuous weight axis"
  - "13 styles: 11 standard (display/title/body/label) + 2 messenger-specific (chatBubble + chatTimestamp) — covers all DS-03 scale requirements"
  - "Type.kt emptied (old Material3 Typography val removed) — superseded by Typography.kt to avoid name conflict with new KotoTypography data class"
metrics:
  duration: "~5 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_changed: 3
---

# Phase 01 Plan 03: Inter Variable Font and KotoTypography Token System Summary

Inter Variable v4.1 font bundled (860KB TTF) and @Immutable KotoTypography data class created with 13 named styles all bound to interFontFamily, replacing the old Material3 Typography val in Type.kt.

## Tasks Completed

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Download and place Inter Variable font | Done | 215b343 |
| 2 | Create Typography.kt and update Type.kt | Done | 0e7f568 |

## What Was Built

### Task 1: Inter Variable Font (215b343)

**android/app/src/main/res/font/inter_variable.ttf:**
- Downloaded Inter v4.1 from official rsms/inter GitHub release zip (Inter-4.1.zip)
- Extracted `InterVariable.ttf` — full Unicode range variable font
- File size: 860KB (valid binary, TrueType magic `00 01 00 00`)
- Named `inter_variable.ttf` per Android resource naming conventions (lowercase, underscores, .ttf)

**Download path used:** GitHub releases zip `Inter-4.1.zip` → extracted `InterVariable.ttf`

Note: Direct GitHub raw URL served HTML instead of binary. Google Fonts CDN served WOFF2 subset. The zip archive from releases was the only reliable source for the full TTF.

### Task 2: Typography.kt and Type.kt (0e7f568)

**android/app/src/main/java/run/koto/ui/theme/Typography.kt (new):**
- `interFontFamily`: FontFamily with 4 `Font()` entries using `FontVariation.Settings` for weights 400/500/600/700 from `R.font.inter_variable`
- `@Immutable data class KotoTypography` with 13 `TextStyle` fields
- `val defaultKotoTypography`: all 13 styles with `fontFamily = interFontFamily`
- Exact sizes per FEATURES.md: chatBubble=15sp, chatTimestamp=11sp, displayLarge=28sp, titleLarge=22sp, bodyLarge=16sp, labelSmall=11sp

**android/app/src/main/java/run/koto/ui/theme/Type.kt (emptied):**
- Removed `val KotoTypography = Typography(...)` (old Material3 val)
- Replaced with package declaration + comment explaining supersession
- Prevents name conflict with new `data class KotoTypography` in Typography.kt

**Theme.kt:** Already updated by Plan 02 to not reference the old `KotoTypography` val — no changes needed.

## Verification Results

```
grep "@Immutable" Typography.kt                        → PASS
grep "data class KotoTypography" Typography.kt         → PASS
grep "val interFontFamily = FontFamily(" Typography.kt → PASS
grep "R.font.inter_variable" Typography.kt             → PASS (×4 entries)
grep "val chatBubble" Typography.kt                    → PASS (15sp field)
grep "val chatTimestamp" Typography.kt                 → PASS (11sp field)
grep "val defaultKotoTypography" Typography.kt         → PASS
Count of KotoTypography fields                         → 13 (PASS)
Count of fontFamily = interFontFamily                  → 13/13 styles (PASS)
grep "val KotoTypography = Typography(" Type.kt        → PASS (removed)
ls -lh inter_variable.ttf                             → 860K (PASS, >200KB)
od -t x1 inter_variable.ttf | head -1                 → 00 01 00 00 (TrueType PASS)
./gradlew :app:assembleDebug                          → BLOCKED (see deviation below)
```

## Deviations from Plan

### Environment Limitation — NDK Not Installed

**Found during:** Task 2 verification step

**Issue:** `./gradlew :app:assembleDebug` fails with `NDK is not installed` at Gradle configuration phase. The `org.mozilla.rust-android-gradle.rust-android` plugin requires NDK at configuration time (before compilation). Same pre-existing constraint as documented in Plans 01 and 02.

**Impact:** Cannot verify `BUILD SUCCESSFUL` via Gradle. All file content is syntactically correct Kotlin — verified by inspection and grep checks against each acceptance criterion.

**Action:** None — environment setup issue. Install NDK in Android Studio SDK Manager (Tools → SDK Manager → SDK Tools → NDK Side by Side). Documented.

### Font Download URL Deviation

**Found during:** Task 1

**Issue:** Primary URL `https://github.com/rsms/inter/releases/download/v4.0/Inter-Variable.ttf` returned 9-byte response. Alternate raw URL `https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Variable.ttf` served HTML instead of binary (GitHub UI redirect). Google Fonts CDN returned a Latin-subset WOFF2.

**Fix:** Downloaded `Inter-4.1.zip` from GitHub releases (33MB zip, v4.1 instead of v4.0) and extracted `InterVariable.ttf` from it. Result: 860KB full variable font with complete Unicode range.

**Impact:** Font is v4.1 instead of v4.0 — newer version, functionally equivalent. No behavior change.

## Known Stubs

None — all 13 typography styles have concrete values. `interFontFamily` references the bundled resource `R.font.inter_variable`. No placeholder text or empty values flow to UI rendering.

## Self-Check: PASSED
