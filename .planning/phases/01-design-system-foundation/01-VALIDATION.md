---
phase: 1
slug: design-system-foundation
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-05
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android Instrumented Tests (Compose UI Test) |
| **Config file** | `android/app/build.gradle.kts` |
| **Quick run command** | `cd android && ./gradlew :app:compileDebugKotlin` |
| **Full suite command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd android && ./gradlew :app:compileDebugKotlin`
- **After every plan wave:** Run `cd android && ./gradlew :app:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | DS-01..07 | compile | `./gradlew :app:compileDebugKotlin` | ✅ | ⬜ pending |
| 01-01-02 | 01 | 1 | DS-01..07 | unit | `./gradlew :app:testDebugUnitTest` | ✅ W0 | ⬜ pending |
| 01-02-01 | 02 | 2 | DS-01, DS-02 | compile + grep | `grep -r "7B61FF" android/app/src/main/java/run/koto/ui/theme/` | ✅ | ⬜ pending |
| 01-03-01 | 03 | 2 | DS-03 | compile + grep | `grep "Inter" android/app/src/main/java/run/koto/ui/theme/Typography.kt` | ✅ | ⬜ pending |
| 01-03-02 | 03 | 2 | DS-03 | asset check | `test -f android/app/src/main/res/font/inter_variable.ttf` | ✅ | ⬜ pending |
| 01-04-01 | 04 | 2 | DS-04, DS-05 | compile + grep | `grep "xxxl" android/app/src/main/java/run/koto/ui/theme/Spacing.kt` | ✅ | ⬜ pending |
| 01-04-02 | 04 | 2 | DS-06 | compile + grep | `grep "KotoMotionSpec" android/app/src/main/java/run/koto/ui/theme/Motion.kt` | ✅ | ⬜ pending |
| 01-05-01 | 05 | 3 | DS-06 | compile + grep | `grep "animateColorAsState" android/app/src/main/java/run/koto/ui/theme/Theme.kt` | ✅ | ⬜ pending |
| 01-05-02 | 05 | 3 | DS-07 | compile + grep | `grep "@Immutable" android/app/src/main/java/run/koto/ui/theme/UiModels.kt` | ✅ | ⬜ pending |
| 01-05-03 | 05 | 3 | DS-01..07 | full compile | `./gradlew :app:compileDebugKotlin` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `KotoThemeTest.kt` — test scaffold created in plan 01-01 Task 2
- [x] Compose UI Test dependency already in build.gradle.kts

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Animated theme switching visually smooth | DS-06 | Requires visual inspection | Toggle dark/light theme, verify crossfade/circular reveal animation |
| Inter font renders correctly on API 26 | DS-03 | Font rendering is device-specific | Run on API 26 emulator, inspect text rendering |
| Gradient bubbles display correct colors | DS-02 | Brush rendering is visual | Preview composable with gradient brush |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-05
