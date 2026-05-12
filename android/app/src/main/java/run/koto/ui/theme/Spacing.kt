package run.koto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing design tokens for KotoTheme.
 * Access via KotoTheme.spacing.* — never use raw dp literals in layout code.
 *
 * DS-04: xxs=2dp through xxxl=48dp. Messenger-semantic tokens for common use cases.
 * Note: xxxl=48dp per REQUIREMENTS.md DS-04 (overrides ARCHITECTURE.md which had 32dp).
 */
@Immutable
data class KotoSpacing(
    // ── Base scale ────────────────────────────────────────────────────────────
    val xxs  : Dp =  2.dp,   // between check marks, dot indicators
    val xs   : Dp =  4.dp,   // inline icon-to-text gap
    val sm   : Dp =  8.dp,   // between grouped bubbles, chip padding
    val md   : Dp = 12.dp,   // avatar-to-text gap, section internal padding
    val lg   : Dp = 16.dp,   // screen horizontal padding, between sections
    val xl   : Dp = 20.dp,   // between major UI groups
    val xxl  : Dp = 24.dp,   // between major UI groups
    val xxxl : Dp = 48.dp,   // empty state vertical centering (DS-04: 48dp)
    // ── Messenger-semantic convenience tokens ─────────────────────────────────
    val bubblePaddingH  : Dp = 14.dp,   // bubble horizontal padding
    val bubblePaddingV  : Dp = 10.dp,   // bubble vertical padding
    val listItemPadding : Dp = 16.dp,   // conversation row horizontal padding
    val inputBarPadding : Dp = 12.dp,   // compose bar horizontal padding
)
